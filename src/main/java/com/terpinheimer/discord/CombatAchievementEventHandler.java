package com.terpinheimer.discord;

import com.terpinheimer.TerpinheimerConfig;
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
	private final WebhookDispatcher dispatcher;

	@Inject
	CombatAchievementEventHandler(
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
		if (!config.sendCombatAchievements() || !DiscordChatFilters.allows(event.getType()))
		{
			return;
		}
		String plain = Text.removeTags(event.getMessage()).toLowerCase();
		if (!plain.contains("combat achievement"))
		{
			return;
		}
		String json = messageBuilder.toWebhookJson(
			messageBuilder.simpleEmbed("Combat achievement", Text.removeTags(event.getMessage())));
		dispatcher.enqueue(new WebhookPayload(json, null));
	}
}
