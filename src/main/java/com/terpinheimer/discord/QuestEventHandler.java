package com.terpinheimer.discord;

import com.terpinheimer.TerpinheimerConfig;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class QuestEventHandler
{
	private final TerpinheimerConfig config;
	private final WebhookMessageBuilder messageBuilder;
	private final DiscordWebhookCapture webhookCapture;
	private final ScheduledExecutorService scheduledExecutor;

	@Inject
	QuestEventHandler(
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
		if (!config.sendQuests() || !DiscordChatFilters.allows(event.getType()))
		{
			return;
		}
		String plain = Text.removeTags(event.getMessage());
		String low = plain.toLowerCase();
		boolean questLine = low.contains("quest complete")
			|| low.contains("congratulations, you've completed")
			|| (low.contains("you have completed") && low.contains("quest"));
		if (!questLine)
		{
			return;
		}
		webhookCapture.send(scheduledExecutor, config.progressionSendImage(),
			messageBuilder.simpleEmbed("Quest", plain));
	}
}
