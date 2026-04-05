package com.terpinheimer.site;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import net.runelite.client.RuneLiteProperties;

/**
 * GETs JSON from your clan site and maps it to <strong>Active</strong>, <strong>Pending</strong>, or
 * <strong>None</strong> for the Home → Events → Website row (manual calendar only, not WOM).
 *
 * <p>Resolution order on a JSON object:
 * <ul>
 *   <li>{@code phase}: {@code active} → Active; {@code pending}, {@code upcoming} → Pending</li>
 *   <li>{@code activeCount} &gt; 0 → Active; else {@code upcomingCount} &gt; 0 → Pending</li>
 *   <li>{@code hasActive} / {@code hasPending} booleans</li>
 *   <li>{@code status} or {@code summary} string — keyword match (see {@link #statusFromPlainText(String)})</li>
 * </ul>
 * JSON strings use the same keyword rules. Unknown → {@code None}.
 *
 * <p>If the root is a JSON <strong>array</strong> of event objects (e.g. {@code ?format=array}),
 * each item may use {@code startsAt}/{@code start} and {@code endsAt}/{@code end} (ISO-8601). Status is
 * derived from whether any event is currently in range or still in the future.
 */
@Singleton
public class ClanCalendarSummaryService
{
	/**
	 * Sent as the {@code Authorization} header on calendar summary GET requests. Not shown in plugin
	 * settings; change here if your server key rotates.
	 */
	public static final String CLAN_CALENDAR_SUMMARY_AUTH_SECRET = "476552589";

	/** One row for the Web Events leaderboard table (Rank / Event / When). */
	public static final class WebEventRow
	{
		private final int rank;
		private final String title;
		private final String when;

		public WebEventRow(int rank, String title, String when)
		{
			this.rank = rank;
			this.title = title != null ? title : "";
			this.when = when != null ? when : "";
		}

		public int getRank()
		{
			return rank;
		}

		public String getTitle()
		{
			return title;
		}

		public String getWhen()
		{
			return when;
		}
	}

	public static final class CalendarParseResult
	{
		private final String status;
		private final String eventsDetailText;
		private final List<WebEventRow> eventRows;

		public CalendarParseResult(String status, String eventsDetailText)
		{
			this(status, eventsDetailText, Collections.emptyList());
		}

		public CalendarParseResult(String status, String eventsDetailText, List<WebEventRow> eventRows)
		{
			this.status = status;
			this.eventsDetailText = eventsDetailText != null ? eventsDetailText : "";
			this.eventRows = eventRows != null ? eventRows : Collections.emptyList();
		}

		public String getStatus()
		{
			return status;
		}

		public String getEventsDetailText()
		{
			return eventsDetailText;
		}

