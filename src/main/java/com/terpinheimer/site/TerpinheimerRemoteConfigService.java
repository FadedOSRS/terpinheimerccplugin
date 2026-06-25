package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.terpinheimer.TerpinheimerLinks;
import com.terpinheimer.clan.RankTitlePermissionList;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches announcements and rank permissions from terpinheimercc.com. URLs, API endpoints,
 * WOM settings, and feature flags come from {@link TerpinheimerLinks} only.
 */
@Singleton
public class TerpinheimerRemoteConfigService
{
	private static final Logger log = LoggerFactory.getLogger(TerpinheimerRemoteConfigService.class);
	private static final String CONFIG_GROUP = "terpinheimer";
	private static final String CACHE_KEY = "remotePluginConfigJson";

	private final OkHttpClient http;
	private final Gson gson;
	private final ConfigManager configManager;

	private volatile TerpinheimerRemoteConfigDto active = new TerpinheimerRemoteConfigDto();

	@Inject
	TerpinheimerRemoteConfigService(OkHttpClient http, Gson gson, ConfigManager configManager)
	{
		this.http = http;
		this.gson = gson;
		this.configManager = configManager;
		loadFromCache();
	}

	public void loadFromCache()
	{
		String cached = configManager.getConfiguration(CONFIG_GROUP, CACHE_KEY);
		if (cached == null || cached.trim().isEmpty())
		{
			return;
		}
		try
		{
			active = parse(cached);
		}
		catch (Exception e)
		{
			log.debug("Could not parse cached remote plugin config: {}", e.getMessage());
		}
	}

	public void refreshFromNetwork() throws IOException
	{
		String body = fetchJson(TerpinheimerLinks.PLUGIN_CONFIG_API);
		active = parse(body);
		configManager.setConfiguration(CONFIG_GROUP, CACHE_KEY, body);
	}

	public boolean hasRemoteAnnouncements()
	{
		TerpinheimerRemoteConfigDto.Announcements a = active.announcements;
		if (a == null)
		{
			return false;
		}
		return a.enabled != null || (a.text != null && !a.text.isEmpty());
	}

	public boolean isAnnouncementsEnabled()
	{
		TerpinheimerRemoteConfigDto.Announcements a = active.announcements;
		if (a != null && a.enabled != null)
		{
			return a.enabled;
		}
		return true;
	}

	public String getAnnouncementsText()
	{
		TerpinheimerRemoteConfigDto.Announcements a = active.announcements;
		if (a != null && a.text != null)
		{
			return a.text;
		}
		return "";
	}

	public String getDiscord()
	{
		return TerpinheimerLinks.DISCORD;
	}

	public String getNameChangesChannel()
	{
		return TerpinheimerLinks.NAME_CHANGES;
	}

	public String getAnnouncementsChannel()
	{
		return TerpinheimerLinks.ANNOUNCEMENTS;
	}

	public String getEventsChannel()
	{
		return TerpinheimerLinks.EVENTS;
	}

	public String getWebsite()
	{
		return TerpinheimerLinks.WEBSITE;
	}

	public String getWiseOldManGroup()
	{
		return TerpinheimerLinks.WISE_OLD_MAN_GROUP;
	}

	public String getLiveClanMapPage()
	{
		return TerpinheimerLinks.LIVE_CLAN_MAP;
	}

	public String getClanCalendarPage()
	{
		return TerpinheimerLinks.CLAN_CALENDAR_PAGE;
	}

	public String getClanCalendarSummaryApi()
	{
		return TerpinheimerLinks.CLAN_CALENDAR_SUMMARY_API;
	}

	public String getLiveMapApiBase()
	{
		return TerpinheimerLinks.LIVE_MAP_API_BASE;
	}

	public String getClogSyncApi()
	{
		return ClogSiteSyncService.normalizeSitePostUrl(TerpinheimerLinks.CLOG_SYNC_API);
	}

	public String getClanRosterSyncApi()
	{
		return ClogSiteSyncService.normalizeSitePostUrl(TerpinheimerLinks.CLAN_ROSTER_SYNC_API);
	}

	public String getAttendanceSyncApi()
	{
		return ClogSiteSyncService.normalizeSitePostUrl(TerpinheimerLinks.ATTENDANCE_SYNC_API);
	}

	public boolean isClanRosterSyncEnabled()
	{
		return TerpinheimerLinks.CLAN_ROSTER_SYNC_ENABLED;
	}

	public boolean isClogAutomaticSyncEnabled()
	{
		return TerpinheimerLinks.CLOG_AUTOMATIC_SYNC_ENABLED;
	}

	public boolean isClogRapidSyncOnNewItem()
	{
		return TerpinheimerLinks.CLOG_RAPID_SYNC_ON_NEW_ITEM;
	}

	public boolean isWomUpdateProfileOnLogout()
	{
		return TerpinheimerLinks.WOM_UPDATE_PROFILE_ON_LOGOUT;
	}

	public boolean isWomSyncOnlyAfterProgress()
	{
		return TerpinheimerLinks.WOM_SYNC_ONLY_AFTER_PROGRESS;
	}

	public String getClogRunescapeNameOverride()
	{
		return TerpinheimerLinks.CLOG_SYNC_RUNESCAPE_NAME_OVERRIDE;
	}

	public int getWomGroupId()
	{
		return TerpinheimerLinks.WOM_GROUP_ID;
	}

	public String getWomApiBase()
	{
		return TerpinheimerLinks.WOM_API_BASE;
	}

