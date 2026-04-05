package com.terpinheimer.wom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Immutable snapshot for UI after parsing Wise Old Man competition JSON.
 */
public final class WomLeaderboardModels
{
	private WomLeaderboardModels()
	{
	}

	public enum EventPhase
	{
		UPCOMING,
		ACTIVE,
		FINISHED,
		UNKNOWN
	}

	public static final class LeaderRow
	{
		private final int rank;
		private final String playerName;
		private final long gained;
		private final String tooltip;

		public LeaderRow(int rank, String playerName, long gained, String tooltip)
		{
			this.rank = rank;
			this.playerName = playerName;
			this.gained = gained;
			this.tooltip = tooltip;
		}

		public int getRank()
		{
			return rank;
		}

		public String getPlayerName()
		{
			return playerName;
		}

		public long getGained()
		{
			return gained;
		}

		public String getTooltip()
		{
			return tooltip;
		}
	}

	public static final class CompetitionSnapshot
	{
		private final boolean available;
		private final String error;
		private final long competitionId;
		private final String title;
		private final String metric;
		private final Instant startsAt;
		private final Instant endsAt;
		private final EventPhase phase;
		private final List<LeaderRow> top10;
		private final LeaderRow winner;
		private final long lastUpdatedEpochMs;
		private final String countdownHint;

		private CompetitionSnapshot(boolean available, String error, long competitionId, String title, String metric,
			Instant startsAt, Instant endsAt, EventPhase phase, List<LeaderRow> top10, LeaderRow winner,
			long lastUpdatedEpochMs, String countdownHint)
		{
			this.available = available;
			this.error = error;
			this.competitionId = competitionId;
			this.title = title;
			this.metric = metric;
			this.startsAt = startsAt;
			this.endsAt = endsAt;
			this.phase = phase;
			this.top10 = top10;
			this.winner = winner;
			this.lastUpdatedEpochMs = lastUpdatedEpochMs;
			this.countdownHint = countdownHint;
		}

		public static CompetitionSnapshot error(String message)
		{
			return new CompetitionSnapshot(false, message, 0L, "", "", null, null, EventPhase.UNKNOWN,
				Collections.emptyList(), null, 0L, "");
		}

		public static CompetitionSnapshot empty(String hint)
		{
			return new CompetitionSnapshot(false, "", 0L, "", "", null, null, EventPhase.UNKNOWN,
				Collections.emptyList(), null, 0L, hint);
		}

		public static CompetitionSnapshot ok(long competitionId, String title, String metric, Instant startsAt, Instant endsAt,
			EventPhase phase, List<LeaderRow> top10, LeaderRow winner, long lastUpdatedEpochMs, String countdownHint)
		{
			return new CompetitionSnapshot(true, "", competitionId, title, metric, startsAt, endsAt, phase, top10, winner,
				lastUpdatedEpochMs, countdownHint);
		}

		public boolean isAvailable()
		{
			return available;
		}

		public String getError()
		{
			return error;
		}

		public long getCompetitionId()
		{
			return competitionId;
		}

		public String getTitle()
		{
			return title;
		}

		public String getMetric()
		{
			return metric;
		}

		public Instant getStartsAt()
		{
			return startsAt;
		}

		public Instant getEndsAt()
		{
			return endsAt;
		}

		public EventPhase getPhase()
		{
			return phase;
		}

		public List<LeaderRow> getTop10()
		{
			return top10;
		}

		public LeaderRow getWinner()
		{
			return winner;
		}

		public long getLastUpdatedEpochMs()
		{
			return lastUpdatedEpochMs;
		}

		public String getCountdownHint()
		{
			return countdownHint;
		}
	}

	public static EventPhase phaseFor(Instant start, Instant end, Instant now)
	{
		if (start == null || end == null)
		{
			return EventPhase.UNKNOWN;
		}
		if (now.isBefore(start))
		{
			return EventPhase.UPCOMING;
		}
		if (now.isAfter(end))
		{
			return EventPhase.FINISHED;
		}
		return EventPhase.ACTIVE;
	}

	public static String formatCountdown(Instant start, Instant end, Instant now, EventPhase phase)
	{
		if (phase == EventPhase.UNKNOWN)
		{
			return "Schedule: —";
		}
		if (phase == EventPhase.UPCOMING)
		{
			return "Starts in " + humanDuration(now, start);
		}
		if (phase == EventPhase.ACTIVE)
		{
			return "Ends in " + humanDuration(now, end);
		}
		return "Ended";
	}

	private static String humanDuration(Instant from, Instant to)
	{
		long seconds = Math.max(0, to.getEpochSecond() - from.getEpochSecond());
		long d = seconds / 86400;
		long h = (seconds % 86400) / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;
		if (d > 0)
		{
			return String.format(Locale.US, "%dd %02dh %02dm", d, h, m);
		}
		if (h > 0)
		{
			return String.format(Locale.US, "%dh %02dm %02ds", h, m, s);
		}
		if (m > 0)
		{
			return String.format(Locale.US, "%dm %02ds", m, s);
		}
		return String.format(Locale.US, "%ds", s);
	}

	public static List<LeaderRow> topNFromParticipations(List<WomJson.Participation> participations, int n)
	{
		if (participations == null || participations.isEmpty())
		{
			return Collections.emptyList();
		}
		List<WomJson.Participation> sorted = new ArrayList<>(participations);
		sorted.sort(Comparator.comparingLong((WomJson.Participation p) -> p.progress != null ? p.progress.gainedLong() : 0L).reversed());
		List<LeaderRow> out = new ArrayList<>(Math.min(n, sorted.size()));
		for (int i = 0; i < sorted.size() && i < n; i++)
		{
			WomJson.Participation p = sorted.get(i);
			String name = p.player != null ? coalesce(p.player.displayName, p.player.username, "?") : "?";
			long g = p.progress != null ? p.progress.gainedLong() : 0L;
			String tip = buildTooltip(p);
			out.add(new LeaderRow(i + 1, name, g, tip));
		}
		return Collections.unmodifiableList(out);
	}

	private static String coalesce(String a, String b, String d)
	{
		if (a != null && !a.isEmpty())
		{
			return a;
		}
		if (b != null && !b.isEmpty())
		{
			return b;
		}
		return d;
	}

	private static String buildTooltip(WomJson.Participation p)
	{
		if (p.player == null)
		{
			return "";
		}
		return String.format(Locale.US, "Total XP: %s | EHP: %.2f | EHB: %.2f",
			formatLong(p.player.exp), p.player.ehp, p.player.ehb);
	}

	private static String formatLong(long v)
	{
		return String.format(Locale.US, "%,d", v);
	}
}
