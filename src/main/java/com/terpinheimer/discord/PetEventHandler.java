package com.terpinheimer.discord;

import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class PetEventHandler
{
	private static final int PET_SCREENSHOT_DELAY_MS = 500;

	private final Client client;
	private final TerpinheimerConfig config;
	private final WebhookMessageBuilder messageBuilder;
	private final WebhookDispatcher dispatcher;
	private final ClientThread clientThread;
	private final ClientScreenshot screenshot;
	private final ScheduledExecutorService scheduledExecutor;

	@Inject
	PetEventHandler(
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
		if (!config.sendPet() || client.getGameState() != GameState.LOGGED_IN
			|| !DiscordChatFilters.allows(event.getType()))
		{
			return;
		}
		String plain = Text.removeTags(event.getMessage());
		String low = plain.toLowerCase();
		if (!isPetDropMessage(low))
		{
			return;
		}
		String user = client.getLocalPlayer() != null
			? Text.removeTags(client.getLocalPlayer().getName()) : "Player";
		String desc = config.petNotifyMessage()
			.replace("%USERNAME%", user)
			.replace("%GAME_MESSAGE%", plain)
			.replace("\\n", "\n");
		final JsonObject embed = messageBuilder.petDinkStyleEmbed(user, desc);
		if (config.petSendImage())
		{
			scheduledExecutor.schedule(() -> clientThread.invokeLater(() ->
			{
				byte[] png = screenshot.capturePngOrNull();
				String json = messageBuilder.toWebhookJson(embed);
				dispatcher.enqueue(new WebhookPayload(json, png));
			}), PET_SCREENSHOT_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		else
		{
			String json = messageBuilder.toWebhookJson(embed);
			dispatcher.enqueue(new WebhookPayload(json, null));
		}
	}

	private static boolean isPetDropMessage(String lower)
	{
		return lower.contains("funny feeling")
			|| lower.contains("being followed")
			|| lower.contains("something appears to be following")
			|| lower.contains("you feel something weird")
			|| lower.contains("you have a funny feeling like you would have been followed");
	}
}
