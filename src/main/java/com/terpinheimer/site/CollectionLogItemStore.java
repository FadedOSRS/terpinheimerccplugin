package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RuneProfile-style collection log storage: {@code Map<itemId, quantity>} persisted per Jagex account hash.
 */
@Singleton
public final class CollectionLogItemStore
{
	private static final Logger log = LoggerFactory.getLogger(CollectionLogItemStore.class);
	private static final String CONFIG_GROUP = "terpinheimer";
	private static final String CONFIG_KEY_PREFIX = "clogItems.";
	private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() {}.getType();

	private final Client client;
	private final ConfigManager configManager;
	private final Gson gson;
	private final Map<Integer, Integer> items = new HashMap<>();
	private volatile long lastPersistEpochMs;
	private static final int PERSIST_DEBOUNCE_MS = 4000;

	@Inject
	CollectionLogItemStore(Client client, ConfigManager configManager, Gson gson)
	{
		this.client = client;
		this.configManager = configManager;
		this.gson = gson;
	}

	public void reloadForCurrentAccount()
	{
		long hash = client.getAccountHash();
		synchronized (items)
		{
			items.clear();
			if (hash == 0L)
			{
				return;
			}
			String raw = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_PREFIX + hash);
			if (raw == null || raw.isEmpty())
			{
				return;
			}
			Map<String, Integer> parsed = gson.fromJson(raw, MAP_TYPE);
			if (parsed == null)
			{
				return;
			}
			for (Map.Entry<String, Integer> e : parsed.entrySet())
			{
				try
				{
					int id = Integer.parseInt(e.getKey());
					int qty = e.getValue() != null ? e.getValue() : 0;
					if (id > 0 && qty > 0)
					{
						items.put(id, Math.min(65535, qty));
					}
				}
				catch (NumberFormatException ignored)
				{
					// skip
				}
			}
		}
		log.debug("Terpinheimer: loaded {} collection log item(s) from disk", items.size());
	}

	public void storeItem(int itemId, int quantity)
	{
		if (itemId <= 0 || quantity <= 0)
		{
			return;
		}
		int qty = Math.min(65535, quantity);
		boolean changed;
		synchronized (items)
		{
			Integer prev = items.get(itemId);
			int merged = prev == null ? qty : Math.max(prev, qty);
			changed = prev == null || merged != prev;
			items.put(itemId, merged);
		}
		if (changed)
		{
			persistForCurrentAccountDebounced();
		}
	}

	public void persistNow()
	{
		persistForCurrentAccount();
	}

	public int size()
	{
		synchronized (items)
		{
			return items.size();
		}
	}

	public void writeItemsJson(JsonObject root)
	{
		if (root == null)
		{
			return;
		}
		JsonObject map = new JsonObject();
		synchronized (items)
		{
			for (Map.Entry<Integer, Integer> e : items.entrySet())
			{
				map.addProperty(Integer.toString(e.getKey()), e.getValue());
			}
		}
		if (map.size() > 0)
		{
			root.add("items", map);
		}
	}

	private void persistForCurrentAccountDebounced()
	{
		long now = System.currentTimeMillis();
		if (now - lastPersistEpochMs < PERSIST_DEBOUNCE_MS)
		{
			return;
		}
		lastPersistEpochMs = now;
		persistForCurrentAccount();
	}

	private void persistForCurrentAccount()
	{
		long hash = client.getAccountHash();
		if (hash == 0L)
		{
			return;
		}
		Map<String, Integer> out = new HashMap<>();
		synchronized (items)
		{
			for (Map.Entry<Integer, Integer> e : items.entrySet())
			{
				out.put(Integer.toString(e.getKey()), e.getValue());
			}
		}
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_PREFIX + hash, gson.toJson(out));
	}
}
