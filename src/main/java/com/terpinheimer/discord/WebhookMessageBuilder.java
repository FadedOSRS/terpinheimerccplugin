package com.terpinheimer.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Builds Discord webhook JSON for built-in clan features (e.g. coffer donations). */
@Singleton
public class WebhookMessageBuilder
{
	private static final int DESC_MAX = 3800;

	private final Gson gson;

	@Inject
	WebhookMessageBuilder(Gson gson)
	{
		this.gson = gson;
	}

	public String toWebhookJson(JsonObject embed)
	{
		JsonArray embeds = new JsonArray();
		embeds.add(embed);
		JsonObject root = new JsonObject();
		root.addProperty("username", "Terpinheimer");
		root.add("embeds", embeds);
		return gson.toJson(root);
	}

	public JsonObject dinkChromeEmbed(
		int color,
		String authorName,
		String title,
		String description,
		String thumbnailUrlOrNull,
		JsonArray fieldsOrNull)
	{
		JsonObject embed = new JsonObject();
		embed.addProperty("color", color);
		JsonObject author = new JsonObject();
		author.addProperty("name", authorName);
		embed.add("author", author);
		embed.addProperty("title", title);
		embed.addProperty("description", truncate(description, DESC_MAX));
		if (thumbnailUrlOrNull != null && !thumbnailUrlOrNull.isEmpty())
		{
			JsonObject th = new JsonObject();
			th.addProperty("url", thumbnailUrlOrNull);
			embed.add("thumbnail", th);
		}
		if (fieldsOrNull != null && fieldsOrNull.size() > 0)
		{
			embed.add("fields", fieldsOrNull);
		}
		addTerpinheimerFooter(embed);
		return embed;
	}

	private void addTerpinheimerFooter(JsonObject embed)
	{
		DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
		String ts = fmt.format(LocalDateTime.now());
		String footerText = truncate("Powered by Terpinheimer • " + ts, 2048);
		JsonObject footer = new JsonObject();
		footer.addProperty("text", footerText);
		embed.add("footer", footer);
	}

	private static String truncate(String s, int max)
	{
		if (s.length() <= max)
		{
			return s;
		}
		return s.substring(0, max - 3) + "...";
	}
}
