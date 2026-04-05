package com.terpinheimer.map;

import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async POST to a clan-hosted live map API. Compatible with the pattern used by the
 * <a href="https://github.com/s0lved/goblin-scape-clan-plugin">Goblin Scape clan plugin</a>:
 * {@code POST {baseUrl}/post}, JSON body, {@code Authorization} header set to the shared key.
 */
@Singleton
public class LiveMapService
{
	private static final Logger log = LoggerFactory.getLogger(LiveMapService.class);
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient http;

	@Inject
	LiveMapService(OkHttpClient http)
	{
		this.http = http;
	}

	public static String buildPostUrl(String baseUrl)
	{
		if (baseUrl == null)
		{
			return null;
		}
		String url = baseUrl.trim();
		if (url.isEmpty())
		{
			return null;
		}
		if (url.endsWith("/"))
		{
			return url + "post";
		}
		return url + "/post";
	}

	public void postPlayerLocation(String postUrl, String authorizationKey, String jsonBody)
	{
		if (postUrl == null || authorizationKey == null || authorizationKey.trim().isEmpty()
			|| jsonBody == null || jsonBody.isEmpty())
		{
			return;
		}
		Request req = new Request.Builder()
			.url(postUrl)
			.header("Authorization", authorizationKey.trim())
			.post(RequestBody.create(JSON, jsonBody))
			.build();
		http.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Live map POST failed: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful())
					{
						log.debug("Live map POST HTTP {}", r.code());
					}
				}
			}
		});
	}
}
