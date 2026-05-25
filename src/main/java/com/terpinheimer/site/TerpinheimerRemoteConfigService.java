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
 * Fetches clan-wide URLs and API endpoints from terpinheimercc.com. Values fall back to
 * {@link TerpinheimerLinks} when the server is unreachable or a field is omitted.
 */
@Singleton
public class TerpinheimerRemoteConfigService
{
	private static final Logger log = LoggerFactory.getLogger(TerpinheimerRemoteConfigService.class);
	private static final String CONFIG_GROUP = "terpinheimer";
	private static final String CACHE_KEY = "remotePluginConfigJson";

	/** Immutable bootstrap URL — only this address is hardcoded in the plugin. */
	public static final String BOOTSTRAP_URL = "https://terpinheimercc.com/api/runelite/plugin-config";

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
			active = parseAndMerge(cached);
		}
		catch (Exception e)
		{
			log.debug("Could not parse cached remote plugin config: {}", e.getMessage());
		}
	}

	public void refreshFromNetwork() throws IOException
	{
		String body = fetchJson(BOOTSTRAP_URL);
		TerpinheimerRemoteConfigDto parsed = parseAndMerge(body);
		active = parsed;
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
		return link(active.links != null ? active.links.discord : null, TerpinheimerLinks.DISCORD);
	}

	public String getNameChangesChannel()
	{
		return link(active.links != null ? active.links.discordNameChanges : null, TerpinheimerLinks.NAME_CHANGES);
	}

	public String getAnnouncementsChannel()
	{
		return link(active.links != null ? active.links.discordAnnouncements : null, TerpinheimerLinks.ANNOUNCEMENTS);
	}

	public String getEventsChannel()
	{
		return link(active.links != null ? active.links.discordEvents : null, TerpinheimerLinks.EVENTS);
	}

	public String getWebsite()
	{
		return link(active.links != null ? active.links.website : null, TerpinheimerLinks.WEBSITE);
	}

	public String getWiseOldManGroup()
	{
		return link(active.links != null ? active.links.wiseOldManGroup : null, TerpinheimerLinks.WISE_OLD_MAN_GROUP);
	}

	public String getLiveClanMapPage()
	{
		return link(active.links != null ? active.links.liveClanMapPage : null, TerpinheimerLinks.LIVE_CLAN_MAP);
	}

	public String getClanCalendarPage()
	{
		return link(active.links != null ? active.links.clanCalendarPage : null, TerpinheimerLinks.CLAN_CALENDAR_PAGE);
	}

	public String getClanCalendarSummaryApi()
	{
		return api(active.apis != null ? active.apis.clanCalendarSummary : null, TerpinheimerLinks.CLAN_CALENDAR_SUMMARY_API);
	}

	public String getLiveMapApiBase()
	{
		return api(active.apis != null ? active.apis.liveMapBase : null, TerpinheimerLinks.LIVE_MAP_API_BASE);
	}

	public String getClogSyncApi()
	{
		return normalizePostApi(active.apis != null ? active.apis.clogSync : null, TerpinheimerLinks.CLOG_SYNC_API);
	}

	public String getClanRosterSyncApi()
	{
		return normalizePostApi(active.apis != null ? active.apis.clanRosterSync : null, TerpinheimerLinks.CLAN_ROSTER_SYNC_API);
	}

	public String getAttendanceSyncApi()
	{
		return normalizePostApi(
			active.apis != null ? active.apis.attendanceSync : null,
			TerpinheimerLinks.ATTENDANCE_SYNC_API);
	}

	public boolean isClanRosterSyncEnabled()
	{
		TerpinheimerRemoteConfigDto.Features f = active.features;
		if (f != null && f.clanRosterSyncEnabled != null)
		{
			return f.clanRosterSyncEnabled;
		}
		return TerpinheimerLinks.CLAN_ROSTER_SYNC_ENABLED;
	}

	public String getClogRunescapeNameOverride()
	{
		TerpinheimerRemoteConfigDto.CollectionLog c = active.collectionLog;
		if (c != null && c.runescapeNameOverride != null)
		{
			return c.runescapeNameOverride;
		}
		return TerpinheimerLinks.CLOG_SYNC_RUNESCAPE_NAME_OVERRIDE;
	}

	public int getWomGroupId()
	{
		TerpinheimerRemoteConfigDto.WiseOldMan w = active.wiseOldMan;
		if (w != null && w.groupId != null && w.groupId > 0)
		{
			return w.groupId;
		}
		return 0;
	}

	/**
	 * Rank titles allowed to POST the clan roster. From {@code permissions.clanRosterPostRankTitles}
	 * on the website (comma-separated, e.g. {@code Deputy Owner,Ruby}). When omitted, {@link TerpinheimerLinks#CLAN_ROSTER_POST_RANK_TITLES_DEFAULT}.
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
	 * Rank titles allowed to use Clan Event tracker (start/stop/post). When omitted or empty on the
	 * website, returns an empty list and the plugin treats that as no restriction.
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
		TerpinheimerRemoteConfigDto.WiseOldMan w = active.wiseOldMan;
		if (w != null && w.competitionPageBase != null && !w.competitionPageBase.trim().isEmpty())
		{
			String base = w.competitionPageBase.trim();
			while (base.endsWith("/"))
			{
				base = base.substring(0, base.length() - 1);
			}
			return base;
		}
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

	private TerpinheimerRemoteConfigDto parseAndMerge(String json)
	{
		JsonObject root = gson.fromJson(json, JsonObject.class);
		if (root == null)
		{
			return new TerpinheimerRemoteConfigDto();
		}
		TerpinheimerRemoteConfigDto dto = gson.fromJson(root, TerpinheimerRemoteConfigDto.class);
		if (dto == null)
		{
			dto = new TerpinheimerRemoteConfigDto();
		}
		applyFlatRootKeys(root, dto);
		return dto;
	}

	/** Supports legacy flat JSON keys at the root alongside the nested schema. */
	private static void applyFlatRootKeys(JsonObject root, TerpinheimerRemoteConfigDto dto)
	{
		if (dto.links == null)
		{
			dto.links = new TerpinheimerRemoteConfigDto.Links();
		}
		if (dto.apis == null)
		{
			dto.apis = new TerpinheimerRemoteConfigDto.Apis();
		}
		if (dto.features == null)
		{
			dto.features = new TerpinheimerRemoteConfigDto.Features();
		}
		if (dto.permissions == null)
		{
			dto.permissions = new TerpinheimerRemoteConfigDto.Permissions();
		}

		setIfPresent(root, "discord", v -> dto.links.discord = v);
		setIfPresent(root, "discordNameChanges", v -> dto.links.discordNameChanges = v);
		setIfPresent(root, "discordAnnouncements", v -> dto.links.discordAnnouncements = v);
		setIfPresent(root, "discordEvents", v -> dto.links.discordEvents = v);
		setIfPresent(root, "website", v -> dto.links.website = v);
		setIfPresent(root, "wiseOldManGroup", v -> dto.links.wiseOldManGroup = v);
		setIfPresent(root, "liveClanMapPage", v -> dto.links.liveClanMapPage = v);
		setIfPresent(root, "clanCalendarPage", v -> dto.links.clanCalendarPage = v);

		setIfPresent(root, "clanCalendarSummaryApi", v -> dto.apis.clanCalendarSummary = v);
		setIfPresent(root, "liveMapApiBase", v -> dto.apis.liveMapBase = v);
		setIfPresent(root, "clogSyncApi", v -> dto.apis.clogSync = v);
		setIfPresent(root, "clanRosterSyncApi", v -> dto.apis.clanRosterSync = v);
		setIfPresent(root, "attendanceSyncApi", v -> dto.apis.attendanceSync = v);

		if (root.has("clanRosterSyncEnabled") && !root.get("clanRosterSyncEnabled").isJsonNull())
		{
			dto.features.clanRosterSyncEnabled = root.get("clanRosterSyncEnabled").getAsBoolean();
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
			if (v != null && !v.trim().isEmpty())
			{
				setter.accept(v.trim());
			}
		}
	}

	private static String link(String remote, String fallback)
	{
		String v = sanitizeHttps(remote);
		return v != null ? v : fallback;
	}

	private static String api(String remote, String fallback)
	{
		String v = sanitizeHttps(remote);
		return v != null ? v : fallback;
	}

	private static String normalizePostApi(String remote, String fallback)
	{
		String v = sanitizeHttps(remote);
		if (v == null)
		{
			v = fallback;
		}
		return ClogSiteSyncService.normalizeSitePostUrl(v);
	}

	private static String sanitizeHttps(String url)
	{
		if (url == null)
		{
			return null;
		}
		String t = url.trim();
		if (t.isEmpty() || !t.startsWith("https://"))
		{
			return null;
		}
		return t;
	}
}
