package com.terpinheimer.discord;

import net.runelite.api.Skill;

/**
 * OSRS Wiki skill icon URLs for Discord embed thumbnails (top-right).
 */
final class SkillWikiIcons
{
	private static final String BASE = "https://oldschool.runescape.wiki/images/";

	private SkillWikiIcons()
	{
	}

	static String iconUrl(Skill skill)
	{
		if (skill == null || skill == Skill.OVERALL)
		{
			return null;
		}
		String file = skill.getName().replace(' ', '_') + "_icon.png";
		return BASE + file;
	}
}
