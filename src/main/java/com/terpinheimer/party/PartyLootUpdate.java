package com.terpinheimer.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * WebSocket payload broadcast when local NPC/player loot is received.
 * Public fields for RuneLite party Gson (same pattern as {@code LocationUpdate}).
 */
public class PartyLootUpdate extends PartyMemberMessage
{
	/** Display name of the player who received the drop. */
	public String who;
	/** Drop source label (NPC name, or player name for PvP loot). */
	public String src;
	/** Parallel arrays of item ids and stack sizes. */
	public int[] ids;
	public int[] qty;
	/** Epoch millis when the drop was recorded. */
	public long t;

	public PartyLootUpdate()
	{
	}

	public PartyLootUpdate(String who, String src, int[] ids, int[] qty, long t)
	{
		this.who = who;
		this.src = src;
		this.ids = ids;
		this.qty = qty;
		this.t = t;
	}
}
