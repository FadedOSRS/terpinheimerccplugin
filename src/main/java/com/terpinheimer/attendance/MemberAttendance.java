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
	int ticksLate;
	int tickActivityStarted;
	int ticksTotal;
	boolean isPresent;

	MemberAttendance(Player member, int ticksLate, int tickActivityStarted, int ticksTotal, boolean isPresent)
	{
		this.member = member;
		this.ticksLate = ticksLate;
		this.tickActivityStarted = tickActivityStarted;
		this.ticksTotal = ticksTotal;
		this.isPresent = isPresent;
	}
}
