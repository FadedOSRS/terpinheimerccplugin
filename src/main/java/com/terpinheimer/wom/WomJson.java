package com.terpinheimer.wom;

import java.util.List;

/**
 * Gson DTOs for Wise Old Man API v2 (competition + group competition list).
 */
final class WomJson
{
	static final class CompetitionDetails
	{
		long id;
		String title;
		String metric;
		String startsAt;
		String endsAt;
		List<Participation> participations;
	}

	static final class CompetitionSummary
	{
		long id;
		String title;
		String metric;
		String startsAt;
		String endsAt;
	}

	static final class Participation
	{
		Player player;
		Progress progress;
	}

	static final class Player
	{
		String username;
		String displayName;
		long exp;
		double ehp;
		double ehb;
	}

	static final class Progress
	{
		Number gained;

		long gainedLong()
		{
			return gained == null ? 0L : gained.longValue();
		}
	}
}
