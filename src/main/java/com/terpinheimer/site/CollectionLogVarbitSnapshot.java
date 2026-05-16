package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads collection-log varbits via {@link Client#getVarbitValue(int)} using a bundled id list
 * ({@code /collection-varbit-ids.json}) — no runtime reflection on RuneLite gameval classes.
 */
@Singleton
public final class CollectionLogVarbitSnapshot
{
	private static final Logger log = LoggerFactory.getLogger(CollectionLogVarbitSnapshot.class);
	private static final String VARBIT_IDS_RESOURCE = "/collection-varbit-ids.json";
	private static final Type INT_LIST_TYPE = new TypeToken<List<Integer>>() {}.getType();

	private final Gson gson;
	private volatile int[] varbitIds;

	@Inject
	CollectionLogVarbitSnapshot(Gson gson)
	{
		this.gson = gson;
	}

	/** Fills {@code out} with non-zero varbit values (keys = varbit id strings). */
	void snapshot(Client client, JsonObject out)
	{
		if (client == null || out == null)
		{
			return;
		}
		for (int varbitId : varbitIds())
		{
			int value = client.getVarbitValue(varbitId);
			if (value != 0)
			{
				out.addProperty(Integer.toString(varbitId), value);
			}
		}
	}

	private int[] varbitIds()
	{
		int[] cached = varbitIds;
		if (cached != null)
		{
			return cached;
		}
		synchronized (this)
		{
			if (varbitIds != null)
			{
				return varbitIds;
			}
			varbitIds = loadVarbitIds();
			return varbitIds;
		}
	}

	private int[] loadVarbitIds()
	{
		try (InputStream in = CollectionLogVarbitSnapshot.class.getResourceAsStream(VARBIT_IDS_RESOURCE))
		{
			if (in == null)
			{
				log.warn("Terpinheimer: missing {}", VARBIT_IDS_RESOURCE);
				return new int[0];
			}
			List<Integer> list = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), INT_LIST_TYPE);
			if (list == null || list.isEmpty())
			{
				return new int[0];
			}
			int[] out = new int[list.size()];
			int n = 0;
			for (Integer id : list)
			{
				if (id != null && id > 0)
				{
					out[n++] = id;
				}
			}
			if (n < out.length)
			{
				int[] trimmed = new int[n];
				System.arraycopy(out, 0, trimmed, 0, n);
				out = trimmed;
			}
			log.debug("Terpinheimer: loaded {} collection varbit ids from {}", out.length, VARBIT_IDS_RESOURCE);
			return out;
		}
		catch (Exception e)
		{
			log.warn("Terpinheimer: could not load {}: {}", VARBIT_IDS_RESOURCE, e.toString());
			return new int[0];
		}
	}
}
