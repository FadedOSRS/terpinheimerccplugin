package com.terpinheimer.site;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Tracks the last roster POST snapshot per clan name so the next payload can list members who
 * disappeared (leave date = {@code leftAtEpochMs} on the server when it merges this array).
 */
@Singleton
public class ClanRosterSnapshotTracker
{
	/** Jagex clan name from {@code ClanSettings#getName()} for the last stored snapshot. */
	private String snapshotClanName = "";
	private final Map<String, JsonObject> lastMembersByStd = new HashMap<>();

	@Inject
	ClanRosterSnapshotTracker()
	{
	}

	public void reset()
	{
		snapshotClanName = "";
		lastMembersByStd.clear();
	}

	/**
	 * Compares {@code currentMemberRows} to the last snapshot, updates the stored snapshot, and
	 * returns rows for members who are no longer present.
	 *
	 * @param playerStillInClan when the current member list is empty but this is true, transient
	 *                          load is assumed and no mass-leave rows are emitted
	 */
	public JsonArray takeLeftMemberRows(String clanName, JsonArray currentMemberRows, boolean playerStillInClan, long nowMs)
	{
		if (clanName == null)
		{
			clanName = "";
		}
		if (!clanName.equals(snapshotClanName))
		{
			snapshotClanName = clanName;
			lastMembersByStd.clear();
		}

		Set<String> currentStd = new HashSet<>();
		Map<String, JsonObject> currentCopy = new HashMap<>();
		for (int i = 0; i < currentMemberRows.size(); i++)
		{
			JsonObject row = currentMemberRows.get(i).getAsJsonObject();
			if (!row.has("standardizedName"))
			{
				continue;
			}
			String std = row.get("standardizedName").getAsString();
			currentStd.add(std);
			currentCopy.put(std, copyMemberSnapshot(row));
		}

		JsonArray left = new JsonArray();
		if (!lastMembersByStd.isEmpty())
		{
			if (!currentStd.isEmpty())
			{
				for (Map.Entry<String, JsonObject> e : lastMembersByStd.entrySet())
				{
					if (!currentStd.contains(e.getKey()))
					{
						left.add(buildLeftRow(e.getValue(), nowMs));
					}
				}
			}
			else if (!playerStillInClan)
			{
				for (JsonObject prev : lastMembersByStd.values())
				{
					left.add(buildLeftRow(prev, nowMs));
				}
			}
		}

		lastMembersByStd.clear();
		lastMembersByStd.putAll(currentCopy);
		return left;
	}

	private static JsonObject copyMemberSnapshot(JsonObject row)
	{
		JsonObject c = new JsonObject();
		copyIfPresent(row, c, "name");
		copyIfPresent(row, c, "standardizedName");
		copyIfPresent(row, c, "rank");
		copyIfPresent(row, c, "rankLevel");
		copyIfPresent(row, c, "rankTitle");
		copyIfPresent(row, c, "joinDate");
		return c;
	}

	private static void copyIfPresent(JsonObject from, JsonObject to, String key)
	{
		if (from.has(key))
		{
			to.add(key, from.get(key));
		}
	}

	private static JsonObject buildLeftRow(JsonObject lastMember, long nowMs)
	{
		JsonObject o = new JsonObject();
		if (lastMember.has("name"))
		{
			o.add("name", lastMember.get("name"));
		}
		if (lastMember.has("standardizedName"))
		{
			o.add("standardizedName", lastMember.get("standardizedName"));
		}
		o.addProperty("leftAtEpochMs", nowMs);
		if (lastMember.has("rank"))
		{
			o.add("lastRank", lastMember.get("rank"));
		}
		if (lastMember.has("rankLevel"))
		{
			o.add("lastRankLevel", lastMember.get("rankLevel"));
		}
		if (lastMember.has("rankTitle"))
		{
			o.add("lastRankTitle", lastMember.get("rankTitle"));
		}
		if (lastMember.has("joinDate"))
		{
			o.add("lastJoinDate", lastMember.get("joinDate"));
		}
		return o;
	}
}
