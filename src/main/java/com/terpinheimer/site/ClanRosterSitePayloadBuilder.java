package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.util.Text;

/**
 * JSON body for {@code POST} to the clan website roster / rank-ups endpoint.
 * <p>
 * Schema {@code schemaVersion} 1:
 * <ul>
 *   <li>{@code clanName}, {@code members[]} — {@code name}, {@code standardizedName}, {@code rank}
 *       (display rank: custom clan title when set, otherwise a readable Jagex rank name),
 *       {@code rankLevel} (Jagex rank index), {@code rankTitle} (same as {@code rank} when the clan assigns a title),
 *       {@code joinDate} (ISO-8601 date when known)</li>
 *   <li>{@code leftMembers[]} — optional; members present on the prior snapshot but missing now:
 *       {@code name}, {@code standardizedName}, {@code leftAtEpochMs}, plus {@code lastRank},
 *       {@code lastRankLevel} (when present), {@code lastRankTitle}, {@code lastJoinDate} from the last seen roster row</li>
 * </ul>
 */
@Singleton
public class ClanRosterSitePayloadBuilder
{
	private final Gson gson;
	private final TerpinheimerConfig config;
	private final ClanRosterSnapshotTracker rosterSnapshotTracker;

	@Inject
	ClanRosterSitePayloadBuilder(Gson gson, TerpinheimerConfig config, ClanRosterSnapshotTracker rosterSnapshotTracker)
	{
		this.gson = gson;
		this.config = config;
		this.rosterSnapshotTracker = rosterSnapshotTracker;
	}

	/**
	 * @return JSON or {@code null} when not in a clan or settings are unavailable
	 */
	public String buildJson(Client client)
	{
		ClanSettings settings = client.getClanSettings();
		if (settings == null)
		{
			return null;
		}
		String clanName = settings.getName();
		if (clanName == null)
		{
			clanName = "";
		}

		long nowMs = System.currentTimeMillis();
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", 1);
		addSyncTokenFields(root);
		root.addProperty("syncedAtEpochMs", nowMs);
		String rl = RuneLiteProperties.getVersion();
		root.addProperty("runeliteVersion", rl != null ? rl : "unknown");
		root.addProperty("pluginVersion", "2.0.1");
		root.addProperty("clanName", clanName);

		if (client.getLocalPlayer() != null)
		{
			String uploader = Text.removeTags(client.getLocalPlayer().getName());
			if (uploader != null)
			{
				uploader = uploader.trim().replace('\u00A0', ' ');
				root.addProperty("syncedByDisplayName", uploader);
			}
		}

		JsonArray members = new JsonArray();
		List<?> list = settings.getMembers();
		if (list != null)
		{
			for (Object o : list)
			{
				if (!(o instanceof ClanMember))
				{
					continue;
				}
				ClanMember m = (ClanMember) o;
				JsonObject row = memberRow(settings, m);
				if (row != null)
				{
					members.add(row);
				}
			}
		}
		root.add("members", members);

		boolean stillInClan = client.getLocalPlayer() != null && client.getLocalPlayer().isClanMember();
		JsonArray leftMembers = rosterSnapshotTracker.takeLeftMemberRows(clanName, members, stillInClan, nowMs);
		if (leftMembers.size() > 0)
		{
			root.add("leftMembers", leftMembers);
		}

		return gson.toJson(root);
	}

	private JsonObject memberRow(ClanSettings settings, ClanMember m)
	{
		String name = m.getName();
		if (name == null)
		{
			return null;
		}
		name = Text.removeTags(name).trim().replace('\u00A0', ' ');
		if (name.isEmpty())
		{
			return null;
		}
		JsonObject row = new JsonObject();
		row.addProperty("name", name);
		String std = Text.standardize(name);
		row.addProperty("standardizedName", std);

		ClanRank rank = m.getRank();
		if (rank != null)
		{
			row.addProperty("rankLevel", rank.getRank());
			ClanTitle title = settings.titleForRank(rank);
			String rankDisplay;
			if (title != null && title.getName() != null && !title.getName().isEmpty())
			{
				rankDisplay = title.getName();
				row.addProperty("rankTitle", rankDisplay);
			}
			else
			{
				rankDisplay = readableJagexRankName(rank);
			}
			row.addProperty("rank", rankDisplay);
		}

		if (m.getJoinDate() != null)
		{
			row.addProperty("joinDate", m.getJoinDate().toString());
		}

		return row;
	}

	/**
	 * When the clan has not set a custom title for this rank slot, use a readable name for built-in
	 * {@link ClanRank} constants; otherwise a neutral slot label (not {@link ClanRank#toString()} which is
	 * {@code Rank N}).
	 */
	private static String readableJagexRankName(ClanRank rank)
	{
		if (rank.equals(ClanRank.OWNER))
		{
			return "Owner";
		}
		if (rank.equals(ClanRank.DEPUTY_OWNER))
		{
			return "Deputy Owner";
		}
		if (rank.equals(ClanRank.ADMINISTRATOR))
		{
			return "Administrator";
		}
		if (rank.equals(ClanRank.GUEST))
		{
			return "Guest";
		}
		if (rank.equals(ClanRank.JMOD))
		{
			return "Jagex Moderator";
		}
		return "Clan rank slot " + rank.getRank();
	}

	private void addSyncTokenFields(JsonObject root)
	{
		String st = config.clanRosterSyncApiSecret();
		if (st == null || st.trim().isEmpty())
		{
			return;
		}
		String token = st.trim();
		if (token.regionMatches(true, 0, "Bearer ", 0, 7))
		{
			token = token.substring(7).trim();
		}
		if (token.isEmpty())
		{
			return;
		}
		root.addProperty("syncToken", token);
		root.addProperty("sync_token", token);
	}
}
