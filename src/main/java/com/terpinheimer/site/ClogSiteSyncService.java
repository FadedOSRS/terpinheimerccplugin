package com.terpinheimer.site;

import java.io.IOException;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async HTTPS POST of collection log / chronicle payload to the clan website.
 * <p>
 * For {@code terpinheimercc.com}, a plain RuneLite sync secret is sent in JSON as {@code syncToken} and
 * mirrored on {@code X-Sync-Token} / {@code Sync-Token} when no {@code Authorization} header is sent.
 * An {@code Authorization: Bearer …} header is added only when you paste a session JWT (or a value that
 * looks like a JWT). Other hosts still receive the secret as a raw {@code Authorization} value.
 */
@Singleton
public class ClogSiteSyncService
{
	private static final Logger log = LoggerFactory.getLogger(ClogSiteSyncService.class);
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final int CHAT_DETAIL_MAX = 140;

	private final OkHttpClient http;
	private final Client client;
	private final ClientThread clientThread;

	@Inject
	ClogSiteSyncService(OkHttpClient http, Client client, ClientThread clientThread)
	{
		this.http = http;
		this.client = client;
		this.clientThread = clientThread;
	}

	/**
	 * Same as {@link #postClogJsonAsync(String, String, String, boolean)} with no in-game chat for the result
	 * (logout / hop sync).
	 */
	public void postClogJsonAsync(String httpsPostUrl, String authorizationSecret, String jsonBody)
	{
		postClogJsonAsync(httpsPostUrl, authorizationSecret, jsonBody, false);
	}

	/**
	 * Same HTTPS + auth rules as collection log sync, for other JSON POSTs (e.g. clan roster snapshot).
	 */
	public void postSiteJsonAsync(String httpsPostUrl, String authorizationSecret, String jsonBody)
	{
		postSiteJsonAsync(httpsPostUrl, authorizationSecret, jsonBody, false);
	}

	/**
	 * @param notifyChat when true, posts brief game chat when the request starts and finishes (manual roster POST).
	 */
	public void postSiteJsonAsync(String httpsPostUrl, String authorizationSecret, String jsonBody, boolean notifyChat)
	{
		postJsonToSite(httpsPostUrl, authorizationSecret, jsonBody, notifyChat ? SiteSyncChat.ROSTER : SiteSyncChat.NONE);
	}

	/**
	 * @param notifyChat when true, posts brief game chat when the request finishes (manual sync from UI).
	 */
	public void postClogJsonAsync(String httpsPostUrl, String authorizationSecret, String jsonBody, boolean notifyChat)
	{
		postJsonToSite(httpsPostUrl, authorizationSecret, jsonBody, notifyChat ? SiteSyncChat.COLLECTION_LOG : SiteSyncChat.NONE);
	}

