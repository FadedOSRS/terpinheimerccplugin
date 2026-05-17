package com.terpinheimer.site;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared filters so opening the collection log UI does not match broad "collection log" chat. */
public final class CollectionLogChatFilters
{
	private static final Pattern ITEM_ADDED =
		Pattern.compile("(?i)added (?:an? |the )?(.+?) to your collection");

	private CollectionLogChatFilters()
	{
	}

	public static boolean isUnlockNotification(String plain)
	{
		if (plain == null || plain.isEmpty())
		{
			return false;
		}
		String low = plain.toLowerCase(Locale.ROOT);
		return low.contains("new item added to your collection log")
			|| low.contains("added to your collection log")
			|| ITEM_ADDED.matcher(plain).find();
	}
}