	/**
	 * Rank titles allowed to POST the clan roster. From {@code permissions.clanRosterPostRankTitles}
	 * on the website. When omitted, {@link TerpinheimerLinks#CLAN_ROSTER_POST_RANK_TITLES_DEFAULT}.
	 */
	public List<String> getClanRosterPostRankTitles()
	{
		String raw = permissionsField("clanRosterPostRankTitles");
		if (raw == null)
		{
			return RankTitlePermissionList.parse(TerpinheimerLinks.CLAN_ROSTER_POST_RANK_TITLES_DEFAULT);
		}
		List<String> parsed = RankTitlePermissionList.parse(raw);
		if (parsed.isEmpty())
		{
			return RankTitlePermissionList.parse(TerpinheimerLinks.CLAN_ROSTER_POST_RANK_TITLES_DEFAULT);
		}
		return parsed;
	}

	/**
	 * Rank titles allowed to use Clan Event tracker. When omitted or empty on the website, returns
	 * an empty list (no rank restriction).
	 */
	public List<String> getClanEventTrackerRankTitles()
	{
		String raw = permissionsField("clanEventTrackerRankTitles");
		if (raw == null)
		{
			return RankTitlePermissionList.parse(TerpinheimerLinks.CLAN_EVENT_TRACKER_RANK_TITLES_DEFAULT);
		}
		return RankTitlePermissionList.parse(raw);
	}

	public String getWiseOldManCompetitionPageBase()
	{
		return TerpinheimerLinks.WISE_OLD_MAN_COMPETITION_PAGE_BASE;
	}

	private String fetchJson(String httpsUrl) throws IOException
	{
		String rl = RuneLiteProperties.getVersion();
		String ua = "Terpinheimer/2.0.0 RuneLite/" + (rl != null ? rl : "unknown");
		Request req = new Request.Builder()
			.url(httpsUrl)
			.header("User-Agent", ua)
			.header("Accept", "application/json")
			.get()
			.build();
		try (Response res = http.newCall(req).execute())
		{
			ResponseBody b = res.body();
			String body = b != null ? b.string() : "";
			if (!res.isSuccessful())
			{
				throw new IOException("HTTP " + res.code());
			}
			return body;
		}
	}

	private TerpinheimerRemoteConfigDto parse(String json)
	{
		JsonObject root = gson.fromJson(json, JsonObject.class);
		if (root == null)
		{
			return new TerpinheimerRemoteConfigDto();
		}
		TerpinheimerRemoteConfigDto dto = new TerpinheimerRemoteConfigDto();
		if (root.has("announcements") && root.get("announcements").isJsonObject())
		{
			dto.announcements = gson.fromJson(root.get("announcements"), TerpinheimerRemoteConfigDto.Announcements.class);
		}
		applyPermissions(root, dto);
		return dto;
	}

	private static void applyPermissions(JsonObject root, TerpinheimerRemoteConfigDto dto)
	{
		if (dto.permissions == null)
		{
			dto.permissions = new TerpinheimerRemoteConfigDto.Permissions();
		}

		setIfPresent(root, "clanRosterPostRankTitles", v -> dto.permissions.clanRosterPostRankTitles = v);
		setIfPresent(root, "clanEventTrackerRankTitles", v -> dto.permissions.clanEventTrackerRankTitles = v);

		if (root.has("permissions") && root.get("permissions").isJsonObject())
		{
			JsonObject perms = root.getAsJsonObject("permissions");
			setIfPresent(perms, "clanRosterPostRankTitles", v -> dto.permissions.clanRosterPostRankTitles = v);
			setIfPresent(perms, "clanEventTrackerRankTitles", v -> dto.permissions.clanEventTrackerRankTitles = v);
			applyPermissionArray(perms, "clanRosterPostRankTitles", titles -> dto.permissions.clanRosterPostRankTitles = titles);
			applyPermissionArray(perms, "clanEventTrackerRankTitles", titles -> dto.permissions.clanEventTrackerRankTitles = titles);
		}

		applyPermissionArray(root, "clanRosterPostRankTitles", titles -> dto.permissions.clanRosterPostRankTitles = titles);
		applyPermissionArray(root, "clanEventTrackerRankTitles", titles -> dto.permissions.clanEventTrackerRankTitles = titles);
	}

	private String permissionsField(String key)
	{
		TerpinheimerRemoteConfigDto.Permissions p = active.permissions;
		if (p == null)
		{
			return null;
		}
		switch (key)
		{
			case "clanRosterPostRankTitles":
				return p.clanRosterPostRankTitles;
			case "clanEventTrackerRankTitles":
				return p.clanEventTrackerRankTitles;
			default:
				return null;
		}
	}

	private static void applyPermissionArray(JsonObject root, String key, java.util.function.Consumer<String> setter)
	{
		if (!root.has(key) || root.get(key).isJsonNull())
		{
			return;
		}
		JsonElement el = root.get(key);
		if (!el.isJsonArray())
		{
			return;
		}
		List<String> titles = RankTitlePermissionList.parseJsonElement(el);
		if (!titles.isEmpty())
		{
			setter.accept(String.join(",", titles));
		}
	}

	private static void setIfPresent(JsonObject root, String key, java.util.function.Consumer<String> setter)
	{
		if (!root.has(key) || root.get(key).isJsonNull())
		{
			return;
		}
		JsonElement el = root.get(key);
		if (el.isJsonPrimitive())
		{
			String v = el.getAsString();
			if (v != null)
			{
				setter.accept(v.trim());
			}
		}
	}
}
