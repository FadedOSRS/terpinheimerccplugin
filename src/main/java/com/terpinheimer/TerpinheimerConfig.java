package com.terpinheimer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("terpinheimer")
public interface TerpinheimerConfig extends Config
{
	String SEC_GEN = "terpinheimerGeneral";
	String SEC_LIVE_MAP = "terpinheimerLiveMap";
	String SEC_ATTENDANCE = "terpinheimerAttendance";

	@ConfigSection(
		name = "General",
		description = "Refresh interval, announcements, clan secret (live map, collection log, roster sync), RuneLite sidebar icon.",
		position = 0,
		closedByDefault = true
	)
	String generalSection = SEC_GEN;

	@ConfigSection(
		name = "Live clan map",
		description = "Opt-in only (off by default). When enabled, POSTs your own position to the clan live map API using Clan secret under General.",
		position = 1,
		closedByDefault = true
	)
	String liveMapSection = SEC_LIVE_MAP;

	@ConfigSection(
		name = "Clan event attendance",
		description = "Built-in tracker (same idea as Plugin Hub Clan Event Attendance). Jagex clan chat only; open from Home → Clan Event tracker.",
		position = 2,
		closedByDefault = true
	)
	String attendanceSection = SEC_ATTENDANCE;

	// ---- General ----

	@ConfigItem(keyName = "announcementsEnabled", name = "Show announcements", description = "Show the announcements block on Home", position = 0, section = SEC_GEN)
	default boolean announcementsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "announcementsText",
		name = "Announcements text",
		description = "Static text for the Home tab announcements block (use \\n for line breaks). Only Owner or Deputy Owner (Jagex clan rank, logged in) can save changes; other ranks revert to the last authorized text. Other plugin settings are not rank-locked.",
		position = 1,
		section = SEC_GEN
	)
	default String announcementsText()
	{
		return "Welcome! Check Discord for the latest clan news.";
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "sidebarButtonPriority",
		name = "Sidebar icon position",
		description = "Sort order for this plugin's icon on the right sidebar. Lower numbers move it up; higher numbers move it down (same rule as RuneLite's other plugins).",
		position = 5,
		section = SEC_GEN
	)
	default int sidebarButtonPriority()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "clanSecret",
		name = "Clan secret",
		description = "One shared key for live map POSTs (Authorization header), collection log sync (syncToken), and clan roster sync. On terpinheimercc.com use the same value as RUNELITE_CLOG_SYNC_SECRET / your site RuneLite shared secret. For web session auth you may paste a full \"Bearer eyJ…\" JWT here instead.",
		position = 7,
		section = SEC_GEN,
		secret = true
	)
	default String clanSecret()
	{
		return "";
	}

	/** @deprecated use {@link #clanSecret()} — kept for call sites that still use the old name. */
	default String clogSyncApiSecret()
	{
		return clanSecret();
	}

	/** @deprecated use {@link #clanSecret()} — kept for call sites that still use the old name. */
	default String clanRosterSyncApiSecret()
	{
		return clanSecret();
	}

	// ---- Clan event attendance ----

	@Range(min = 0, max = 7200)
	@ConfigItem(
		keyName = "attendancePresentThresholdSeconds",
		name = "Present time threshold",
		description = "Minimum time in the area (logged) to count in the “Present members” list when the event stops.",
		position = 0,
		section = SEC_ATTENDANCE
	)
	@Units(Units.SECONDS)
	default int attendancePresentThresholdSeconds()
	{
		return 600;
	}

	@Range(min = 0, max = 7200)
	@ConfigItem(
		keyName = "attendanceLateThresholdSeconds",
		name = "Late threshold",
		description = "If “late” column is on, arrival after this many seconds from event start is marked late.",
		position = 1,
		section = SEC_ATTENDANCE
	)
	@Units(Units.SECONDS)
	default int attendanceLateThresholdSeconds()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "attendanceShowLateColumn",
		name = "Show late column",
		description = "Include a Late column in the text report.",
		position = 2,
		section = SEC_ATTENDANCE
	)
	default boolean attendanceShowLateColumn()
	{
		return true;
	}

	@ConfigItem(
		keyName = "attendanceDiscordCodeFence",
		name = "Discord code block on stop",
		description = "When you stop the event, wrap the final report in ``` for Discord.",
		position = 3,
		section = SEC_ATTENDANCE
	)
	default boolean attendanceDiscordCodeFence()
	{
		return false;
	}

	@ConfigItem(
		keyName = "attendanceConfirmStartStop",
		name = "Confirm start / stop",
		description = "Ask before starting (clears data) or stopping (finalizes report).",
		position = 4,
		section = SEC_ATTENDANCE
	)
	default boolean attendanceConfirmStartStop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "attendanceBlockCopyWhileRunning",
		name = "Block post while event runs",
		description = "When on, Post to Website is only enabled after you stop the event.",
		position = 5,
		section = SEC_ATTENDANCE
	)
	default boolean attendanceBlockCopyWhileRunning()
	{
		return false;
	}

	// ---- Live clan map ----

	@ConfigItem(
		keyName = "liveMapEnabled",
		name = "Enable live map API",
		description = "When on, periodically POST your position to the clan live map API ({base}/post) using General → Clan secret.",
		position = 0,
		section = SEC_LIVE_MAP
	)
	default boolean liveMapEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "liveMapEventHide",
		name = "Event: hide on live map",
		description = "While on, your position is never sent to the live map API (hide and seek, events, etc.). Your pin may linger on the website until your server expires stale data.",
		position = 1,
		section = SEC_LIVE_MAP
	)
	default boolean liveMapEventHide()
	{
		return false;
	}

	@ConfigItem(
		keyName = "liveMapSendInWilderness",
		name = "Send position in Wilderness",
		description = "When off, positions are not sent while the Wilderness varbit is set (matches Goblin Scape default).",
		position = 2,
		section = SEC_LIVE_MAP
	)
	default boolean liveMapSendInWilderness()
	{
		return false;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "liveMapIntervalTicks",
		name = "Update every N game ticks",
		description = "1 = every tick (~0.6s), like the reference plugin; higher values reduce API traffic.",
		position = 3,
		section = SEC_LIVE_MAP
	)
	default int liveMapIntervalTicks()
	{
		return 5;
	}
}
