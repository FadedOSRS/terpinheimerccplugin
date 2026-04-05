package com.terpinheimer.discord;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.util.Text;

final class LevelNotificationFormatter
{
	private LevelNotificationFormatter()
	{
	}

	static String formatSkill(
		String template,
		Client client,
		Skill skill,
		int realLevel,
		int virtualLevel,
		int xp)
	{
		String name = playerName(client);
		return template
			.replace("%USERNAME%", name)
			.replace("%SKILL%", skill.getName())
			.replace("%LEVEL%", Integer.toString(realLevel))
			.replace("%VLEVEL%", Integer.toString(virtualLevel))
			.replace("%XP%", String.format("%,d", xp))
			.replace("\\n", "\n");
	}

	static String formatCombat(String template, Client client, int combatLevel)
	{
		String name = playerName(client);
		String c = Integer.toString(combatLevel);
		return template
			.replace("%USERNAME%", name)
			.replace("%COMBATLEVEL%", c)
			.replace("%COMBAT%", c)
			.replace("\\n", "\n");
	}

	private static String playerName(Client client)
	{
		if (client.getLocalPlayer() == null)
		{
			return "Player";
		}
		return Text.removeTags(client.getLocalPlayer().getName());
	}
}
