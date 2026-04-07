package com.terpinheimer.discord;

import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class DeathEventHandler
{
	private static final int DEATH_SCREENSHOT_DELAY_MS = 500;

	private final Client client;
	private final TerpinheimerConfig config;
	private final WebhookMessageBuilder messageBuilder;
	private final WebhookDispatcher dispatcher;
	private final ClientThread clientThread;
	private final ClientScreenshot screenshot;
	private final ScheduledExecutorService scheduledExecutor;

	@Inject
	DeathEventHandler(
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
		String user = client.getLocalPlayer() != null
			? Text.removeTags(client.getLocalPlayer().getName()) : "Player";
		String deathReason = resolveDeathReason(actor);
		final JsonObject embed = messageBuilder.deathDinkStyleEmbed(user, deathReason);
		scheduledExecutor.schedule(() -> clientThread.invokeLater(() ->
		{
			byte[] png = screenshot.capturePngOrNull();
			String json = messageBuilder.toWebhookJson(embed);
			dispatcher.enqueue(new WebhookPayload(json, png));
		}), DEATH_SCREENSHOT_DELAY_MS, TimeUnit.MILLISECONDS);
	}

	/** Fighting target at death when available; otherwise environmental / unknown. */
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
