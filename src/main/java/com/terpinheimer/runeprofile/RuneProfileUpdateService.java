package com.terpinheimer.runeprofile;

import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import net.runelite.client.RuneLiteProperties;

/**
 * Pushes a profile snapshot to <a href="https://www.runeprofile.com/">RuneProfile</a> using their
 * public API ({@code POST /profiles}). Anyone can view updated stats on the website; this plugin
 * triggers the sync from the logged-in client (same session rules as the Wise Old Man logout update).
 */
@Singleton
public class RuneProfileUpdateService
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String API = "https://api.runeprofile.com/profiles";

	private final OkHttpClient http;

	@Inject
	public RuneProfileUpdateService(OkHttpClient http)
	{
		this.http = http;
	}

	public void postProfileJson(String jsonBody) throws IOException
	{
		if (jsonBody == null || jsonBody.isBlank())
		{
			return;
		}
		String rl = RuneLiteProperties.getVersion();
		String ua = "Terpinheimer/2.0.0 RuneLite/" + (rl != null ? rl : "unknown");
		Request req = new Request.Builder()
			.url(API)
			.header("Content-Type", "application/json; charset=utf-8")
			.header("User-Agent", ua)
			.post(RequestBody.create(JSON, jsonBody))
			.build();
		try (Response res = http.newCall(req).execute())
		{
			if (!res.isSuccessful())
			{
				ResponseBody b = res.body();
				if (b != null)
				{
					b.close();
				}
			}
		}
	}
}
