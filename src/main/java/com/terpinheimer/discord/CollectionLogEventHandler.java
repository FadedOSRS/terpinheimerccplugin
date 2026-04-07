package com.terpinheimer.discord;

import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.util.Text;

@Singleton
public class CollectionLogEventHandler
{
	private static final int COLLECTION_SCREENSHOT_DELAY_MS = 500;
	private static final int CHAT_BUFFER_MAX = 24;
	private static final long LOOT_CORRELATE_MAX_MS = 35_000L;

	private static final Pattern ITEM_ADDED = Pattern.compile(
		"(?i)added (?:an? |the )?(.+?) to your collection");
	private static final Pattern NEW_ITEM = Pattern.compile("(?i)new item[^:]*:?\\s*(.+)");
	private static final Pattern CONSUMES_SOURCE = Pattern.compile("(?i)consumes? (?:the |a |an )([^.,!?]+)");
	private static final Pattern RECEIVED_FROM = Pattern.compile("(?i)received from (?:the |a |an )?([^.,!?]+)");
	private static final Pattern REWARD_FROM = Pattern.compile("(?i)reward (?:from|for) (?:the |a |an )?([^.,!?]+)");
	private static final Pattern NTH_TIME = Pattern.compile(
		"(?i)(?:for the |this is (?:your|the) )(\\d+)(?:st|nd|rd|th)(?: time)?");
	private static final Pattern FIRST_TIME = Pattern.compile("(?i)\\bfirst (?:time|completion)\\b");
	/** Boss KC style lines often appear in game chat near the drop. */
	private static final Pattern KILL_COUNT_IS = Pattern.compile("(?i)\\bkill count is:?\\s*(\\d+)\\b");
	private static final Pattern COMPLETION_COUNT_IS = Pattern.compile("(?i)\\bcompletion count is:?\\s*(\\d+)\\b");

	private final Client client;
	private final TerpinheimerConfig config;
	private final ItemManager itemManager;
	private final WebhookMessageBuilder messageBuilder;
	private final WebhookDispatcher dispatcher;
	private final ClientThread clientThread;
	private final ClientScreenshot screenshot;
	private final ScheduledExecutorService scheduledExecutor;

	private final ArrayDeque<String> recentPlainChat = new ArrayDeque<>();
	private volatile RecentLoot recentLoot;

	private static final class RecentLoot
	{
		final long timeMs;
		final String sourceLabel;
		final List<String> itemNamesStd;

		RecentLoot(long timeMs, String sourceLabel, List<String> itemNamesStd)
		{
			this.timeMs = timeMs;
			this.sourceLabel = sourceLabel;
			this.itemNamesStd = itemNamesStd;
		}
	}

