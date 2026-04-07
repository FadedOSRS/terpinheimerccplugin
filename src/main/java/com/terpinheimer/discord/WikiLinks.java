package com.terpinheimer.discord;

/**
 * Old School RuneScape Wiki URLs for Discord markdown links.
 */
public final class WikiLinks
{
	private WikiLinks()
	{
	}

	static String itemPage(String itemName)
	{
		return pageUrl(itemName);
	}

	static String npcPage(String npcName)
	{
		return pageUrl(npcName);
	}

	private static String pageUrl(String name)
	{
		if (name == null || name.isEmpty())
		{
			return "https://oldschool.runescape.wiki/";
		}
		String slug = name.trim().replace(' ', '_');
		slug = slug.replace("'", "%27");
		return "https://oldschool.runescape.wiki/w/" + slug;
	}

	/** Escape characters that break Discord [label](url) markdown. */
	static String escapeLinkLabel(String label)
	{
		return label.replace("\\", "\\\\")
			.replace("]", "\\]")
			.replace("(", "\\(");
	}

	static String markdownLink(String label, String url)
	{
		return "[" + escapeLinkLabel(label) + "](" + url + ")";
	}

	public static String formatGpCompact(long gp)
	{
		if (gp >= 1_000_000L)
		{
			return String.format("%.1fM", gp / 1_000_000.0);
		}
		if (gp >= 10_000L)
		{
			return String.format("%.1fK", gp / 1_000.0);
		}
		if (gp >= 1_000L)
		{
			return String.format("%,d", gp);
		}
		return String.format("%,d", gp);
	}

	/** Wiki /images/ URL for a common item icon filename (best-effort; may 404 for some items). */
	static String itemIconPngUrl(String itemName)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return null;
		}
		String slug = itemName.replace(' ', '_').replaceAll("[^A-Za-z0-9_+\\-]", "");
		if (slug.isEmpty())
		{
			return null;
		}
		return "https://oldschool.runescape.wiki/images/" + slug + ".png";
	}
}
