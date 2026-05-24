package com.terpinheimer.discord;

import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Schedules GPU-safe screenshots and enqueues Discord webhooks with optional PNG attachments.
 */
@Singleton
public class DiscordWebhookCapture
{
	public static final int DEFAULT_SCREENSHOT_DELAY_MS = 500;

	private final ClientScreenshot screenshot;
	private final WebhookMessageBuilder messageBuilder;
	private final WebhookDispatcher dispatcher;

	@Inject
	DiscordWebhookCapture(
		ClientScreenshot screenshot,
		WebhookMessageBuilder messageBuilder,
		WebhookDispatcher dispatcher)
	{
		this.screenshot = screenshot;
		this.messageBuilder = messageBuilder;
		this.dispatcher = dispatcher;
	}

	public void send(
		ScheduledExecutorService scheduler,
		long delayMs,
		boolean includeScreenshot,
		JsonObject embed)
	{
		Runnable deliver = () -> deliver(embed, includeScreenshot);
		if (delayMs > 0)
		{
			scheduler.schedule(deliver, delayMs, TimeUnit.MILLISECONDS);
		}
		else
		{
			deliver.run();
		}
	}

	public void send(ScheduledExecutorService scheduler, boolean includeScreenshot, JsonObject embed)
	{
		send(scheduler, DEFAULT_SCREENSHOT_DELAY_MS, includeScreenshot, embed);
	}

	private void deliver(JsonObject embed, boolean includeScreenshot)
	{
		if (includeScreenshot)
		{
			screenshot.capturePngAfterNextFrame(png ->
			{
				String json = messageBuilder.toWebhookJson(embed);
				dispatcher.enqueue(new WebhookPayload(json, png));
			});
		}
		else
		{
			String json = messageBuilder.toWebhookJson(embed);
			dispatcher.enqueue(new WebhookPayload(json, null));
		}
	}
}
