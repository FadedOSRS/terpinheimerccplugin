package com.terpinheimer.party;

import java.util.List;

/**
 * One row in the party loot log: a single item stack (one boss kill can create several rows).
 */
public final class PartyLootRow
{
	private final long id;
	private final long epochMillis;
	private final String player;
	private final String source;
	private final List<String> lootLines;
	private final long valueGp;

	public PartyLootRow(long id, long epochMillis, String player, String source, List<String> lootLines, long valueGp)
	{
		this.id = id;
		this.epochMillis = epochMillis;
		this.player = player;
		this.source = source;
		this.lootLines = lootLines;
		this.valueGp = valueGp;
	}

	public long getId()
	{
		return id;
	}

	public long getEpochMillis()
	{
		return epochMillis;
	}

	public String getPlayer()
	{
		return player;
	}

	public String getSource()
	{
		return source;
	}

	public List<String> getLootLines()
	{
		return lootLines;
	}

	/** Sum of GE guide prices × quantity for stacks in this row. */
	public long getValueGp()
	{
		return valueGp;
	}

	/** Drop column: item name for this stack (quantities are reflected in {@link #getValueGp()}). */
	public String getDropSummary()
	{
		if (lootLines.isEmpty())
		{
			return "—";
		}
		return escapeHtml(stackLineToItemName(lootLines.get(0)));
	}

	private static String stackLineToItemName(String line)
	{
		if (line == null || line.isEmpty())
		{
			return "";
		}
		int x = line.indexOf(" x ");
		if (x < 0)
		{
			return line;
		}
		return line.substring(x + 3);
	}

	private static String escapeHtml(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
