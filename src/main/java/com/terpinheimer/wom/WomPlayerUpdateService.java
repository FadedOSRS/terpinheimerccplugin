package com.terpinheimer.wom;

import java.io.IOException;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Triggers a Wise Old Man hiscores update for the logged-in player (POST /v2/players/:username),
 * matching the hub plugin / XP Updater behavior on logout.
 */
@Singleton
public class WomPlayerUpdateService
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String API = "https://api.wiseoldman.net/v2";

	private final OkHttpClient http;

	@Inject
	public WomPlayerUpdateService(OkHttpClient http)
	{
		this.http = http;
	}

	/**
	 * Asks WOM to (re)import the player from the hiscores. Username should be the in-game
	 * name (spaces are normalized to underscores; casing is lowercased for the URL).
	 */
	public void requestUpdate(String displayName) throws IOException
	{
		if (displayName == null || displayName.isBlank())
		{
			return;
		}
		String slug = displayName.trim().replace('\u00A0', ' ').replace(' ', '_').toLowerCase(Locale.ENGLISH);
		HttpUrl url = HttpUrl.parse(API).newBuilder()
			.addPathSegment("players")
			.addPathSegment(slug)
			.build();
		Request req = new Request.Builder()
			.url(url)
			.post(RequestBody.create(JSON, ""))
			.header("Accept", "application/json")
			.build();
		try (Response res = http.newCall(req).execute())
		{
			// 200 / 201 = success; 400 / 429 etc. — caller may log; avoid spamming throws
			if (!res.isSuccessful() && res.body() != null)
			{
				res.body().close();
			}
		}
	}
}
