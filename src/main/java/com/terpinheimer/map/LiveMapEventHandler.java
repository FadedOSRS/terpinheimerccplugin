package com.terpinheimer.map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Varbits;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Pushes the local player's world position on a throttled schedule so a clan website can plot it,
 * following the same client-side approach as the Goblin Scape clan plugin.
 */
@Singleton
public class LiveMapEventHandler
{
	private final Client client;
	private final TerpinheimerConfig config;
	private final Gson gson;
	private final LiveMapService liveMapService;

	private int tickCounter;

	@Inject
	LiveMapEventHandler(Client client, TerpinheimerConfig config, Gson gson, LiveMapService liveMapService)
	{
		this.client = client;
		this.config = config;
		this.gson = gson;
		this.liveMapService = liveMapService;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!config.liveMapEnabled())
		{
			return;
		}
		if (config.liveMapEventHide())
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int interval = Math.max(1, config.liveMapIntervalTicks());
		tickCounter++;
		if (tickCounter < interval)
		{
			return;
		}
		tickCounter = 0;

		String base = config.liveMapApiBaseUrl();
		String key = config.liveMapApiKey();
		String postUrl = LiveMapService.buildPostUrl(base);
		if (postUrl == null || !isValidHttpsBase(base) || key == null || key.trim().isEmpty())
		{
			return;
		}

		if (!config.liveMapSendInWilderness() && client.getVarbitValue(Varbits.IN_WILDERNESS) != 0)
		{
			return;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		LocalPoint local = player.getLocalLocation();
		if (local == null)
		{
			return;
		}
		WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, local);
		if (worldPoint == null)
		{
			return;
		}

		String displayName = Text.removeTags(player.getName());
		if (displayName == null || displayName.isBlank())
		{
			return;
		}

		JsonObject waypoint = new JsonObject();
		waypoint.addProperty("x", worldPoint.getX());
		waypoint.addProperty("y", worldPoint.getY());
		waypoint.addProperty("plane", worldPoint.getPlane());

		JsonObject root = new JsonObject();
		root.addProperty("name", displayName);
		root.add("waypoint", waypoint);
		root.addProperty("title", clanTitleForPlayer(displayName));
		root.addProperty("world", client.getWorld());

		liveMapService.postPlayerLocation(postUrl, key, gson.toJson(root));
	}

	private static boolean isValidHttpsBase(String url)
	{
		if (url == null)
		{
			return false;
		}
		String t = url.trim();
		return t.startsWith("https://") && t.length() > "https://x".length();
	}

	private String clanTitleForPlayer(String playerName)
	{
		ClanSettings settings = client.getClanSettings();
		if (settings == null)
		{
			return "";
		}
		ClanMember member = settings.findMember(playerName);
		if (member == null)
		{
			return "";
		}
		return settings.titleForRank(member.getRank()).getName();
	}
}
