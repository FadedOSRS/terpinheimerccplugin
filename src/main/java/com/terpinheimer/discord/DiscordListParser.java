package com.terpinheimer.discord;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.client.util.Text;

final class DiscordListParser
{
	private DiscordListParser()
	{
	}

	static Set<String> parseLines(String raw)
	{
		if (raw == null || raw.isBlank())
		{
			return Collections.emptySet();
		}
		Set<String> out = new LinkedHashSet<>();
		for (String part : raw.split("[\\r\\n,]+"))
		{
			String t = Text.standardize(part.trim());
			if (!t.isEmpty())
			{
				out.add(t);
			}
		}
		return out;
	}
}