		public List<WebEventRow> getEventRows()
		{
			return eventRows;
		}
	}

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	ClanCalendarSummaryService(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	public CalendarParseResult fetchCalendar(String httpsUrl, String authorizationSecret) throws IOException
	{
		String rl = RuneLiteProperties.getVersion();
		String ua = "Terpinheimer/2.0.0 RuneLite/" + (rl != null ? rl : "unknown");
		Request.Builder rb = new Request.Builder()
			.url(httpsUrl)
			.header("User-Agent", ua);
		if (authorizationSecret != null && !authorizationSecret.trim().isEmpty())
		{
			rb.header("Authorization", authorizationSecret.trim());
		}
		Request req = rb.get().build();
		try (Response res = http.newCall(req).execute())
		{
			ResponseBody b = res.body();
			String body = b != null ? b.string() : "";
			if (!res.isSuccessful())
			{
				throw new IOException("HTTP " + res.code());
			}
			return parseCalendarBody(gson, body);
		}
	}

	static String mapBodyToWebsiteStatus(Gson gson, String body)
	{
		return parseCalendarBody(gson, body).getStatus();
	}

	static CalendarParseResult parseCalendarBody(Gson gson, String body)
	{
		if (body == null)
		{
			return new CalendarParseResult("None", "");
		}
		String trimmed = body.trim();
		if (trimmed.isEmpty())
		{
			return new CalendarParseResult("None", "");
		}
		JsonElement root;
		try
		{
			root = gson.fromJson(trimmed, JsonElement.class);
			if (root == null)
			{
				return new CalendarParseResult(statusFromPlainText(trimmed), "");
			}
		}
		catch (Exception e)
		{
			return new CalendarParseResult(statusFromPlainText(trimmed), "");
		}
		if (root.isJsonArray())
		{
			return fromEventArray(root.getAsJsonArray());
		}
		if (root.isJsonObject())
		{
			JsonObject o = root.getAsJsonObject();
			if (o.has("events") && o.get("events").isJsonArray())
			{
				return fromEventArray(o.getAsJsonArray("events"));
			}
			return new CalendarParseResult(mapObjectToWebsiteStatus(o), "");
		}
		if (root.isJsonPrimitive() && root.getAsJsonPrimitive().isString())
		{
			return new CalendarParseResult(statusFromPlainText(root.getAsString()), "");
		}
		return new CalendarParseResult(statusFromPlainText(trimmed), "");
	}

	private enum CalPhase
	{
		LIVE, UPCOMING, PAST
	}

	private static final class CalEv
	{
		final Instant start;
		final Instant end;
		final String title;
		final CalPhase phase;

		CalEv(Instant start, Instant end, String title, CalPhase phase)
		{
			this.start = start;
			this.end = end;
			this.title = title;
			this.phase = phase;
		}
	}

	private static CalendarParseResult fromEventArray(JsonArray arr)
	{
		Instant now = Instant.now();
		List<CalEv> events = new ArrayList<>();
		DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
			.withZone(ZoneId.systemDefault());

		for (JsonElement el : arr)
		{
			if (el == null || !el.isJsonObject())
			{
				continue;
			}
			JsonObject ev = el.getAsJsonObject();
			Instant start = parseInstant(ev, "startsAt", "start");
			Instant end = parseInstant(ev, "endsAt", "end");
			if (start == null)
			{
				continue;
			}
			if (end == null || !end.isAfter(start))
			{
				end = start.plus(1, ChronoUnit.HOURS);
			}
			String title = strOrNull(ev, "title");
			if (title == null || title.isEmpty())
			{
				title = strOrNull(ev, "name");
			}
			if (title == null || title.isEmpty())
			{
				title = "Event";
			}

			CalPhase phase;
			if (!now.isBefore(start) && now.isBefore(end))
			{
				phase = CalPhase.LIVE;
			}
			else if (now.isBefore(start))
			{
				phase = CalPhase.UPCOMING;
			}
			else
			{
				phase = CalPhase.PAST;
			}
			events.add(new CalEv(start, end, title, phase));
		}

		events.sort((a, b) ->
		{
			int oa = a.phase == CalPhase.LIVE ? 0 : a.phase == CalPhase.UPCOMING ? 1 : 2;
			int ob = b.phase == CalPhase.LIVE ? 0 : b.phase == CalPhase.UPCOMING ? 1 : 2;
			if (oa != ob)
			{
				return Integer.compare(oa, ob);
			}
			if (a.phase == CalPhase.LIVE)
			{
				return a.end.compareTo(b.end);
			}
			if (a.phase == CalPhase.UPCOMING)
			{
				return a.start.compareTo(b.start);
			}
			return b.start.compareTo(a.start);
		});

		int active = 0;
		int upcoming = 0;
		StringBuilder detail = new StringBuilder();
		List<WebEventRow> rows = new ArrayList<>();
		final int maxRows = 20;
		int r = 1;
		for (CalEv e : events)
		{
			if (e.phase == CalPhase.LIVE)
			{
				active++;
				detail.append("• ").append(e.title).append(" — live now\n");
			}
			else if (e.phase == CalPhase.UPCOMING)
			{
				upcoming++;
				detail.append("• ").append(e.title).append(" — ").append(fmt.format(e.start)).append('\n');
			}

			if (r <= maxRows)
			{
				String whenCol;
				if (e.phase == CalPhase.LIVE)
				{
					whenCol = "Live now";
				}
				else if (e.phase == CalPhase.UPCOMING)
				{
					whenCol = fmt.format(e.start);
				}
				else
				{
					whenCol = "Ended";
				}
				rows.add(new WebEventRow(r, e.title, whenCol));
				r++;
			}
		}

		String status;
		if (active > 0)
		{
			status = "Active";
		}
		else if (upcoming > 0)
		{
			status = "Pending";
		}
		else
		{
			status = "None";
		}

		String detailStr = detail.toString().trim();
		if (detailStr.isEmpty() && arr.size() > 0 && active == 0 && upcoming == 0)
		{
			detailStr = "No active or upcoming events (all start times are in the past).";
		}
		return new CalendarParseResult(status, detailStr, Collections.unmodifiableList(rows));
	}

	private static Instant parseInstant(JsonObject o, String... keys)
	{
		for (String key : keys)
		{
			if (!o.has(key) || o.get(key).isJsonNull())
			{
				continue;
			}
			try
			{
				return Instant.parse(o.get(key).getAsString());
			}
			catch (Exception ignored)
			{
			}
		}
		return null;
	}

	private static String mapObjectToWebsiteStatus(JsonObject o)
	{
		String phase = strOrNull(o, "phase");
		if (phase != null)
		{
			String fromPhase = statusFromPlainText(phase);
			if (!"None".equals(fromPhase))
			{
				return fromPhase;
			}
		}

		int active = intOrNeg1(o, "activeCount");
		int upcoming = intOrNeg1(o, "upcomingCount");
		if (active > 0)
		{
			return "Active";
		}
		if (upcoming > 0)
		{
			return "Pending";
		}

		if (boolOrFalse(o, "hasActive"))
		{
			return "Active";
		}
		if (boolOrFalse(o, "hasPending"))
		{
			return "Pending";
		}

		if (o.has("status") && !o.get("status").isJsonNull())
		{
			try
			{
				String s = statusFromPlainText(o.get("status").getAsString());
				if (!"None".equals(s))
				{
					return s;
				}
			}
			catch (Exception ignored)
			{
			}
		}
		if (o.has("summary") && !o.get("summary").isJsonNull())
		{
			try
			{
				String s = statusFromPlainText(o.get("summary").getAsString());
				if (!"None".equals(s))
				{
					return s;
				}
			}
			catch (Exception ignored)
			{
			}
		}

		return "None";
	}

	/**
	 * Maps free text to Active / Pending / None using simple English keywords.
	 */
	static String statusFromPlainText(String text)
	{
		if (text == null)
		{
			return "None";
		}
		String t = text.toLowerCase(Locale.ROOT);
		if (t.isEmpty())
		{
			return "None";
		}
		if (t.contains("inactive") || t.contains("not active"))
		{
			return "None";
		}
		if (t.contains("active") || t.contains("live") || t.contains("in progress") || t.contains("ongoing"))
		{
			return "Active";
		}
		if (t.contains("pending") || t.contains("upcoming") || t.contains("scheduled") || t.contains("soon"))
		{
			return "Pending";
		}
		return "None";
	}

	private static int intOrNeg1(JsonObject o, String key)
	{
		if (!o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive())
		{
			return -1;
		}
		try
		{
			return o.get(key).getAsInt();
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	private static String strOrNull(JsonObject o, String key)
	{
		if (!o.has(key) || o.get(key).isJsonNull())
		{
			return null;
		}
		try
		{
			return o.get(key).getAsString();
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private static boolean boolOrFalse(JsonObject o, String key)
	{
		if (!o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive())
		{
			return false;
		}
		try
		{
			return o.get(key).getAsBoolean();
		}
		catch (Exception e)
		{
			return false;
		}
	}
}
