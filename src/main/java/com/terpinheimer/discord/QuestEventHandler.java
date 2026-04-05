package com.terpinheimer.discord;

import com.terpinheimer.TerpinheimerConfig;
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
	private final WebhookDispatcher dispatcher;

	@Inject
	QuestEventHandler(
		TerpinheimerConfig config,
		WebhookMessageBuilder messageBuilder,
		WebhookDispatcher dispatcher)
	{
		this.config = config;
		this.messageBuilder = messageBuilder;
		this.dispatcher = dispatcher;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.sendQuests() || !DiscordChatFilters.allows(event.getType()))
		{
			return;
		}
		String plain = Text.removeTags(event.getMessage()).toLowerCase();
		boolean questLine = plain.contains("quest complete")
			|| plain.contains("congratulations, you've completed")
			|| (plain.contains("you have completed") && plain.contains("quest"));
		if (!questLine)
		{
			return;
		}
		String json = messageBuilder.toWebhookJson(
			messageBuilder.simpleEmbed("Quest", Text.removeTags(event.getMessage())));
		dispatcher.enqueue(new WebhookPayload(json, null));
	}
}
