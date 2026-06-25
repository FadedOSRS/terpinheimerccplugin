package com.terpinheimer.site;

/** Parsed announcements and permissions from {@code GET /api/runelite/plugin-config}. */
final class TerpinheimerRemoteConfigDto
{
	static final class Announcements
	{
		Boolean enabled;
		String text;
	}

	/**
	 * Rank titles allowed for sensitive actions. Values are comma-separated in-game rank titles
	 * (e.g. {@code Deputy Owner,Ruby}) as shown in the Jagex clan panel / roster sync payload.
	 */
	static final class Permissions
	{
		String clanRosterPostRankTitles;
		String clanEventTrackerRankTitles;
	}

	Announcements announcements = new Announcements();
	Permissions permissions = new Permissions();
}
