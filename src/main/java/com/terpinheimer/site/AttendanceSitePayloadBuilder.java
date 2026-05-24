package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import com.terpinheimer.attendance.ClanAttendanceTracker;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * JSON body for {@code POST} of a clan event attendance report to the clan website.
 */
@Singleton
public class AttendanceSitePayloadBuilder
{
	private static final String CONFIG_GROUP = "terpinheimer";

	private final Gson gson;
	private final Client client;
	private final TerpinheimerConfig config;
	private final ConfigManager configManager;
	private final ClanAttendanceTracker clanAttendanceTracker;

	@Inject
	AttendanceSitePayloadBuilder(
		Gson gson,
		Client client,
		TerpinheimerConfig config,
		ConfigManager configManager,
		ClanAttendanceTracker clanAttendanceTracker)
	{
		this.gson = gson;
		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.clanAttendanceTracker = clanAttendanceTracker;
	}

	public String buildJson()
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return null;
		}

		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", 1);
		root.addProperty("pluginVersion", "2.1.0");
		root.addProperty("runeliteVersion", RuneLiteProperties.getVersion());
		root.addProperty("uploadedBy", Text.removeTags(client.getLocalPlayer().getName()));
		root.addProperty("uploadedAtEpochMs", System.currentTimeMillis());

		ClanSettings settings = client.getClanSettings();
		if (settings != null && settings.getName() != null)
		{
			root.addProperty("clanName", settings.getName());
		}

		clanAttendanceTracker.appendSitePayloadFields(root, client);
		addSyncTokenFields(root);
		return gson.toJson(root);
	}

	private void addSyncTokenFields(JsonObject root)
	{
		String token = resolveClanSecret();
		if (token.isEmpty())
		{
			return;
		}
		root.addProperty("syncToken", token);
		root.addProperty("sync_token", token);
	}

	private String resolveClanSecret()
	{
		String st = configManager.getConfiguration(CONFIG_GROUP, "clanSecret");
		if (st == null || st.trim().isEmpty())
		{
			st = config.clanSecret();
		}
		if (st == null || st.trim().isEmpty())
		{
			for (String legacyKey : new String[] {"clogSyncApiSecret", "clanRosterSyncApiSecret", "liveMapApiKey"})
			{
				String legacy = configManager.getConfiguration(CONFIG_GROUP, legacyKey);
				if (legacy != null && !legacy.trim().isEmpty())
				{
					st = legacy;
					break;
				}
			}
		}
		return st != null ? st.trim() : "";
	}
}
