package com.terpinheimer.discord;

import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.util.Text;

@Singleton
public class LootEventHandler
{
	private static final int BOSS_COMBAT_THRESHOLD = 100;
	private static final int LOOT_SCREENSHOT_DELAY_MS = 500;

	private final Client client;
	private final TerpinheimerConfig config;
	private final ItemManager itemManager;
	private final WebhookMessageBuilder messageBuilder;
	private final WebhookDispatcher dispatcher;
	private final ClientThread clientThread;
	private final ClientScreenshot screenshot;
	private final ScheduledExecutorService scheduledExecutor;

	@Inject
	LootEventHandler(
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
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (!config.sendLoot())
		{
			return;
		}
		NPC npc = event.getNpc();
		boolean boss = npc != null && npc.getCombatLevel() >= BOSS_COMBAT_THRESHOLD;
		String source = npc != null ? Text.removeTags(npc.getName()) : "NPC";
		processLoot(source, event.getItems(), boss);
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		if (!config.sendLoot() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (!config.lootIncludePkLoot())
		{
			return;
		}
		Player p = event.getPlayer();
		String source = p != null ? "Player: " + Text.removeTags(p.getName()) : "Player loot";
		processLoot(source, event.getItems(), false);
	}

	private void processLoot(String sourceLabel, Collection<ItemStack> stacks, boolean bossStyle)
	{
		if (stacks == null || stacks.isEmpty())
		{
			return;
		}
		boolean clueLike = isClueLikeSource(sourceLabel);
		if (clueLike && config.sendClueScrolls() && !bossStyle)
		{
			return;
		}
		if (clueLike && !config.lootIncludeClueLoot() && !bossStyle)
		{
			return;
		}
		if (isBaGambleSource(sourceLabel) && !config.lootIncludeBaGambles() && !bossStyle)
		{
			return;
		}

		long total = 0L;
		List<String> markdownLines = new ArrayList<>();
		Integer firstItemId = null;
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
			if (firstItemId == null)
			{
				firstItemId = id;
			}
		}
		markdownLines.sort(Comparator.naturalOrder());

		int threshold = config.lootValueThreshold();
		if (total < threshold)
		{
			return;
		}

		String title = bossStyle ? "Boss Drop" : "Loot Drop";
		final long valueFinal = total;
		String user = client.getLocalPlayer() != null
			? Text.removeTags(client.getLocalPlayer().getName()) : "Player";
		String lootMd = String.join("\n", markdownLines);
		String sourceMd = WikiLinks.markdownLink(sourceLabel, WikiLinks.npcPage(sourceLabel));
		String desc = config.lootNotificationMessage()
			.replace("%USERNAME%", user)
			.replace("%LOOT%", lootMd)
			.replace("%SOURCE%", sourceMd)
			.replace("%VALUE%", WikiLinks.formatGpCompact(valueFinal) + " gp")
			.replace("\\n", "\n");
		String thumb = null;
		if (config.lootShowLootIcons() && firstItemId != null)
		{
			String iname = itemManager.getItemComposition(firstItemId).getName();
			thumb = WikiLinks.itemIconPngUrl(iname);
		}

		final String thumbFinal = thumb;
		final String titleFinal = title;
		final JsonObject embed = messageBuilder.lootDropDinkStyleEmbed(
			user,
			titleFinal,
			desc,
			thumbFinal,
			"—",
			WikiLinks.formatGpCompact(valueFinal) + " gp",
			"—");

		if (config.lootSendImage())
		{
			scheduledExecutor.schedule(() -> clientThread.invokeLater(() ->
			{
				byte[] png = screenshot.capturePngOrNull();
				String json = messageBuilder.toWebhookJson(embed);
				dispatcher.enqueue(new WebhookPayload(json, png));
			}), LOOT_SCREENSHOT_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		else
		{
			String json = messageBuilder.toWebhookJson(embed);
			dispatcher.enqueue(new WebhookPayload(json, null));
		}
	}

	private static boolean isClueLikeSource(String source)
	{
		String s = Text.standardize(source);
		return s.contains("clue") || s.contains("treasure") || s.contains("casket")
			|| s.contains("reward chest") || s.contains("scroll box") || s.contains("scroll")
			|| s.contains("mimic");
	}

	private static boolean isBaGambleSource(String source)
	{
		String s = Text.standardize(source);
		return s.contains("penance") || s.contains("barbarian") || s.contains("gamble")
			|| s.contains("rewards guardian");
	}

}
