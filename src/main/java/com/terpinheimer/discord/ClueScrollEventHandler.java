package com.terpinheimer.discord;

import com.google.gson.JsonObject;
import com.terpinheimer.ClueNotifyTier;
import com.terpinheimer.TerpinheimerConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.util.Text;

@Singleton
public class ClueScrollEventHandler
{
	private static final int CLUE_SCREENSHOT_DELAY_MS = 500;

	private static final Pattern CLUE_COUNT_PATTERN = Pattern.compile(
		"(?:completed|finished)(?: your)?(?: the)?\\s+([0-9,]+)", Pattern.CASE_INSENSITIVE);

	private final Client client;
	private final TerpinheimerConfig config;
	private final ItemManager itemManager;
	private final WebhookMessageBuilder messageBuilder;
	private final WebhookDispatcher dispatcher;
	private final ClientThread clientThread;
	private final ClientScreenshot screenshot;
	private final ScheduledExecutorService scheduledExecutor;

	private volatile int pendingTierOrd = ClueNotifyTier.MEDIUM.ordinal();
	private volatile String pendingCount = "?";
	private volatile long pendingChatAtMs;

	@Inject
	ClueScrollEventHandler(
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
	public void onChatMessage(ChatMessage event)
	{
		if (!config.sendClueScrolls() || !DiscordChatFilters.allows(event.getType()))
		{
			return;
		}
		String raw = Text.removeTags(event.getMessage());
		String low = raw.toLowerCase();
		if (!low.contains("clue") && !low.contains("treasure trail"))
		{
			return;
		}
		ClueNotifyTier t = parseTier(low);
		if (t == null)
		{
			if (low.contains("treasure trail") || low.contains("clue scroll"))
			{
				t = ClueNotifyTier.MEDIUM;
			}
			else
			{
				return;
			}
		}
		pendingTierOrd = t.ordinal();
		pendingChatAtMs = System.currentTimeMillis();
		Matcher m = CLUE_COUNT_PATTERN.matcher(raw);
		if (m.find())
		{
			pendingCount = m.group(1).replace(",", "");
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!config.sendClueScrolls() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		NPC npc = event.getNpc();
		String source = npc != null ? Text.removeTags(npc.getName()) : "";
		if (!isClueLootSource(source))
		{
			return;
		}
		Collection<ItemStack> stacks = event.getItems();
		if (stacks == null || stacks.isEmpty())
		{
			return;
		}
		long total = 0L;
		List<String> markdownLines = new ArrayList<>();
		Integer firstId = null;
		for (ItemStack stack : stacks)
		{
			int id = stack.getId();
			int qty = stack.getQuantity();
			int unit = itemManager.getItemPrice(id);
			long stackVal = (long) unit * qty;
			total += stackVal;
			String name = itemManager.getItemComposition(id).getName();
			String label = String.format("%,d x %s", qty, name);
			String line = WikiLinks.markdownLink(label, WikiLinks.itemPage(name))
				+ " (" + WikiLinks.formatGpCompact(stackVal) + ")";
			markdownLines.add(line);
			if (firstId == null)
			{
				firstId = id;
			}
		}
		markdownLines.sort(Comparator.naturalOrder());
		if (total < config.clueMinValue())
		{
			return;
		}

		ClueNotifyTier effective = ClueNotifyTier.MEDIUM;
		if (System.currentTimeMillis() - pendingChatAtMs < 10_000L)
		{
			ClueNotifyTier[] vals = ClueNotifyTier.values();
			if (pendingTierOrd >= 0 && pendingTierOrd < vals.length)
			{
				effective = vals[pendingTierOrd];
			}
		}
		if (effective.ordinal() < config.clueMinTier().ordinal())
		{
			return;
		}

		String user = client.getLocalPlayer() != null
			? Text.removeTags(client.getLocalPlayer().getName()) : "Player";
		String tierName = capitalizeWords(effective.name());
		String lootMd = String.join("\n", markdownLines);
		String desc = config.clueNotificationMessage()
			.replace("%USERNAME%", user)
			.replace("%CLUE%", tierName)
			.replace("%COUNT%", pendingCount)
			.replace("%LOOT%", lootMd)
			.replace("%VALUE%", WikiLinks.formatGpCompact(total) + " gp")
			.replace("\\n", "\n");
		String thumb = null;
		if (config.clueShowItemIcons() && firstId != null)
		{
			String iname = itemManager.getItemComposition(firstId).getName();
			thumb = WikiLinks.itemIconPngUrl(iname);
		}

		final String thumbFinal = thumb;
		final String descFinal = desc;
		final String tierFinal = tierName;
		final long totalFinal = total;
		final JsonObject embed = messageBuilder.clueRewardDinkStyleEmbed(
			user,
			descFinal,
			thumbFinal,
			tierFinal,
			pendingCount,
			WikiLinks.formatGpCompact(totalFinal) + " gp");

		if (config.clueSendImage())
		{
			scheduledExecutor.schedule(() -> clientThread.invokeLater(() ->
			{
				byte[] png = screenshot.capturePngOrNull();
				String json = messageBuilder.toWebhookJson(embed);
				dispatcher.enqueue(new WebhookPayload(json, png));
			}), CLUE_SCREENSHOT_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		else
		{
			String json = messageBuilder.toWebhookJson(embed);
			dispatcher.enqueue(new WebhookPayload(json, null));
		}
	}

	private static ClueNotifyTier parseTier(String plainLower)
	{
		if (plainLower.contains("master"))
		{
			return ClueNotifyTier.MASTER;
		}
		if (plainLower.contains("elite"))
		{
			return ClueNotifyTier.ELITE;
		}
		if (plainLower.contains("hard"))
		{
			return ClueNotifyTier.HARD;
		}
		if (plainLower.contains("medium"))
		{
			return ClueNotifyTier.MEDIUM;
		}
		if (plainLower.contains("easy"))
		{
			return ClueNotifyTier.EASY;
		}
		if (plainLower.contains("beginner"))
		{
			return ClueNotifyTier.BEGINNER;
		}
		return null;
	}

	private static String capitalizeWords(String enumName)
	{
		String[] p = enumName.toLowerCase().split("_");
		StringBuilder sb = new StringBuilder();
		for (String w : p)
		{
			if (w.isEmpty())
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	private static boolean isClueLootSource(String source)
	{
		String s = Text.standardize(source);
		return s.contains("clue") || s.contains("treasure") || s.contains("casket")
			|| s.contains("reward chest") || s.contains("scroll")
			|| s.contains("mimic");
	}

}
