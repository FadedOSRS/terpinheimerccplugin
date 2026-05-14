package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@code itemIds} for clog sync: (1) {@code COLLECTION_ITEM_*} varbits → {@code ItemID} via reflection,
 * (2) optional {@code /collection-varbit-to-item-id.json} (varbit id string → item id) for keys present in
 * {@code collectionVarbits} — invert of the site's {@code collection-log-varbit-map.json}.
 */
@Singleton
public final class CollectionLogItemIdsSupport
{
	private static final Logger log = LoggerFactory.getLogger(CollectionLogItemIdsSupport.class);
	private static final String ITEM_ID_CLASS = "net.runelite.api.gameval.ItemID";
	private static final String VARBIT_TO_ITEM_RESOURCE = "/collection-varbit-to-item-id.json";
	private static final int COLLECTION_ITEM_PREFIX_LEN = "COLLECTION_ITEM_".length();

	private final Gson gson;
	private volatile Map<String, Integer> varbitKeyToItemId;

	@Inject
	CollectionLogItemIdsSupport(Gson gson)
	{
		this.gson = gson;
		this.varbitKeyToItemId = null;
	}

	/**
	 * For {@code COLLECTION_ITEM_FOO} with non-zero {@code value}, adds {@code ItemID.FOO} when that field exists.
	 */
	void tryAddItemIdFromCollectionItemVarbit(String varbitFieldName, int value, Set<Integer> out)
	{
		if (value == 0 || varbitFieldName == null || !varbitFieldName.startsWith("COLLECTION_ITEM_"))
		{
			return;
		}
		String suffix = varbitFieldName.substring(COLLECTION_ITEM_PREFIX_LEN);
		if (suffix.isEmpty())
		{
			return;
		}
		try
		{
			Class<?> itemId = Class.forName(ITEM_ID_CLASS);
			int item = itemId.getField(suffix).getInt(null);
			if (item > 0)
			{
				out.add(item);
			}
		}
		catch (ReflectiveOperationException | LinkageError ignored)
		{
			// No matching ItemID — site varbit map / resource JSON may still map this varbit id.
		}
	}

	/** For each non-zero varbit key in {@code varbits}, add mapped item id from the bundled resource when present. */
	void addItemIdsFromVarbitResource(JsonObject varbits, Set<Integer> out)
	{
		if (varbits == null || varbits.size() == 0)
		{
			return;
		}
		Map<String, Integer> map = varbitToItemMap();
		if (map.isEmpty())
		{
			return;
		}
		for (Map.Entry<String, JsonElement> e : varbits.entrySet())
		{
			JsonElement el = e.getValue();
			if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber())
			{
				continue;
			}
			if (el.getAsInt() == 0)
			{
				continue;
			}
			Integer item = map.get(e.getKey());
			if (item != null && item > 0)
			{
				out.add(item);
			}
		}
	}

	private Map<String, Integer> varbitToItemMap()
	{
		Map<String, Integer> m = varbitKeyToItemId;
		if (m != null)
		{
			return m;
		}
		synchronized (this)
		{
			if (varbitKeyToItemId != null)
			{
				return varbitKeyToItemId;
			}
			varbitKeyToItemId = readVarbitToItemResource();
			return varbitKeyToItemId;
		}
	}

	private Map<String, Integer> readVarbitToItemResource()
	{
		try (InputStream in = CollectionLogItemIdsSupport.class.getResourceAsStream(VARBIT_TO_ITEM_RESOURCE))
		{
			if (in == null)
			{
				return Map.of();
			}
			JsonObject o = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			if (o == null)
			{
				return Map.of();
			}
			HashMap<String, Integer> map = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : o.entrySet())
			{
				if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber())
				{
					continue;
				}
				int item = e.getValue().getAsInt();
				if (item > 0)
				{
					map.put(e.getKey(), item);
				}
			}
			log.debug("Terpinheimer: loaded {} varbit→item mappings from {}", map.size(), VARBIT_TO_ITEM_RESOURCE);
			return map;
		}
		catch (Exception e)
		{
			log.warn("Terpinheimer: could not load {}: {}", VARBIT_TO_ITEM_RESOURCE, e.toString());
			return Map.of();
		}
	}
}
