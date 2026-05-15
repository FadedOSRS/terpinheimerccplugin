package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON body for {@code POST} to the clan website collection-log sync endpoint.
 * <p>
 * Schema {@code schemaVersion} 1:
 * <ul>
 *   <li>{@code displayName} — exact visible logged-in name (tags stripped, trimmed, NBSP → spaces), same
 *       spelling and capitalization as in-game and as **My profile → RuneScape name** on the site
 *       (not the Admin “Set RSN” field unless that is what you use on My profile). Optional
 *       {@code clogSyncRuneScapeNameOverride} replaces this when set.</li>
 *   <li>{@code standardizedDisplayName} / {@code standardized_display_name} — {@link Text#standardize(String)}
 *       of the visible in-game name so the server can match case-insensitively if it supports it.</li>
 *   <li>{@code displayNameFormatted} — omitted when it would duplicate {@code displayName}.</li>
 *   <li>{@code syncedAtEpochMs} — client clock when the snapshot was taken</li>
 *   <li>{@code clogLogged} / {@code clogTotal} — {@link VarPlayer#CLOG_LOGGED} / {@link VarPlayer#CLOG_TOTAL}</li>
 *   <li>{@code chronicle} — array of {@code { "t": epochMs, "text": "..." }} from recent collection log chat lines</li>
 *   <li>{@code collectionVarbits} — sparse map {@code varbitId} (string) → value for non-zero
 *       {@code net.runelite.api.gameval.VarbitID} fields whose names start with {@code COLLECTION_},
 *       excluding UI varbits {@code COLLECTION_LAST_TAB} and {@code COLLECTION_LAST_CATEGORY}.
 *       The site can derive collection slots, pet vault, etc. from this map plus its catalog.</li>
 *   <li>{@code itemIds} — optional deduped OSRS item ids for obtained slots: {@code COLLECTION_ITEM_*} varbits
 *       resolved through {@code net.runelite.api.gameval.ItemID}, plus any entries from
 *       {@code /collection-varbit-to-item-id.json} (invert of the site's {@code collection-log-varbit-map.json}).</li>
 *   <li>{@code syncToken} / {@code sync_token} — when the shared secret is set in plugin config: RuneLite
 *       secret (same as {@code RUNELITE_CLOG_SYNC_SECRET}), or JWT only if you pasted {@code Bearer eyJ…}
 *       (Bearer prefix stripped for the JSON fields). Placed early in the object for strict parsers.</li>
 *   <li>{@code runeliteVersion}, {@code pluginVersion} — diagnostics</li>
 * </ul>
 */
@Singleton
public class ClogSitePayloadBuilder
{
	private static final Logger log = LoggerFactory.getLogger(ClogSitePayloadBuilder.class);
	private static final String VARBIT_ID_CLASS = "net.runelite.api.gameval.VarbitID";

	private final Gson gson;
	private final TerpinheimerConfig config;
	private final CollectionLogItemIdsSupport collectionLogItemIdsSupport;

	@Inject
	ClogSitePayloadBuilder(Gson gson, TerpinheimerConfig config, CollectionLogItemIdsSupport collectionLogItemIdsSupport)
	{
		this.gson = gson;
		this.config = config;
		this.collectionLogItemIdsSupport = collectionLogItemIdsSupport;
	}

	public String buildJson(Client client, List<ClogChronicleTracker.Line> chronicle)
	{
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", 1);
		addSyncTokenFields(root);
		root.addProperty("syncedAtEpochMs", System.currentTimeMillis());
		String rl = RuneLiteProperties.getVersion();
		root.addProperty("runeliteVersion", rl != null ? rl : "unknown");
		root.addProperty("pluginVersion", "2.0.1");

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

		JsonObject varbits = new JsonObject();
		Set<Integer> itemIdSet = new LinkedHashSet<>();
		appendCollectionVarbits(client, varbits, itemIdSet);
		root.add("collectionVarbits", varbits);
		collectionLogItemIdsSupport.addItemIdsFromVarbitResource(varbits, itemIdSet);
		if (!itemIdSet.isEmpty())
		{
			JsonArray itemIds = new JsonArray();
			for (int id : itemIdSet)
			{
				itemIds.add(id);
			}
			root.add("itemIds", itemIds);
		}

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
			// Exact visible name (case-sensitive) so it matches My profile when stored as shown in-game.
			// Do not use Text.standardize here: e.g. "FadedOSRS" must not become "fadedosrs".
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

	/**
	 * Visible RuneScape name for the logged-in account — tags stripped, trimmed, NBSP → spaces.
	 */
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
		n = n.trim().replace('\u00A0', ' ');
		return n;
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

	private void appendCollectionVarbits(Client client, JsonObject out, Set<Integer> itemIdSet)
	{
		try
		{
			Class<?> vb = Class.forName(VARBIT_ID_CLASS);
			for (Field f : vb.getFields())
			{
				if (f.getType() != int.class || !Modifier.isStatic(f.getModifiers()))
				{
					continue;
				}
				String n = f.getName();
				if (!n.startsWith("COLLECTION_"))
				{
					continue;
				}
				if ("COLLECTION_LAST_TAB".equals(n) || "COLLECTION_LAST_CATEGORY".equals(n))
				{
					continue;
				}
				int varbitId = f.getInt(null);
				int v = client.getVarbitValue(varbitId);
				if (v != 0)
				{
					out.addProperty(Integer.toString(varbitId), v);
					collectionLogItemIdsSupport.tryAddItemIdFromCollectionItemVarbit(n, v, itemIdSet);
				}
			}
		}
		catch (ReflectiveOperationException | LinkageError e)
		{
			log.debug("Terpinheimer: collection varbit snapshot skipped: {}", e.toString());
		}
	}
}
