package com.terpinheimer.discord;

import java.util.Collection;
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

	static boolean sourceDenied(String sourceLabel, Set<String> denySources)
	{
		if (denySources.isEmpty())
		{
			return false;
		}
		String std = Text.standardize(sourceLabel);
		for (String d : denySources)
		{
			if (std.contains(d) || d.contains(std))
			{
				return true;
			}
		}
		return false;
	}

	static boolean lootViolatesItemFilters(Collection<String> itemNamesStd, Set<String> allow, Set<String> deny)
	{
		for (String n : itemNamesStd)
		{
			for (String d : deny)
			{
				if (n.contains(d) || n.equals(d))
				{
					return true;
				}
			}
		}
		if (!allow.isEmpty())
		{
			boolean any = false;
			outer:
			for (String n : itemNamesStd)
			{
				for (String a : allow)
				{
					if (n.contains(a) || n.equals(a))
					{
						any = true;
						break outer;
					}
				}
			}
			if (!any)
			{
				return true;
			}
		}
		return false;
	}
}
