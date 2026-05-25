package com.terpinheimer.clan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.client.util.Text;

/**
 * Parses comma-separated rank titles from remote plugin config and matches them to a player's
 * in-game display rank (case-insensitive, trimmed).
 */
public final class RankTitlePermissionList
{
	private RankTitlePermissionList()
	{
	}

	public static List<String> parse(String commaSeparated)
	{
		if (commaSeparated == null)
		{
			return Collections.emptyList();
		}
		String t = commaSeparated.trim();
		if (t.isEmpty())
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		for (String part : t.split("[,;]"))
		{
			String label = part.trim();
			if (!label.isEmpty())
			{
				out.add(label);
			}
		}
		return Collections.unmodifiableList(out);
	}

	public static List<String> parseJsonElement(JsonElement el)
	{
		if (el == null || el.isJsonNull())
		{
			return Collections.emptyList();
		}
		if (el.isJsonArray())
		{
			JsonArray arr = el.getAsJsonArray();
			List<String> out = new ArrayList<>();
			for (JsonElement item : arr)
			{
				if (item != null && item.isJsonPrimitive())
				{
					String label = item.getAsString();
					if (label != null && !label.trim().isEmpty())
					{
						out.add(label.trim());
					}
				}
			}
			return Collections.unmodifiableList(out);
		}
		if (el.isJsonPrimitive())
		{
			return parse(el.getAsString());
		}
		return Collections.emptyList();
	}

	/**
	 * @param playerDisplayRank from {@link ClanMemberRankDisplay#forMember}
	 * @param allowedTitles parsed allow-list from the website
	 */
	public static boolean matches(String playerDisplayRank, List<String> allowedTitles)
	{
		if (allowedTitles == null || allowedTitles.isEmpty() || playerDisplayRank == null)
		{
			return false;
		}
		String playerKey = Text.standardize(playerDisplayRank.trim());
		if (playerKey.isEmpty())
		{
			return false;
		}
		for (String allowed : allowedTitles)
		{
			if (allowed == null)
			{
				continue;
			}
			String allowedKey = Text.standardize(allowed.trim());
			if (!allowedKey.isEmpty() && allowedKey.equals(playerKey))
			{
				return true;
			}
		}
		return false;
	}

	/** Human-readable hint for permission dialogs (original spelling from config). */
	public static String formatForMessage(List<String> titles)
	{
		if (titles == null || titles.isEmpty())
		{
			return "(none configured)";
		}
		return String.join(", ", titles);
	}
}
