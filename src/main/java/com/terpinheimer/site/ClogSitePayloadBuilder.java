package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import com.terpinheimer.site.TerpinheimerRemoteConfigService;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON body for {@code POST} to the clan website collection-log sync endpoint.
 * <p>
 * Schema v2 (RuneProfile-aligned): primary progress is {@code items} map (item id string → quantity).
 * VarPlayers {@code clogLogged}/{@code clogTotal} and optional varbit snapshot are supplementary.
 */
@Singleton
public class ClogSitePayloadBuilder
{
	private static final Logger log = LoggerFactory.getLogger(ClogSitePayloadBuilder.class);
	private static final String CONFIG_GROUP = "terpinheimer";

	private final Gson gson;
	private final TerpinheimerConfig config;
	private final TerpinheimerRemoteConfigService remoteConfigService;
	private final ConfigManager configManager;
	private final CollectionLogVarbitSnapshot collectionLogVarbitSnapshot;
	private final CollectionLogItemStore collectionLogItemStore;
	private final ClogAccountProgressSnapshot clogAccountProgressSnapshot;

	@Inject
	ClogSitePayloadBuilder(
		Gson gson,
		TerpinheimerConfig config,
		TerpinheimerRemoteConfigService remoteConfigService,
		ConfigManager configManager,
		CollectionLogVarbitSnapshot collectionLogVarbitSnapshot,
		CollectionLogItemStore collectionLogItemStore,
		ClogAccountProgressSnapshot clogAccountProgressSnapshot)
	{
		this.gson = gson;
		this.config = config;
		this.remoteConfigService = remoteConfigService;
		this.configManager = configManager;
		this.collectionLogVarbitSnapshot = collectionLogVarbitSnapshot;
		this.collectionLogItemStore = collectionLogItemStore;
		this.clogAccountProgressSnapshot = clogAccountProgressSnapshot;
	}

	public String buildJson(Client client, List<ClogChronicleTracker.Line> chronicle)
	{
		return buildJson(client, chronicle, "manual-sync");
	}

	public String buildJson(Client client, List<ClogChronicleTracker.Line> chronicle, String eventSource)
	{
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", 2);
		root.addProperty("clogDataSource", "script-items");
		addSyncTokenFields(root);
		root.addProperty("syncedAtEpochMs", System.currentTimeMillis());
		String rl = RuneLiteProperties.getVersion();
		root.addProperty("runeliteVersion", rl != null ? rl : "unknown");
		root.addProperty("pluginVersion", "2.1.3");
		if (eventSource != null && !eventSource.isEmpty())
		{
			root.addProperty("eventSource", eventSource);
		}

		long accountHash = client.getAccountHash();
		if (accountHash != 0L)
		{
			root.addProperty("accountHash", Long.toString(accountHash));
		}

		addRuneScapeNameFields(root, client);

		root.addProperty("clogLogged", client.getVarpValue(VarPlayer.CLOG_LOGGED));
		root.addProperty("clogTotal", client.getVarpValue(VarPlayer.CLOG_TOTAL));

		JsonArray chronicleArr = new JsonArray();
		if (chronicle != null)
		{
			for (ClogChronicleTracker.Line line : chronicle)
			{
				JsonObject o = new JsonObject();
				o.addProperty("t", line.getEpochMs());
				o.addProperty("text", line.getText());
				chronicleArr.add(o);
			}
		}
		root.add("chronicle", chronicleArr);

		collectionLogItemStore.persistNow();
		collectionLogItemStore.writeItemsJson(root);

		JsonObject varbits = new JsonObject();
		collectionLogVarbitSnapshot.snapshot(client, varbits);
		root.add("collectionVarbits", varbits);

		clogAccountProgressSnapshot.writeTo(root, client);
		flattenAccountProgressToRoot(root);

		log.info("Terpinheimer clog sync payload: displayName={}, items={}, varbits={}, quests={}",
			root.get("displayName"), root.has("items") ? "yes" : "no", varbits.size(),
			root.has("quests") ? root.getAsJsonObject("quests").size() : 0);

		return gson.toJson(root);
	}

	/** Copy {@code accountProgress} quest/music fields to the root for dev-server ingest (same as musicVarps). */
	private static void flattenAccountProgressToRoot(JsonObject root)
	{
		if (root == null || !root.has("accountProgress"))
		{
			return;
		}
		com.google.gson.JsonElement apEl = root.get("accountProgress");
		if (apEl == null || !apEl.isJsonObject())
		{
			return;
		}
		JsonObject ap = apEl.getAsJsonObject();
		copyJsonMember(ap, root, "quests");
		copyJsonMember(ap, root, "questsFinished");
		copyJsonMember(ap, root, "questPoints");
		copyJsonMember(ap, root, "questsFinishedCount");
		copyJsonMember(ap, root, "achievementDiaries");
		copyJsonMember(ap, root, "achievementDiaryTiers");
		copyJsonMember(ap, root, "musicUnlocked");
		copyJsonMember(ap, root, "musicUnlockedCount");
	}

	private static void copyJsonMember(JsonObject from, JsonObject to, String key)
	{
		if (from.has(key) && !to.has(key))
		{
			to.add(key, from.get(key).deepCopy());
		}
	}

	private void addRuneScapeNameFields(JsonObject root, Client client)
	{
		String visible = runescapeDisplayNameForSite(client);
		String override = remoteConfigService.getClogRunescapeNameOverride();
		String displayName;
		if (override != null && !override.trim().isEmpty())
		{
			displayName = override.trim().replace('\u00A0', ' ');
		}
		else
		{
			displayName = visible;
		}
		root.addProperty("displayName", displayName);
		root.addProperty("osrsName", displayName);
		root.addProperty("runescapeName", displayName);
		root.addProperty("osrs_name", displayName);
		root.addProperty("runescape_name", displayName);
		String std = displayName.isEmpty() ? "" : Text.standardize(displayName);
		root.addProperty("standardizedDisplayName", std);
		root.addProperty("standardized_display_name", std);
		if ((override == null || override.trim().isEmpty()) && !visible.isEmpty() && !visible.equals(displayName))
		{
			root.addProperty("displayNameFormatted", visible);
		}
	}

	private static String runescapeDisplayNameForSite(Client client)
	{
		if (client.getLocalPlayer() == null)
		{
			return "";
		}
		String n = Text.removeTags(client.getLocalPlayer().getName());
		if (n == null)
		{
			return "";
		}
		return n.trim().replace('\u00A0', ' ');
	}

	private void addSyncTokenFields(JsonObject root)
	{
		String st = resolveClanSecret();
		if (st.isEmpty())
		{
			return;
		}
		root.addProperty("syncToken", st);
		root.addProperty("sync_token", st);
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
		if (st == null)
		{
			return "";
		}
		String token = st.trim();
		if (token.regionMatches(true, 0, "Bearer ", 0, 7))
		{
			token = token.substring(7).trim();
		}
		return token;
	}
}
