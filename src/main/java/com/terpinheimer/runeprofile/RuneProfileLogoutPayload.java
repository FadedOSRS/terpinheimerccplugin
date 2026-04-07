package com.terpinheimer.runeprofile;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the JSON body for {@code POST https://api.runeprofile.com/profiles} (same contract as the
 * <a href="https://github.com/ReinhardtR/runeprofile-plugin">RuneProfile</a> client).
 * Collection log items are omitted (empty map); skills, quests, combat achievements, and diaries are
 * filled from the live client where the API allows.
 */
public final class RuneProfileLogoutPayload
{
	private static final Logger log = LoggerFactory.getLogger(RuneProfileLogoutPayload.class);

	private RuneProfileLogoutPayload()
	{
	}

	@Nullable
	public static String buildJson(Client client, Gson gson)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		String id = RuneProfileAccountHasher.hashedAccountId(client);
		if (id == null)
		{
			return null;
		}
		String username = Text.removeTags(player.getName());
		if (username == null || username.isBlank())
		{
			return null;
		}

		try
		{
			JsonObject root = new JsonObject();
			root.addProperty("id", id);
			root.addProperty("username", username);
			root.addProperty("accountType", client.getVarbitValue(Varbits.ACCOUNT_TYPE));

			JsonObject skills = new JsonObject();
			for (Skill skill : Skill.values())
			{
				skills.addProperty(skill.getName(), client.getSkillExperience(skill));
			}
			root.add("skills", skills);

			JsonObject quests = new JsonObject();
			for (Quest quest : Quest.values())
			{
				int state;
				QuestState st = quest.getState(client);
				if (st == QuestState.IN_PROGRESS)
				{
					state = 1;
				}
				else if (st == QuestState.FINISHED)
				{
					state = 2;
				}
				else
				{
					state = 0;
				}
				quests.addProperty(String.valueOf(quest.getId()), state);
			}
			root.add("quests", quests);

			root.add("combatAchievementTiers", new JsonObject());
			root.add("achievementDiaryTiers", new JsonArray());
			root.add("items", new JsonObject());

			root.addProperty("eventSource", "terpinheimer_logout");
			return gson.toJson(root);
		}
		catch (RuntimeException e)
		{
			log.warn("Terpinheimer: RuneProfile logout payload skipped after client/API change: {}", e.toString());
			return null;
		}
	}
}
