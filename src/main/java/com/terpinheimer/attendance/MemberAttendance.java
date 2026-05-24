/*
 * BSD 2-Clause License — adapted from Clan Event Attendance
 * Copyright (c) 2021, Jonathan Rousseau
 * https://github.com/JoRouss/runelite-ClanEventAttendance
 */
package com.terpinheimer.attendance;

import net.runelite.api.Player;

final class MemberAttendance
{
	final Player member;
	/** Cached at first track; used for site POST after despawn. */
	final String displayName;
	int ticksLate;
	int tickActivityStarted;
	int ticksTotal;
	boolean isPresent;
	/** Wall-clock first time this member entered the event area. */
	long firstJoinedAtEpochMs;
	/** Wall-clock last time they left the area; 0 while still present. */
	long lastLeftAtEpochMs;

	MemberAttendance(Player member, String displayName, int ticksLate, int tickActivityStarted, int ticksTotal, boolean isPresent)
	{
		this.member = member;
		this.displayName = displayName;
		this.ticksLate = ticksLate;
		this.tickActivityStarted = tickActivityStarted;
		this.ticksTotal = ticksTotal;
		this.isPresent = isPresent;
		this.firstJoinedAtEpochMs = 0L;
		this.lastLeftAtEpochMs = 0L;
	}
}