	@Inject
	CollectionLogEventHandler(
		Client client,
		TerpinheimerConfig config,
		ItemManager itemManager,
		WebhookMessageBuilder messageBuilder,
		WebhookDispatcher dispatcher,
		ClientThread clientThread,
		ClientScreenshot screenshot,
		ScheduledExecutorService scheduledExecutor)
	{
		this.client = client;
		this.config = config;
		this.itemManager = itemManager;
		this.messageBuilder = messageBuilder;
		this.dispatcher = dispatcher;
		this.clientThread = clientThread;
		this.screenshot = screenshot;
		this.scheduledExecutor = scheduledExecutor;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (!config.sendCollectionLog())
		{
			return;
		}
		if (event.getGameState() == GameState.HOPPING)
		{
			synchronized (recentPlainChat)
			{
				recentPlainChat.clear();
			}
			recentLoot = null;
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!config.sendCollectionLog() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		NPC npc = event.getNpc();
		String src = npc != null ? Text.removeTags(npc.getName()) : "NPC";
		recordLoot(src, event.getItems());
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		if (!config.sendCollectionLog() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Player p = event.getPlayer();
		String src = p != null ? Text.removeTags(p.getName()) : "Loot";
		recordLoot(src, event.getItems());
	}

	private void recordLoot(String sourceLabel, Collection<ItemStack> stacks)
	{
		if (stacks == null || stacks.isEmpty())
		{
			return;
		}
		List<String> names = new ArrayList<>();
		for (ItemStack stack : stacks)
		{
			String name = itemManager.getItemComposition(stack.getId()).getName();
			names.add(Text.standardize(name));
		}
		recentLoot = new RecentLoot(System.currentTimeMillis(), sourceLabel, names);
	}

	private void appendChatLine(String plain)
	{
		if (plain == null || plain.isEmpty())
		{
			return;
		}
		synchronized (recentPlainChat)
		{
			recentPlainChat.addLast(plain);
			while (recentPlainChat.size() > CHAT_BUFFER_MAX)
			{
				recentPlainChat.removeFirst();
			}
		}
	}

	/** Recent game lines in order (includes the triggering collection log line as the newest). */
	private String snapshotChatBuffer()
	{
		synchronized (recentPlainChat)
		{
			return String.join("\n", recentPlainChat);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.sendCollectionLog() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		String plain = Text.removeTags(event.getMessage());
		if (DiscordChatFilters.allows(event.getType()))
		{
			appendChatLine(plain);
		}
		if (!DiscordChatFilters.allows(event.getType()))
		{
			return;
		}
		String low = plain.toLowerCase();
		if (!low.contains("collection log") && !low.contains("col log") && !low.contains("new item"))
		{
			return;
		}
		String item = extractItemName(plain);
		if (item == null || item.isEmpty())
		{
			item = plain;
		}
		Set<String> deny = DiscordListParser.parseLines(config.collectionLogItemDenylist());
		String itemStd = Text.standardize(item);
		for (String d : deny)
		{
			if (itemStd.contains(d) || itemStd.equals(d))
			{
				return;
			}
		}
		String user = client.getLocalPlayer() != null
			? Text.removeTags(client.getLocalPlayer().getName()) : "Player";
		String itemMd = WikiLinks.markdownLink(item, WikiLinks.itemPage(item));
		String desc = config.collectionLogMessage()
			.replace("%USERNAME%", user)
			.replace("%ITEM%", itemMd)
			.replace("%GAME_MESSAGE%", plain)
			.replace("\\n", "\n");
		String thumb = WikiLinks.itemIconPngUrl(item);
		int logged = client.getVarpValue(VarPlayer.CLOG_LOGGED);
		int total = client.getVarpValue(VarPlayer.CLOG_TOTAL);
		String completed = formatCollectionProgress(logged, total);
		String rank = total <= 0 ? "—" : rankForCompletionPercent(percentOrZero(logged, total));
 
		String context = snapshotChatBuffer();
		String source = extractSource(context, itemStd);
		String completionCount = extractCompletionCount(context);
		final JsonObject embed = messageBuilder.collectionLogDinkStyleEmbed(
			user, desc, thumb, completed, rank, source, completionCount);
		if (config.collectionLogSendImage())
		{
			scheduledExecutor.schedule(() -> clientThread.invokeLater(() ->
			{
				byte[] png = screenshot.capturePngOrNull();
				String json = messageBuilder.toWebhookJson(embed);
				dispatcher.enqueue(new WebhookPayload(json, png));
			}), COLLECTION_SCREENSHOT_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		else
		{
			String json = messageBuilder.toWebhookJson(embed);
			dispatcher.enqueue(new WebhookPayload(json, null));
		}
	}

	private static String extractItemName(String plain)
	{
		Matcher m = Pattern.compile("(?i)New item added to your collection log:\\s*(.+)").matcher(plain);
		if (m.find())
		{
			return m.group(1).trim();
		}
		m = ITEM_ADDED.matcher(plain);
		if (m.find())
		{
			return m.group(1).trim();
		}
		m = NEW_ITEM.matcher(plain);
		if (m.find())
		{
			return m.group(1).trim();
		}
		return null;
	}

	private String extractSource(String context, String collectedItemStd)
	{
		String fromChat = extractSourceFromPatterns(context);
		if (!"—".equals(fromChat))
		{
			return fromChat;
		}
		String fromLoot = lootSourceIfItemMatches(collectedItemStd);
		return fromLoot != null ? fromLoot : "—";
	}

	private static String extractSourceFromPatterns(String text)
	{
		Matcher m = CONSUMES_SOURCE.matcher(text);
		if (m.find())
		{
			return tidySourceToken(m.group(1));
		}
		m = RECEIVED_FROM.matcher(text);
		if (m.find())
		{
			return tidySourceToken(m.group(1));
		}
		m = REWARD_FROM.matcher(text);
		if (m.find())
		{
			return tidySourceToken(m.group(1));
		}
		return "—";
	}

	private String lootSourceIfItemMatches(String collectedItemStd)
	{
		RecentLoot loot = recentLoot;
		if (loot == null || (System.currentTimeMillis() - loot.timeMs) > LOOT_CORRELATE_MAX_MS)
		{
			return null;
		}
		for (String n : loot.itemNamesStd)
		{
			if (n.equals(collectedItemStd) || collectedItemStd.contains(n) || n.contains(collectedItemStd))
			{
				return loot.sourceLabel;
			}
		}
		return null;
	}

	private static String formatCollectionProgress(int logged, int total)
	{
		if (total <= 0)
		{
			return "—";
		}
		double pct = 100.0 * Math.min(logged, total) / total;
		return String.format("%d/%d (%.1f%%)", Math.min(logged, total), total, pct);
	}

	private static double percentOrZero(int logged, int total)
	{
		if (total <= 0)
		{
			return 0;
		}
		return 100.0 * Math.min(logged, total) / total;
	}

	/**
	 * Tier labels aligned with global collection completion — matches “Steel” around ~40% like common third-party bots.
	 */
	private static String rankForCompletionPercent(double pct)
	{
		if (pct >= 100)
		{
			return "Dragon";
		}
		if (pct >= 90)
		{
			return "Rune";
		}
		if (pct >= 75)
		{
			return "Adamant";
		}
		if (pct >= 60)
		{
			return "Mithril";
		}
		if (pct >= 45)
		{
			return "Black";
		}
		if (pct >= 30)
		{
			return "Steel";
		}
		if (pct >= 15)
		{
			return "Iron";
		}
		return "Bronze";
	}

	private static String tidySourceToken(String raw)
	{
		String s = raw.trim();
		int and = s.toLowerCase().indexOf(" and ");
		if (and > 0)
		{
			s = s.substring(0, and).trim();
		}
		if (s.toLowerCase().startsWith("the "))
		{
			s = s.substring(4).trim();
		}
		return s.isEmpty() ? "—" : s;
	}

	private static String extractCompletionCount(String context)
	{
		String kc = lastMatchingGroup(KILL_COUNT_IS, context);
		if (kc != null)
		{
			return kc;
		}
		kc = lastMatchingGroup(COMPLETION_COUNT_IS, context);
		if (kc != null)
		{
			return kc;
		}
		if (FIRST_TIME.matcher(context).find())
		{
			return "1";
		}
		String ord = lastMatchingGroup(NTH_TIME, context);
		return ord != null ? ord : "—";
	}

	private static String lastMatchingGroup(Pattern p, String text)
	{
		Matcher m = p.matcher(text);
		String last = null;
		while (m.find())
		{
			last = m.group(1);
		}
		return last;
	}
}