	private void postJsonToSite(String httpsPostUrl, String authorizationSecret, String jsonBody, SiteSyncChat syncChat)
	{
		if (httpsPostUrl == null || httpsPostUrl.trim().isEmpty()
			|| authorizationSecret == null || authorizationSecret.trim().isEmpty()
			|| jsonBody == null || jsonBody.isEmpty())
		{
			return;
		}
		String trimmedIn = httpsPostUrl.trim();
		String url = normalizeSitePostUrl(trimmedIn);
		if (!url.equals(trimmedIn))
		{
			log.info("Terpinheimer: normalized a legacy API path in your sync URL. Using: {}", url);
		}
		if (!url.startsWith("https://"))
		{
			return;
		}
		if (syncChat.notify)
		{
			chat(ChatMessageType.CONSOLE, syncChat.pendingMessage);
		}
		Request.Builder rb = new Request.Builder()
			.url(url)
			.header("Content-Type", "application/json; charset=utf-8")
			.post(RequestBody.create(JSON, jsonBody));
		String secretTrimmed = authorizationSecret.trim();
		String auth = authorizationHeaderValue(url, secretTrimmed);
		if (auth != null && !auth.isEmpty())
		{
			rb.header("Authorization", auth);
		}
		else if (isTerpinheimerClanSite(url.toLowerCase(Locale.ROOT)))
		{
			// Some stacks read sync auth from headers before JSON; harmless if ignored.
			rb.header("X-Sync-Token", secretTrimmed);
			rb.header("Sync-Token", secretTrimmed);
		}
		Request req = rb.build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
				log.warn("Clog site sync POST failed: {}", msg);
				if (syncChat.notify)
				{
					chat(ChatMessageType.CONSOLE, syncChat.failNetworkPrefix + truncate(msg));
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (r.isSuccessful())
					{
						log.debug("Clog site sync POST OK {}", r.code());
						if (syncChat.notify)
						{
							chat(ChatMessageType.CONSOLE, syncChat.successMessage);
						}
						return;
					}
					String detail = httpErrorDetail(r);
					log.warn("Clog site sync POST HTTP {} {}", r.code(), detail);
					if (syncChat.notify)
					{
						chat(ChatMessageType.CONSOLE,
							syncChat.failHttpPrefix + r.code() + "): " + truncate(detail));
						if (syncChat == SiteSyncChat.ROSTER && r.code() == 404)
						{
							chat(ChatMessageType.CONSOLE,
								"Roster 404: that host has no POST handler for this path yet (common on local dev). Add the API route on the site or set “Clan roster sync API” to a URL where it exists.");
						}
					}
				}
			}
		});
	}

	private enum SiteSyncChat
	{
		NONE(false, "", "", "", ""),
		COLLECTION_LOG(true,
			"Sending collection log to clan site…",
			"Collection log sync succeeded. Refresh the site if tiles look stale.",
			"Collection log sync failed (network): ",
			"Collection log sync failed (HTTP "),
		ROSTER(true,
			"Posting clan roster to site…",
			"Clan roster sync succeeded. Refresh the site if the roster looks stale.",
			"Clan roster sync failed (network): ",
			"Clan roster sync failed (HTTP ");

		private final boolean notify;
		private final String pendingMessage;
		private final String successMessage;
		private final String failNetworkPrefix;
		/** Appends status code and detail, e.g. {@code failHttpPrefix + "404): " + detail}. */
		private final String failHttpPrefix;

		SiteSyncChat(boolean notify, String pendingMessage, String successMessage,
			String failNetworkPrefix, String failHttpPrefix)
		{
			this.notify = notify;
			this.pendingMessage = pendingMessage;
			this.successMessage = successMessage;
			this.failNetworkPrefix = failNetworkPrefix;
			this.failHttpPrefix = failHttpPrefix;
		}
	}

	private static String httpErrorDetail(Response r)
	{
		ResponseBody body = r.body();
		if (body == null)
		{
			return r.message() != null ? r.message() : "";
		}
		try
		{
			String raw = body.string();
			return raw != null ? raw.trim() : "";
		}
		catch (IOException e)
		{
			return r.message() != null ? r.message() : "";
		}
	}

	private void chat(ChatMessageType type, String message)
	{
		clientThread.invokeLater(() ->
			client.addChatMessage(type, "Terpinheimer", message, "Terpinheimer"));
	}

	private static String truncate(String s)
	{
		if (s == null || s.isEmpty())
		{
			return "";
		}
		String oneLine = s.replace('\n', ' ').replace('\r', ' ');
		return oneLine.length() <= CHAT_DETAIL_MAX ? oneLine : oneLine.substring(0, CHAT_DETAIL_MAX) + "…";
	}

	/**
	 * Legacy RuneLite docs used {@code /api/runelite/clog-sync} and {@code /api/runelite/roster-sync}, which are
	 * not registered on Terpinheimer clan hosts (404). Rewrites to {@code /api/clog/sync} and
	 * {@code /api/clan/roster/sync} so saved profiles keep working.
	 */
	static String normalizeSitePostUrl(String trimmedHttpsUrl)
	{
		if (trimmedHttpsUrl == null || trimmedHttpsUrl.isEmpty())
		{
			return trimmedHttpsUrl;
		}
		String low = trimmedHttpsUrl.toLowerCase(Locale.ROOT);
		if (!isTerpinheimerClanSite(low))
		{
			return trimmedHttpsUrl;
		}
		String u = trimmedHttpsUrl;
		if (low.contains("/api/runelite/clog-sync"))
		{
			u = u.replace("/api/runelite/clog-sync", "/api/clog/sync");
			low = u.toLowerCase(Locale.ROOT);
		}
		if (low.contains("/api/runelite/roster-sync"))
		{
			u = u.replace("/api/runelite/roster-sync", "/api/clan/roster/sync");
		}
		return u;
	}

	private static boolean isTerpinheimerClanSite(String urlLower)
	{
		return urlLower.contains("terpinheimercc.com") || urlLower.contains("terpinheimercc.onrender.com");
	}

	/**
	 * terpinheimercc.com: plain {@code RUNELITE_CLOG_SYNC_SECRET} values must not be sent as fake
	 * {@code Bearer} tokens (middleware may reject before reading JSON {@code syncToken}). Session JWTs
	 * use {@code Bearer …} in config. Other hosts keep the legacy raw {@code Authorization} header.
	 *
	 * @return value for {@code Authorization}, or {@code null} to omit the header
	 */
	static String authorizationHeaderValue(String normalizedHttpsUrl, String secretTrimmed)
	{
		if (secretTrimmed.regionMatches(true, 0, "Bearer ", 0, 7))
		{
			return secretTrimmed;
		}
		if (!isTerpinheimerClanSite(normalizedHttpsUrl.toLowerCase(Locale.ROOT)))
		{
			return secretTrimmed;
		}
		if (looksLikeJwt(secretTrimmed))
		{
			return "Bearer " + secretTrimmed;
		}
		return null;
	}

	private static boolean looksLikeJwt(String s)
	{
		if (s == null)
		{
			return false;
		}
		String t = s.trim();
		if (t.length() < 20)
		{
			return false;
		}
		int dots = 0;
		for (int i = 0; i < t.length(); i++)
		{
			if (t.charAt(i) == '.')
			{
				dots++;
			}
		}
		return dots >= 2;
	}
}
