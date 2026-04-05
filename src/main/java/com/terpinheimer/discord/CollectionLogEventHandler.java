package com.terpinheimer.discord;

import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class CollectionLogEventHandler
{
	private static final int COLLECTION_SCREENSHOT_DELAY_MS = 500;

	private static final Pattern ITEM_ADDED = Pattern.compile(
		"(?i)added (?:an? |the )?(.+?) to your collection");
	private static final Pattern NEW_ITEM = Pattern.compile("(?i)new item[^:]*:?\\s*(.+)");

	private final Client client;
	private final TerpinheimerConfig config;
	private final WebhookMessageBuilder messageBuilder;
	private final WebhookDispatcher dispatcher;
	private final ClientThread clientThread;
	private final ClientScreenshot screenshot;
	private final ScheduledExecutorService scheduledExecutor;

	@Inject
	CollectionLogEventHandler(
		Client client,
		TerpinheimerConfig config,
		WebhookMessageBuilder messageBuilder,
		WebhookDispatcher dispatcher,
		ClientThread clientThread,
		ClientScreenshot screenshot,
		ScheduledExecutorService scheduledExecutor)
	{
		this.client = client;
		this.config = config;
		this.messageBuilder = messageBuilder;
		this.dispatcher = dispatcher;
		this.clientThread = clientThread;
		this.screenshot = screenshot;
		this.scheduledExecutor = scheduledExecutor;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.sendCollectionLog() || client.getGameState() != GameState.LOGGED_IN
			|| !DiscordChatFilters.allows(event.getType()))
		{
			return;
		}
		String plain = Text.removeTags(event.getMessage());
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
		final JsonObject embed = messageBuilder.collectionLogDinkStyleEmbed(user, desc);
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
		Matcher m = ITEM_ADDED.matcher(plain);
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
}
