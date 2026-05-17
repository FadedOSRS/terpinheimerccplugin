package com.terpinheimer.wom;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import net.runelite.client.RuneLiteProperties;

@Singleton
public class WomCompetitionService
{
	private static final String API = "https://api.wiseoldman.net/v2";
	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	public WomCompetitionService(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/**
	 * Resolve competition id, then fetch full leaderboard.
	 * <p>
	 * When {@code autoPick} is true and {@code configuredId} is 0: first tries the configured
	 * metric, then falls back to competitions whose title looks like Skill of the Week (when
	 * {@code rotatingEventIsBossWeek} is false) or Boss of the Week (when true).
	 */
	public WomLeaderboardModels.CompetitionSnapshot fetchSnapshot(
		int groupId, int configuredId, boolean autoPick, String metric,
		String fallbackStartIso, String fallbackEndIso,
		boolean rotatingEventIsBossWeek) throws IOException
	{
		long now = System.currentTimeMillis();
		long resolvedId = configuredId;
		if (resolvedId <= 0 && autoPick && groupId > 0)
		{
			List<WomJson.CompetitionSummary> list = fetchGroupCompetitions(groupId);
			if (list != null && !list.isEmpty())
			{
				if (metric != null && !metric.isEmpty())
				{
					resolvedId = pickFromMatchList(filterSummaries(list, c -> metricEquals(c, metric)));
				}
				if (resolvedId <= 0)
				{
					resolvedId = pickFromMatchList(
						filterSummaries(list, c -> titleMatchesRotatingEvent(c.title, rotatingEventIsBossWeek)));
				}
			}
		}
		if (resolvedId <= 0)
		{
			return buildFallbackSnapshot(fallbackStartIso, fallbackEndIso,
				"Set your WOM group ID in plugin settings, or add a group competition whose title includes SOTW / Skill of the Week or BOTW / Boss of the Week.",
				now);
		}

		WomJson.CompetitionDetails details = fetchCompetitionDetails(resolvedId);
		if (details == null)
		{
			return WomLeaderboardModels.CompetitionSnapshot.error("Data unavailable (empty response)");
		}

		Instant start = parseIso(details.startsAt);
		Instant end = parseIso(details.endsAt);
		Instant nowInst = Instant.now();
		WomLeaderboardModels.EventPhase phase = WomLeaderboardModels.phaseFor(start, end, nowInst);
		List<WomLeaderboardModels.LeaderRow> top = WomLeaderboardModels.topNFromParticipations(details.participations, 10);
		WomLeaderboardModels.LeaderRow winner = null;
		if (phase == WomLeaderboardModels.EventPhase.FINISHED && !top.isEmpty())
		{
			winner = top.get(0);
		}

		String countdown = WomLeaderboardModels.formatCountdown(start, end, nowInst, phase);
		return WomLeaderboardModels.CompetitionSnapshot.ok(
			details.id,
			details.title != null ? details.title : "",
			details.metric != null ? details.metric : "",
			start,
			end,
			phase,
			top,
			winner,
			now,
			countdown
		);
	}

	private WomLeaderboardModels.CompetitionSnapshot buildFallbackSnapshot(String startIso, String endIso, String hint, long nowMs)
	{
		Instant start = parseIsoLenient(startIso);
		Instant end = parseIsoLenient(endIso);
		if (start != null && end != null)
		{
			WomLeaderboardModels.EventPhase ph = WomLeaderboardModels.phaseFor(start, end, Instant.now());
			String cd = WomLeaderboardModels.formatCountdown(start, end, Instant.now(), ph);
			return WomLeaderboardModels.CompetitionSnapshot.ok(0L, "(not linked)", "", start, end, ph,
				java.util.Collections.emptyList(), null, nowMs, cd);
		}
		return WomLeaderboardModels.CompetitionSnapshot.empty(hint);
	}

	private static boolean metricEquals(WomJson.CompetitionSummary c, String metric)
	{
		if (metric == null || metric.isEmpty() || c.metric == null)
		{
			return false;
		}
		return c.metric.toLowerCase(Locale.ROOT).equals(metric.toLowerCase(Locale.ROOT));
	}

	/**
	 * Matches common clan naming: SOTW / Skill of the Week, BOTW / Boss of the Week (legacy BOM).
	 */
	private static boolean titleMatchesRotatingEvent(String title, boolean bossWeek)
	{
		if (title == null)
		{
			return false;
		}
		String t = title.toLowerCase(Locale.ROOT);
		if (bossWeek)
		{
			return t.contains("botw") || t.contains("boss of the week")
				|| t.contains("boss of the month") || t.contains("botm");
		}
		return t.contains("sotw") || t.contains("skill of the week");
	}

	private static List<WomJson.CompetitionSummary> filterSummaries(
		List<WomJson.CompetitionSummary> list, Predicate<WomJson.CompetitionSummary> pred)
	{
		List<WomJson.CompetitionSummary> out = new ArrayList<>();
		for (WomJson.CompetitionSummary c : list)
		{
			if (pred.test(c))
			{
				out.add(c);
			}
		}
		return out;
	}

	private long pickFromMatchList(List<WomJson.CompetitionSummary> match)
	{
		if (match.isEmpty())
		{
			return 0L;
		}
		Instant now = Instant.now();
		for (WomJson.CompetitionSummary c : match)
		{
			Instant s = parseIso(c.startsAt);
			Instant e = parseIso(c.endsAt);
			if (s != null && e != null && !now.isBefore(s) && !now.isAfter(e))
			{
				return c.id;
			}
		}
		List<WomJson.CompetitionSummary> upcoming = new ArrayList<>();
		for (WomJson.CompetitionSummary c : match)
		{
			Instant s = parseIso(c.startsAt);
			if (s != null && now.isBefore(s))
			{
				upcoming.add(c);
			}
		}
		if (!upcoming.isEmpty())
		{
			upcoming.sort(Comparator.comparing(c -> parseIso(c.startsAt), Comparator.nullsLast(Comparator.naturalOrder())));
			return upcoming.get(0).id;
		}
		match.sort(Comparator.comparing(c -> parseIso(c.endsAt), Comparator.nullsLast(Comparator.reverseOrder())));
		return match.get(0).id;
	}

	private List<WomJson.CompetitionSummary> fetchGroupCompetitions(int groupId) throws IOException
	{
		HttpUrl url = HttpUrl.parse(API + "/groups/" + groupId + "/competitions").newBuilder()
			.addQueryParameter("limit", "100")
			.build();
		String rl = RuneLiteProperties.getVersion();
		String ua = "Terpinheimer/2.0.0 RuneLite/" + (rl != null ? rl : "unknown");
		Request req = new Request.Builder().url(url)
			.header("Accept", "application/json")
			.header("User-Agent", ua)
			.build();
		try (Response res = http.newCall(req).execute())
		{
			if (!res.isSuccessful())
			{
				return null;
			}
			ResponseBody body = res.body();
			if (body == null)
			{
				return null;
			}
			try
			{
				WomJson.CompetitionSummary[] arr = gson.fromJson(body.charStream(), WomJson.CompetitionSummary[].class);
				if (arr == null)
				{
					return null;
				}
				return Arrays.asList(arr);
			}
			catch (JsonSyntaxException e)
			{
				return null;
			}
		}
	}

	private WomJson.CompetitionDetails fetchCompetitionDetails(long id) throws IOException
	{
		HttpUrl url = HttpUrl.parse(API + "/competitions/" + id);
		String rl = RuneLiteProperties.getVersion();
		String ua = "Terpinheimer/2.0.0 RuneLite/" + (rl != null ? rl : "unknown");
		Request req = new Request.Builder().url(url)
			.header("Accept", "application/json")
			.header("User-Agent", ua)
			.build();
		try (Response res = http.newCall(req).execute())
		{
			if (!res.isSuccessful())
			{
				return null;
			}
			ResponseBody body = res.body();
			if (body == null)
			{
				return null;
			}
			try
			{
				return gson.fromJson(body.charStream(), WomJson.CompetitionDetails.class);
			}
			catch (JsonSyntaxException e)
			{
				return null;
			}
		}
	}

	private static Instant parseIso(String iso)
	{
		if (iso == null || iso.isEmpty())
		{
			return null;
		}
		try
		{
			return Instant.parse(iso);
		}
		catch (DateTimeParseException e)
		{
			return null;
		}
	}

	private static Instant parseIsoLenient(String iso)
	{
		return parseIso(iso);
	}

}
