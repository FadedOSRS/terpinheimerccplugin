package com.terpinheimer.discord;

import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class DeathEventHandler
{
	private final Client client;
	private final TerpinheimerConfig config;
	private final WebhookMessageBuilder messageBuilder;
	private final DiscordWebhookCapture webhookCapture;
	private final ScheduledExecutorService scheduledExecutor;

	@Inject
	DeathEventHandler(
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
	public void onActorDeath(ActorDeath event)
	{
		if (!config.sendDeaths() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Actor actor = event.getActor();
		if (client.getLocalPlayer() == null || actor != client.getLocalPlayer())
		{
			return;
		}
		String user = Text.removeTags(client.getLocalPlayer().getName());
		String deathReason = resolveDeathReason(actor);
		JsonObject embed = messageBuilder.deathDinkStyleEmbed(user, deathReason);
		webhookCapture.send(scheduledExecutor, true, embed);
	}

	private static String resolveDeathReason(Actor deceased)
	{
		if (deceased == null)
		{
			return "";
		}
		Actor source = deceased.getInteracting();
		if (source == null)
		{
			return "";
		}
		String name = source.getName();
		if (name == null || name.isBlank())
		{
			return "";
		}
		return Text.removeTags(name).trim();
	}
}
