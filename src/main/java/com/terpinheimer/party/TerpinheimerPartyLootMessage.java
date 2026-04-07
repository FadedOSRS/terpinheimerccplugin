package com.terpinheimer.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * WebSocket payload sent to party members when local NPC/player loot is received.
 * Serialized by RuneLite's party Gson using field names; keep keys short.
 */
public class TerpinheimerPartyLootMessage extends PartyMemberMessage
{
	/** Drop source label (NPC name, or player name for PvP loot). */
	private String src;
	/** Parallel arrays of item ids and stack sizes. */
	private int[] ids;
	private int[] qty;
	/** Epoch millis when the drop was recorded. */
	private long t;

	public TerpinheimerPartyLootMessage()
	{
	}

	public TerpinheimerPartyLootMessage(String src, int[] ids, int[] qty, long t)
	{
		this.src = src;
		this.ids = ids;
		this.qty = qty;
		this.t = t;
	}

	public String getSrc()
	{
		return src;
	}

	public void setSrc(String src)
	{
		this.src = src;
	}

	public int[] getIds()
	{
		return ids;
	}

	public void setIds(int[] ids)
	{
		this.ids = ids;
	}

	public int[] getQty()
	{
		return qty;
	}

	public void setQty(int[] qty)
	{
		this.qty = qty;
	}

	public long getT()
	{
		return t;
	}

	public void setT(long t)
	{
		this.t = t;
	}
}
