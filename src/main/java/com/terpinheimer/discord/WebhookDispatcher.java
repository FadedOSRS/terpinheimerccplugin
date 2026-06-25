package com.terpinheimer.discord;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Outbound queue for built-in Discord webhooks (e.g. clan coffer donations).
 */
@Singleton
public class WebhookDispatcher
{
	private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final long MIN_INTERVAL_MS = 2100L;

	private final OkHttpClient http;
	private final ScheduledExecutorService scheduledExecutor;	private final BlockingQueue<WebhookPayload> queue = new LinkedBlockingQueue<>(256);
	private volatile boolean running;
	private volatile long lastSendEnd;
	private long lastInvalidUrlWarnMs;

	@Inject
	WebhookDispatcher(
		OkHttpClient http,
		ScheduledExecutorService scheduledExecutor)
	{
		this.http = http;
		this.scheduledExecutor = scheduledExecutor;
	}
	public void start()
	{
		running = true;
	}

	public void stop()
	{
		running = false;
		queue.clear();
	}

	public void enqueue(WebhookPayload payload)
	{
		String url = effectiveUrl(payload);
		if (!WebhookUrlValidator.isValid(url))
		{
			long now = System.currentTimeMillis();
			if (now - lastInvalidUrlWarnMs > 60_000L)
			{
				lastInvalidUrlWarnMs = now;
				log.warn("Terpinheimer Discord: webhook URL is empty or invalid; message dropped.");
			}
			return;
		}
		if (!queue.offer(payload))
		{
			log.warn("Terpinheimer Discord: webhook queue is full; dropping a message.");
			return;
		}
		scheduleDrain();
	}

	private void scheduleDrain()
	{
		if (!running)
		{
			return;
		}
		scheduledExecutor.execute(this::drainStep);
	}

	private void drainStep()
	{
		if (!running)
		{
			return;
		}
		WebhookPayload job = queue.poll();
		if (job == null)
		{
			return;
		}
		long wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastSendEnd);
		if (wait > 0)
		{
			scheduledExecutor.schedule(() -> sendThenDrain(job), wait, TimeUnit.MILLISECONDS);
			return;
		}
		sendThenDrain(job);
	}

	private void sendThenDrain(WebhookPayload job)
	{
		if (!running)
		{
			return;
		}
		sendWithRetries(job, 0);
		lastSendEnd = System.currentTimeMillis();
		if (!queue.isEmpty())
		{
			scheduleDrain();
		}
	}

	private void sendWithRetries(WebhookPayload job, int attempt)
	{
		if (!running)
		{
			return;
		}
		String url = effectiveUrl(job).trim();
		try (Response response = executePost(url, job))
		{
			if (response.isSuccessful())
			{
				return;
			}
			if (response.code() == 429 && attempt < 4)
			{
				long delayMs = retryAfterMs(response);
				scheduledExecutor.schedule(
					() -> sendWithRetries(job, attempt + 1), delayMs, TimeUnit.MILLISECONDS);
				return;
			}
			if (response.code() >= 500 && attempt < 4)
			{
				scheduledExecutor.schedule(
					() -> sendWithRetries(job, attempt + 1), backoffMs(attempt), TimeUnit.MILLISECONDS);
				return;
			}
			String errBody = readErrorBodySnippet(response);
			log.warn("Terpinheimer Discord webhook rejected: HTTP {} {}", response.code(), errBody);
		}
		catch (IOException e)
		{
			if (attempt >= 4)
			{
				log.warn("Terpinheimer Discord webhook I/O error: {}", e.getMessage());
				return;
			}
			scheduledExecutor.schedule(
				() -> sendWithRetries(job, attempt + 1), backoffMs(attempt), TimeUnit.MILLISECONDS);
		}
	}

	private static long retryAfterMs(Response response)
	{
		String ra = response.header("Retry-After");
		int seconds = 2;
		try
		{
			if (ra != null)
			{
				seconds = Integer.parseInt(ra);
			}
		}
		catch (NumberFormatException ignored)
		{
		}
		return Math.min(Math.max(seconds, 1), 60) * 1000L;
	}

	private static long backoffMs(int attempt)
	{
		return 400L * (attempt + 1);
	}

	private String effectiveUrl(WebhookPayload payload)
	{
		String override = payload.getWebhookUrlOverride();
		return override != null ? override.trim() : "";
	}

	private Response executePost(String url, WebhookPayload job) throws IOException
	{
		RequestBody body = RequestBody.create(JSON, job.getJsonBody());
		Request request = new Request.Builder().url(url).post(body).build();
		return http.newCall(request).execute();
	}

	private static String readErrorBodySnippet(Response response)
	{
		try
		{
			ResponseBody rb = response.body();
			if (rb == null)
			{
				return "";
			}
			String s = rb.string();
			if (s.length() > 500)
			{
				return s.substring(0, 500) + "...";
			}
			return s;
		}
		catch (Exception e)
		{
			return "(" + e.getMessage() + ")";
		}
	}
}
