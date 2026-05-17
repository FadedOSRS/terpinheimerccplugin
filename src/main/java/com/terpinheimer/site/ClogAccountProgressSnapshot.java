package com.terpinheimer.site;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quest, achievement diary, and music progress for collection-log site sync.
 * Nested under {@code accountProgress} so v2 clog item/varbit saves stay compatible; {@code musicVarps}
 * is also copied to the root for hosts that only read bitfields there.
 */
@Singleton
public final class ClogAccountProgressSnapshot
{
	private static final Logger log = LoggerFactory.getLogger(ClogAccountProgressSnapshot.class);

	private static final int[] MUSIC_VAR_PLAYER_IDS = {
		VarPlayerID.MUSICMULTI_1,
		VarPlayerID.MUSICMULTI_2,
		VarPlayerID.MUSICMULTI_3,
		VarPlayerID.MUSICMULTI_4,
		VarPlayerID.MUSICMULTI_5,
		VarPlayerID.MUSICMULTI_6,
		VarPlayerID.MUSICMULTI_7,
		VarPlayerID.MUSICMULTI_8,
		VarPlayerID.MUSICMULTI_9,
		VarPlayerID.MUSICMULTI_10,
		VarPlayerID.MUSICMULTI_11,
		VarPlayerID.MUSICMULTI_12,
		VarPlayerID.MUSICMULTI_13,
		VarPlayerID.MUSICMULTI_14,
		VarPlayerID.MUSICMULTI_15,
		VarPlayerID.MUSICMULTI_16,
		VarPlayerID.MUSICMULTI_17,
		VarPlayerID.MUSICMULTI_18,
		VarPlayerID.MUSICMULTI_19,
		VarPlayerID.MUSICMULTI_20,
		VarPlayerID.MUSICMULTI_21,
		VarPlayerID.MUSICMULTI_22,
		VarPlayerID.MUSICMULTI_23,
		VarPlayerID.MUSICMULTI_24,
		VarPlayerID.MUSICMULTI_25,
		VarPlayerID.MUSICMULTI_26,
		VarPlayerID.MUSICMULTI_27,
	};

	@Inject
	ClogAccountProgressSnapshot()
	{
	}

	void writeTo(JsonObject root, Client client)
	{
		if (root == null || client == null)
		{
			return;
		}
		JsonObject progress = new JsonObject();
		progress.addProperty("questPoints", client.getVarpValue(VarPlayer.QUEST_POINTS));
		writeQuests(progress, client);
		writeAchievementDiaryTiers(progress, client);
		writeMusic(progress, client);
		root.add("accountProgress", progress);
		// Site fallback: decode unlocks from bitfields when row lists are not stored.
		root.add("musicVarps", progress.getAsJsonObject("musicVarps").deepCopy());
		root.addProperty("musicUnlockedCount", progress.get("musicUnlockedCount").getAsInt());
	}

	private static void writeQuests(JsonObject target, Client client)
	{
		JsonObject quests = new JsonObject();
		JsonArray finished = new JsonArray();
		int finishedCount = 0;
		for (Quest quest : Quest.values())
		{
			QuestState state = quest.getState(client);
			int code = questStateCode(state);
			quests.addProperty(Integer.toString(quest.getId()), code);
			if (state == QuestState.FINISHED)
			{
				finished.add(quest.getId());
				finishedCount++;
			}
		}
		target.add("quests", quests);
		target.add("questsFinished", finished);
		target.addProperty("questsFinishedCount", finishedCount);
	}

	/** RuneProfile-aligned: 0 = not started, 1 = in progress, 2 = finished. */
	private static int questStateCode(QuestState state)
	{
		if (state == QuestState.FINISHED)
		{
			return 2;
		}
		if (state == QuestState.IN_PROGRESS)
		{
			return 1;
		}
		return 0;
	}

	private static void writeAchievementDiaryTiers(JsonObject target, Client client)
	{
		JsonArray tiers = new JsonArray();
		for (DiaryArea area : DiaryArea.values())
		{
			int[] completedCounts = area.getTiersCompletedCount(client);
			for (int tierIndex = 0; tierIndex < completedCounts.length; tierIndex++)
			{
				JsonObject tier = new JsonObject();
				tier.addProperty("areaId", area.getId());
				tier.addProperty("tierIndex", tierIndex);
				int count = completedCounts[tierIndex];
				if (count < 0)
				{
					count = 0;
				}
				tier.addProperty("completedCount", count);
				tiers.add(tier);
			}
		}
		target.add("achievementDiaryTiers", tiers);
	}

