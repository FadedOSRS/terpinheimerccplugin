package com.terpinheimer.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single outbound queue for the one configured webhook: rate limiting and retries.
 */
@Singleton
public class WebhookDispatcher
{
	private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final long MIN_INTERVAL_MS = 2100L;
	/** Must match multipart filename and {@code attachment://} in embed (Discord API). */
	private static final String SCREENSHOT_FILENAME = "screenshot.png";

	private final TerpinheimerConfig config;
	private final OkHttpClient http;
	private final Gson gson;
	private final ScheduledExecutorService scheduledExecutor;
	private final BlockingQueue<WebhookPayload> queue = new LinkedBlockingQueue<>(256);
	private volatile boolean running;
	private volatile long lastSendEnd;
	private long lastInvalidUrlWarnMs;

	@Inject
	WebhookDispatcher(
		TerpinheimerConfig config,
		OkHttpClient http,
		Gson gson,
		ScheduledExecutorService scheduledExecutor)
	{
		this.config = config;
		this.http = http;
		this.gson = gson;
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
				log.warn("Terpinheimer Discord: webhook URL is empty or invalid; notifications are disabled until it is set.");
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
		if (override != null && !override.trim().isEmpty())
		{
			return override.trim();
		}
		return config.webhookUrl();
	}

	private Response executePost(String url, WebhookPayload job) throws IOException
	{
		byte[] png = job.getImagePng();
		if (png != null && png.length > 0)
		{
			String payloadJson = jsonWithScreenshotInEmbed(gson, job.getJsonBody(), SCREENSHOT_FILENAME);
			MultipartBody body = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("payload_json", payloadJson)
				.addFormDataPart("files[0]", SCREENSHOT_FILENAME,
					RequestBody.create(MediaType.parse("image/png"), png))
				.build();
			Request request = new Request.Builder().url(url).post(body).build();
			return http.newCall(request).execute();
		}
		RequestBody body = RequestBody.create(JSON, job.getJsonBody());
		Request request = new Request.Builder().url(url).post(body).build();
		return http.newCall(request).execute();
	}

	/**
	 * Discord requires {@code attachments} in {@code payload_json} for multipart uploads, and
	 * {@code embed.image.url = attachment://filename} so the image shows inside the embed (not only
	 * as a loose attachment).
	 */
	static String jsonWithScreenshotInEmbed(Gson gson, String jsonBody, String filename)
	{
		try
		{
			JsonObject root = gson.fromJson(jsonBody, JsonObject.class);
			if (root == null)
			{
				return jsonBody;
			}

			JsonArray attachments = new JsonArray();
			JsonObject att = new JsonObject();
			att.addProperty("id", 0);
			att.addProperty("filename", filename);
			attachments.add(att);
			root.add("attachments", attachments);

			if (root.has("embeds"))
			{
				JsonArray embeds = root.getAsJsonArray("embeds");
				if (embeds.size() > 0 && embeds.get(0).isJsonObject())
				{
					JsonObject embed = embeds.get(0).getAsJsonObject();
					JsonObject image = new JsonObject();
					image.addProperty("url", "attachment://" + filename);
					embed.add("image", image);
				}
			}
			return gson.toJson(root);
		}
		catch (Exception e)
		{
			log.warn("Terpinheimer Discord: could not attach screenshot to embed: {}", e.getMessage());
			return jsonBody;
		}
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
