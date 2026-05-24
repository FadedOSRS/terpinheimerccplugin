package com.terpinheimer.discord;

import com.terpinheimer.TerpinheimerConfig;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class CombatAchievementEventHandler
{
	private final TerpinheimerConfig config;
	private final WebhookMessageBuilder messageBuilder;
	private final DiscordWebhookCapture webhookCapture;
	private final ScheduledExecutorService scheduledExecutor;

	@Inject
	CombatAchievementEventHandler(
		TerpinheimerConfig config,
		WebhookMessageBuilder messageBuilder,
		DiscordWebhookCapture webhookCapture,
		ScheduledExecutorService scheduledExecutor)
	{
		this.config = config;
		this.messageBuilder = messageBuilder;
		this.webhookCapture = webhookCapture;
		this.scheduledExecutor = scheduledExecutor;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.sendCombatAchievements() || !DiscordChatFilters.allows(event.getType()))
		{
			return;
		}
		String plain = Text.removeTags(event.getMessage());
		if (!plain.toLowerCase().contains("combat achievement"))
		{
			return;
		}
		webhookCapture.send(scheduledExecutor, config.progressionSendImage(),
			messageBuilder.simpleEmbed("Combat achievement", plain));
	}
}