	private static void writeMusic(JsonObject target, Client client)
	{
		JsonObject musicVarps = new JsonObject();
		int varpBitCount = 0;
		for (int i = 0; i < MUSIC_VAR_PLAYER_IDS.length; i++)
		{
			int value = client.getVarpValue(MUSIC_VAR_PLAYER_IDS[i]);
			musicVarps.addProperty("musicMulti" + (i + 1), value);
			varpBitCount += Integer.bitCount(value);
		}
		target.add("musicVarps", musicVarps);
		target.addProperty("musicUnlockedCount", varpBitCount);

		JsonArray unlocked = new JsonArray();
		try
		{
			List<Integer> rows = client.getDBTableRows(DBTableID.Music.ID);
			if (rows != null)
			{
				for (Integer row : rows)
				{
					if (row == null || row <= 0)
					{
						continue;
					}
					if (isMusicRowUnlocked(client, row))
					{
						unlocked.add(row);
					}
				}
			}
		}
		catch (Exception e)
		{
			log.debug("Terpinheimer: music row scan skipped: {}", e.toString());
		}
		if (unlocked.size() > 0)
		{
			target.add("musicUnlocked", unlocked);
		}
	}

	private static boolean isMusicRowUnlocked(Client client, int rowId)
	{
		if (isAutomaticMusicUnlock(client, rowId))
		{
			return true;
		}
		Object[] variable = client.getDBTableField(rowId, DBTableID.Music.COL_VARIABLE, 0);
		if (variable == null || variable.length == 0)
		{
			return false;
		}
		if (variable.length >= 2 && variable[0] instanceof Number && variable[1] instanceof Number)
		{
			int first = ((Number) variable[0]).intValue();
			int bit = ((Number) variable[1]).intValue();
			int varpId = resolveMusicVarpId(first);
			if (varpId > 0 && bit >= 0 && bit < 32)
			{
				return (client.getVarpValue(varpId) & (1 << bit)) != 0;
			}
		}
		if (variable[0] instanceof Number)
		{
			int packed = ((Number) variable[0]).intValue();
			if (packed <= 0)
			{
				return false;
			}
			int varbitId = packed >> 16;
			int expected = packed & 0xFFFF;
			if (varbitId > 0)
			{
				return client.getVarbitValue(varbitId) == expected;
			}
		}
		return false;
	}

	private static int resolveMusicVarpId(int musicMultiIndexOrVarpId)
	{
		if (musicMultiIndexOrVarpId >= 1 && musicMultiIndexOrVarpId <= MUSIC_VAR_PLAYER_IDS.length)
		{
			return MUSIC_VAR_PLAYER_IDS[musicMultiIndexOrVarpId - 1];
		}
		for (int varpId : MUSIC_VAR_PLAYER_IDS)
		{
			if (varpId == musicMultiIndexOrVarpId)
			{
				return varpId;
			}
		}
		return -1;
	}

	private static boolean isAutomaticMusicUnlock(Client client, int rowId)
	{
		Object[] auto = client.getDBTableField(rowId, DBTableID.Music.COL_AUTOMATIC_UNLOCK, 0);
		return auto != null && auto.length > 0 && auto[0] instanceof Number && ((Number) auto[0]).intValue() != 0;
	}

	private enum DiaryArea
	{
		KARAMJA(0),
		ARDOUGNE(1),
		FALADOR(2),
		FREMENNIK(3),
		KANDARIN(4),
		DESERT(5),
		LUMBRIDGE(6),
		MORYTANIA(7),
		VARROCK(8),
		WILDERNESS(9),
		WESTERN_PROVINCES(10),
		KOUREND(11);

		private final int id;

		DiaryArea(int id)
		{
			this.id = id;
		}

		int getId()
		{
			return id;
		}

		int[] getTiersCompletedCount(Client client)
		{
			client.runScript(2200, id);
			int[] stack = client.getIntStack();
			if (stack == null || stack.length < 10)
			{
				return new int[] {0, 0, 0, 0};
			}
			return new int[] {stack[0], stack[3], stack[6], stack[9]};
		}
	}
}
