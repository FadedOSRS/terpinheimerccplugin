/*
 * BSD 2-Clause License — adapted from Clan Event Attendance (JoRouss, 2021).
 * https://github.com/JoRouss/runelite-ClanEventAttendance
 * Simplified: Jagex clan chat only (no friends chat). Plain-text report for Terpinheimer.
 */
package com.terpinheimer.attendance;

import com.terpinheimer.TerpinheimerConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@Singleton
public class ClanAttendanceTracker
{
	private final Client client;
	private final TerpinheimerConfig config;

	private volatile Runnable uiRefresh;

	private int eventStartedAt;
	private int eventStoppedAt;
	private volatile boolean eventRunning;

	private final Map<String, MemberAttendance> attendanceBuffer = new TreeMap<>();
	private final Set<String> clanMemberKeys = new HashSet<>();

	private int scanDelay = -1;

	private volatile String currentReport = "";

	@Inject
	ClanAttendanceTracker(Client client, TerpinheimerConfig config)
	{
		this.client = client;
		this.config = config;
	}

	public void setUiRefresh(Runnable r)
	{
		this.uiRefresh = r;
	}

	public void clearUiRefresh()
	{
		this.uiRefresh = null;
	}

	public boolean isEventRunning()
	{
		return eventRunning;
	}

	public String getCurrentReport()
	{
		return currentReport;
	}

	private void notifyUi()
	{
		Runnable r = uiRefresh;
		if (r != null)
		{
			SwingUtilities.invokeLater(r);
		}
	}

	public void startEvent()
	{
		attendanceBuffer.clear();
		clanMemberKeys.clear();
		eventStartedAt = client.getTickCount();
		eventRunning = true;
		scanDelay = 1;
		rebuildReport(false);
		notifyUi();
	}

	public void stopEvent()
	{
		for (String key : new ArrayList<>(attendanceBuffer.keySet()))
		{
			compileTicks(key);
		}
		eventStoppedAt = client.getTickCount();
		eventRunning = false;
		rebuildReport(true);
		notifyUi();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gs = event.getGameState();
		if (gs == GameState.HOPPING || gs == GameState.LOGIN_SCREEN)
		{
			scanDelay = 1;
		}
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		clanMemberKeys.clear();
		if (event.getClanChannel() == null)
		{
			return;
		}
		scanDelay = 1;
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (!eventRunning)
		{
			return;
		}
		Player player = event.getPlayer();
		if (!isTrackedPlayer(player))
		{
			return;
		}
		addPlayer(player);
		unpausePlayer(player.getName());
		notifyUi();
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		if (!eventRunning)
		{
			return;
		}
		Player player = event.getPlayer();
		String playerKey = nameKey(player.getName());
		if (!attendanceBuffer.containsKey(playerKey))
		{
			return;
		}
		compileTicks(player.getName());
		pausePlayer(player.getName());
		notifyUi();
	}

	@Subscribe
	public void onClanMemberJoined(ClanMemberJoined event)
	{
		if (!eventRunning)
		{
			return;
		}
		ClanChannelMember member = event.getClanMember();
		clanMemberKeys.add(nameKey(member.getName()));
		if (member.getWorld() != client.getWorld())
		{
			return;
		}
		String memberName = member.getName();
		for (Player p : client.getPlayers())
		{
			if (p == null)
			{
				continue;
			}
			if (nameKey(memberName).equals(nameKey(p.getName())))
			{
				addPlayer(p);
				unpausePlayer(p.getName());
				notifyUi();
				break;
			}
		}
	}

	@Subscribe
	public void onClanMemberLeft(ClanMemberLeft event)
	{
		if (!eventRunning)
		{
			return;
		}
		ClanChannelMember member = event.getClanMember();
		clanMemberKeys.remove(nameKey(member.getName()));
		if (member.getWorld() != client.getWorld())
		{
			return;
		}
		String playerKey = nameKey(member.getName());
		if (!attendanceBuffer.containsKey(playerKey))
		{
			return;
		}
		compileTicks(member.getName());
		pausePlayer(member.getName());
		notifyUi();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!eventRunning)
		{
			return;
		}

		if (scanDelay == 0)
		{
			clanMemberKeys.clear();
			if (client.getClanChannel() != null)
			{
				for (ClanChannelMember m : client.getClanChannel().getMembers())
				{
					clanMemberKeys.add(nameKey(m.getName()));
				}
			}
			for (Player player : client.getPlayers())
			{
				if (player == null || !isTrackedPlayer(player))
				{
					continue;
				}
				addPlayer(player);
				unpausePlayer(player.getName());
			}
		}
		if (scanDelay >= 0)
		{
			scanDelay--;
		}

		for (String key : new ArrayList<>(attendanceBuffer.keySet()))
		{
			compileTicks(key);
		}

