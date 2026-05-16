package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.util.Text;

/**
 * JSON body for {@code POST} to the clan website collection-log sync endpoint.
 * <p>
 * Schema v2 (RuneProfile-aligned): primary progress is {@code items} map (item id string → quantity).
 * VarPlayers {@code clogLogged}/{@code clogTotal} and optional varbit snapshot are supplementary.
 */
@Singleton
public class ClogSitePayloadBuilder
{
	private final Gson gson;
	private final TerpinheimerConfig config;
	private final CollectionLogVarbitSnapshot collectionLogVarbitSnapshot;
	private final CollectionLogItemStore collectionLogItemStore;

	@Inject
	ClogSitePayloadBuilder(
		Gson gson,
		TerpinheimerConfig config,
		CollectionLogVarbitSnapshot collectionLogVarbitSnapshot,
		CollectionLogItemStore collectionLogItemStore)
	{
		this.gson = gson;
		this.config = config;
		this.collectionLogVarbitSnapshot = collectionLogVarbitSnapshot;
		this.collectionLogItemStore = collectionLogItemStore;
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
		root.addProperty("pluginVersion", "2.1.0");
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

		collectionLogItemStore.writeItemsJson(root);

		JsonObject varbits = new JsonObject();
		collectionLogVarbitSnapshot.snapshot(client, varbits);
		root.add("collectionVarbits", varbits);

		return gson.toJson(root);
	}

	private void addRuneScapeNameFields(JsonObject root, Client client)
	{
		String visible = runescapeDisplayNameForSite(client);
		String override = config.clogSyncRuneScapeNameOverride();
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
		String st = config.clogSyncApiSecret();
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
