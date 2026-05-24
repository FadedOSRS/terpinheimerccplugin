package com.terpinheimer.discord;

import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class PetEventHandler
{
	private final Client client;
	private final TerpinheimerConfig config;
	private final WebhookMessageBuilder messageBuilder;
	private final DiscordWebhookCapture webhookCapture;
	private final ScheduledExecutorService scheduledExecutor;

	@Inject
	PetEventHandler(
		Client client,
		TerpinheimerConfig config,
		WebhookMessageBuilder messageBuilder,
		DiscordWebhookCapture webhookCapture,
		ScheduledExecutorService scheduledExecutor)
	{
		this.client = client;
		this.config = config;
		this.messageBuilder = messageBuilder;
		this.webhookCapture = webhookCapture;
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
		if (!isPetDropMessage(plain.toLowerCase()))
		{
			return;
		}
		String user = client.getLocalPlayer() != null
			? Text.removeTags(client.getLocalPlayer().getName()) : "Player";
		String desc = config.petNotifyMessage()
			.replace("%USERNAME%", user)
			.replace("%GAME_MESSAGE%", plain)
			.replace("\\n", "\n");
		JsonObject embed = messageBuilder.petDinkStyleEmbed(user, desc);
		webhookCapture.send(scheduledExecutor, config.petSendImage(), embed);
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