		rebuildReport(false);
		notifyUi();
	}

	private boolean isTrackedPlayer(Player player)
	{
		if (player == null)
		{
			return false;
		}
		if (player.isClanMember())
		{
			return true;
		}
		return clanMemberKeys.contains(nameKey(player.getName()));
	}

	private void addPlayer(Player player)
	{
		String playerKey = nameKey(player.getName());
		if (!attendanceBuffer.containsKey(playerKey))
		{
			MemberAttendance ma = new MemberAttendance(
				player,
				client.getTickCount() - eventStartedAt,
				client.getTickCount(),
				0,
				false);
			attendanceBuffer.put(playerKey, ma);
		}
	}

	private void pausePlayer(String playerName)
	{
		String playerKey = nameKey(playerName);
		MemberAttendance ma = attendanceBuffer.get(playerKey);
		if (ma == null)
		{
			return;
		}
		ma.isPresent = false;
	}

	private void unpausePlayer(String playerName)
	{
		String playerKey = nameKey(playerName);
		MemberAttendance ma = attendanceBuffer.get(playerKey);
		if (ma == null)
		{
			return;
		}
		if (ma.isPresent)
		{
			return;
		}
		ma.isPresent = true;
		ma.tickActivityStarted = client.getTickCount();
	}

	private void compileTicks(String playerName)
	{
		String playerKey = nameKey(playerName);
		MemberAttendance ma = attendanceBuffer.get(playerKey);
		if (ma == null || !ma.isPresent)
		{
			return;
		}
		ma.ticksTotal += client.getTickCount() - ma.tickActivityStarted;
		ma.tickActivityStarted = client.getTickCount();
	}

	private void rebuildReport(boolean finalDisplay)
	{
		StringBuilder active = new StringBuilder();
		StringBuilder inactive = new StringBuilder();
		int presentThresholdSec = config.attendancePresentThresholdSeconds();
		int lateThresholdSec = config.attendanceLateThresholdSeconds();
		boolean lateCol = config.attendanceShowLateColumn();

		for (Map.Entry<String, MemberAttendance> e : attendanceBuffer.entrySet())
		{
			MemberAttendance ma = e.getValue();
			int secs = ticksToSeconds(ma.ticksTotal);
			String line = memberLine(ma, lateCol, lateThresholdSec);
			if (secs < presentThresholdSec)
			{
				inactive.append(line).append('\n');
			}
			else
			{
				active.append(line).append('\n');
			}
		}

		StringBuilder out = new StringBuilder();
		out.append("Event duration: ");
		int endTick = eventRunning ? client.getTickCount() : eventStoppedAt;
		out.append(timeFormat(ticksToSeconds(endTick - eventStartedAt)));
		out.append("\n\n");

		if (finalDisplay && config.attendanceDiscordCodeFence())
		{
			out.append("```\n");
		}

		if (active.length() > 0)
		{
			out.append("Present members\n");
			out.append("------------------------------\n");
			if (lateCol)
			{
				out.append(String.format("%-12s | %-6s | %-6s%n", "Name", "Time", "Late"));
			}
			else
			{
				out.append(String.format("%-12s | %-6s%n", "Name", "Time"));
			}
			out.append(active);
		}
		if (inactive.length() > 0)
		{
			if (active.length() > 0)
			{
				out.append('\n');
			}
			out.append("Below threshold (");
			out.append(timeFormat(presentThresholdSec));
			out.append(")\n");
			out.append("------------------------------\n");
			if (lateCol)
			{
				out.append(String.format("%-12s | %-6s | %-6s%n", "Name", "Time", "Late"));
			}
			else
			{
				out.append(String.format("%-12s | %-6s%n", "Name", "Time"));
			}
			out.append(inactive);
		}

		if (finalDisplay && config.attendanceDiscordCodeFence())
		{
			out.append("```\n");
		}

		currentReport = out.toString();
	}

	private String memberLine(MemberAttendance ma, boolean lateCol, int lateThresholdSec)
	{
		String name = Text.removeTags(ma.member.getName());
		if (name.length() > 12)
		{
			name = name.substring(0, 11) + "…";
		}
		String timeStr = timeFormat(ticksToSeconds(ma.ticksTotal));
		if (lateCol)
		{
			boolean late = ticksToSeconds(ma.ticksLate) > lateThresholdSec;
			String lateStr = late ? timeFormat(ticksToSeconds(ma.ticksLate)) : "-";
			return String.format("%-12s | %-6s | %-6s", name, timeStr, lateStr);
		}
		return String.format("%-12s | %-6s", name, timeStr);
	}

	private static String timeFormat(int totalSeconds)
	{
		long minute = TimeUnit.SECONDS.toMinutes(totalSeconds);
		long second = TimeUnit.SECONDS.toSeconds(totalSeconds) - (TimeUnit.SECONDS.toMinutes(totalSeconds) * 60);
		if (minute > 99)
		{
			return String.format("%03d:%02d", minute, second);
		}
		return String.format("%02d:%02d", minute, second);
	}

	private static int ticksToSeconds(int ticks)
	{
		return (int) (ticks * 0.6f);
	}

	private static String nameKey(String playerName)
	{
		return Text.standardize(Text.removeTags(playerName)).toLowerCase();
	}
}
