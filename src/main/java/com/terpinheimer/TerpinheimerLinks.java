package com.terpinheimer;

/**
 * Built-in TerpinheimerCC URLs, API endpoints, WOM settings, and feature flags.
 * Not fetched from the website and not exposed in plugin settings.
 */
public final class TerpinheimerLinks
{
	private TerpinheimerLinks()
	{
	}

	// ---- Links ----

	public static final String DISCORD = "https://discord.gg/NTWqmhSx4U";
	public static final String NAME_CHANGES =
		"https://discord.com/channels/1392895914762567861/1392903002507182150";
	public static final String ANNOUNCEMENTS =
		"https://discord.com/channels/1392895914762567861/1460796088524210276";
	public static final String EVENTS =
		"https://discord.com/channels/1392895914762567861/1392903038066491432";
	public static final String WEBSITE = "https://terpinheimercc.com/";
	public static final String WISE_OLD_MAN_GROUP = "https://wiseoldman.net/groups/23745";
	public static final String LIVE_CLAN_MAP = "https://terpinheimercc.com/#/map";
	public static final String CLAN_CALENDAR_PAGE = "https://terpinheimercc.com/#/events";

	// ---- APIs ----

	public static final String PLUGIN_CONFIG_API =
		"https://terpinheimercc.com/api/runelite/plugin-config";
	public static final String CLAN_CALENDAR_SUMMARY_API =
		"https://terpinheimercc.com/api/runelite/clan-calendar-summary?format=array";
	public static final String LIVE_MAP_API_BASE = "https://terpinheimercc.com";
	public static final String CLOG_SYNC_API = "https://terpinheimercc.com/api/clog/sync";
	public static final String CLAN_ROSTER_SYNC_API = "https://terpinheimercc.com/api/clan/roster/sync";
	public static final String ATTENDANCE_SYNC_API = "https://terpinheimercc.com/api/clan/attendance/sync";

	// ---- Wise Old Man ----

	public static final int WOM_GROUP_ID = 23745;
	public static final String WOM_API_BASE = "https://api.wiseoldman.net/v2";
	public static final String WISE_OLD_MAN_COMPETITION_PAGE_BASE = "https://wiseoldman.net/competitions";

	// ---- Features ----

	public static final boolean CLAN_ROSTER_SYNC_ENABLED = false;
	public static final boolean CLOG_AUTOMATIC_SYNC_ENABLED = false;
	public static final boolean CLOG_RAPID_SYNC_ON_NEW_ITEM = false;
	public static final boolean WOM_UPDATE_PROFILE_ON_LOGOUT = true;
	public static final boolean WOM_SYNC_ONLY_AFTER_PROGRESS = false;

	// ---- Collection log ----

	public static final String CLOG_SYNC_RUNESCAPE_NAME_OVERRIDE = "";

	/** When the website omits {@code permissions.clanRosterPostRankTitles}. */
	public static final String CLAN_ROSTER_POST_RANK_TITLES_DEFAULT = "Owner";

	/**
	 * When the website omits {@code permissions.clanEventTrackerRankTitles}.
	 * Empty = no rank restriction.
	 */
	public static final String CLAN_EVENT_TRACKER_RANK_TITLES_DEFAULT = "";
}
