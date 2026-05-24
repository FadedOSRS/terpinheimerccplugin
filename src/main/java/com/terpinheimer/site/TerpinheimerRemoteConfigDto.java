package com.terpinheimer.site;

import com.terpinheimer.TerpinheimerLinks;

/** Parsed JSON from {@code GET /api/runelite/plugin-config}. Null fields use {@link TerpinheimerLinks} defaults. */
final class TerpinheimerRemoteConfigDto
{
	static final class Links
	{
		String discord;
		String discordNameChanges;
		String discordAnnouncements;
		String discordEvents;
		String website;
		String wiseOldManGroup;
		String liveClanMapPage;
		String clanCalendarPage;
	}

	static final class Apis
	{
		String pluginConfig;
		String clanCalendarSummary;
		String liveMapBase;
		String clogSync;
		String clanRosterSync;
		String attendanceSync;
	}

	static final class Features
	{
		Boolean clanRosterSyncEnabled;
		Boolean clogAutomaticSyncEnabled;
		Boolean clogRapidSyncOnNewItem;
		Boolean womUpdateProfileOnLogout;
		Boolean womSyncOnlyAfterProgress;
	}

	static final class WiseOldMan
	{
		Integer groupId;
		String apiBase;
		String competitionPageBase;
	}

	static final class Announcements
	{
		Boolean enabled;
		String text;
	}

	static final class CollectionLog
	{
		String runescapeNameOverride;
	}

	Links links = new Links();
	Apis apis = new Apis();
	Features features = new Features();
	WiseOldMan wiseOldMan = new WiseOldMan();
	Announcements announcements = new Announcements();
	CollectionLog collectionLog = new CollectionLog();
}
