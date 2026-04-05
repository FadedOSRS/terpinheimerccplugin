package com.terpinheimer.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.util.Text;

/**
 * Builds Discord webhook JSON (embeds) in a Dink-like style.
 */
@Singleton
public class WebhookMessageBuilder
{
	private static final int EMBED_COLOR = 0x5865F2;
	private static final int DESC_MAX = 3800;

	private final Gson gson;
	private final Client client;
	private final TerpinheimerConfig config;

	@Inject
	WebhookMessageBuilder(Client client, TerpinheimerConfig config, Gson gson)
	{
		this.client = client;
		this.config = config;
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

	private static JsonObject field(String name, String value, boolean inline)
	{
		JsonObject f = new JsonObject();
		f.addProperty("name", name);
		f.addProperty("value", value);
		f.addProperty("inline", inline);
		return f;
	}

	public JsonObject deathDinkStyleEmbed(String authorName)
	{
		return dinkChromeEmbed(0xE74C3C, authorName, "Death", "Your character has died.", null, null);
	}

	public JsonObject clueRewardDinkStyleEmbed(
		String authorName,
		String description,
		String thumbnailUrlOrNull,
		String tierDisplay,
		String completedDisplay,
		String totalValueDisplay)
	{
		JsonArray fields = new JsonArray();
		fields.add(field("Tier", tierDisplay, true));
		fields.add(field("Completed", completedDisplay, true));
		fields.add(field("Total Value", totalValueDisplay, true));
		return dinkChromeEmbed(0x9B59B6, authorName, "Clue Reward", description, thumbnailUrlOrNull, fields);
	}

	public JsonObject petDinkStyleEmbed(String authorName, String description)
	{
		return dinkChromeEmbed(0xF1C40F, authorName, "Pet", description, null, null);
	}

	public JsonObject collectionLogDinkStyleEmbed(String authorName, String description)
	{
		return dinkChromeEmbed(0xE67E22, authorName, "Collection log", description, null, null);
	}

	/**
	 * Dink-style level embed: pink accent, author = player name, title "Level Up", skill thumbnail.
	 * Footer is world-only so the name is not duplicated under the author line.
	 */
	public JsonObject levelUpDinkStyleEmbed(String authorName, String description, String skillIconUrlOrNull)
	{
		JsonObject embed = new JsonObject();
		embed.addProperty("color", 0xEB459E);
		JsonObject author = new JsonObject();
		author.addProperty("name", authorName);
		embed.add("author", author);
		embed.addProperty("title", "Level Up");
		embed.addProperty("description", truncate(description, DESC_MAX));
		if (skillIconUrlOrNull != null && !skillIconUrlOrNull.isEmpty())
		{
			JsonObject th = new JsonObject();
			th.addProperty("url", skillIconUrlOrNull);
			embed.add("thumbnail", th);
		}
		addTerpinheimerFooter(embed);
		return embed;
	}

	/** Dark-red Dink-style loot: author, title, wiki-style body, stat row, branded footer. */
	public JsonObject lootDropDinkStyleEmbed(
		String authorName,
		String title,
		String description,
		String thumbnailUrlOrNull,
		String killCountDisplay,
		String totalValueDisplay,
		String rarityDisplay)
	{
		JsonArray fields = new JsonArray();
		fields.add(field("Kill Count", killCountDisplay, true));
		fields.add(field("Total Value", totalValueDisplay, true));
		fields.add(field("Item Rarity", rarityDisplay, true));
		return dinkChromeEmbed(0xA93226, authorName, title, description, thumbnailUrlOrNull, fields);
	}

	/** Shared chrome: colored bar, author, title, optional thumbnail & fields, Terpinheimer footer. */
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

	public JsonObject combatLevelDescriptionEmbed(String description)
	{
		JsonObject embed = baseEmbed(0x3498DB);
		embed.addProperty("title", "Combat level");
		embed.addProperty("description", truncate(description, DESC_MAX));
		addMetaFields(embed);
		return embed;
	}

	public JsonObject simpleEmbed(String title, String description)
	{
		JsonObject embed = baseEmbed(EMBED_COLOR);
		embed.addProperty("title", title);
		embed.addProperty("description", truncate(Text.removeTags(description), DESC_MAX));
		addMetaFields(embed);
		return embed;
	}

	/** Template-filled body; optional HTTPS thumbnail URL for Discord. */
	public JsonObject templateBodyEmbed(String title, String description, String thumbnailUrlOrNull)
	{
		JsonObject embed = baseEmbed(EMBED_COLOR);
		embed.addProperty("title", title);
		embed.addProperty("description", truncate(description, DESC_MAX));
		if (thumbnailUrlOrNull != null && !thumbnailUrlOrNull.isEmpty())
		{
			JsonObject th = new JsonObject();
			th.addProperty("url", thumbnailUrlOrNull);
			embed.add("thumbnail", th);
		}
		addMetaFields(embed);
		return embed;
	}

	private JsonObject baseEmbed(int color)
	{
		JsonObject embed = new JsonObject();
		embed.addProperty("color", color);
		return embed;
	}

	private void addMetaFields(JsonObject embed)
	{
		List<String> footerParts = new ArrayList<>();
		if (config.includePlayerName() && client.getLocalPlayer() != null)
		{
			footerParts.add(Text.removeTags(client.getLocalPlayer().getName()));
		}
		if (!footerParts.isEmpty())
		{
			JsonObject footer = new JsonObject();
			footer.addProperty("text", String.join(" · ", footerParts));
			embed.add("footer", footer);
		}
	}

	private void addTerpinheimerFooter(JsonObject embed)
	{
		DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
		String ts = fmt.format(LocalDateTime.now());
		StringBuilder sb = new StringBuilder("Powered by Terpinheimer • ").append(ts);
		JsonObject footer = new JsonObject();
		footer.addProperty("text", truncate(sb.toString(), 2048));
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
