package com.terpinheimer.clan;

import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;

/**
 * Resolves the in-game rank label shown for a clan member (custom clan title when set, otherwise a
 * readable built-in Jagex rank name). Used for roster JSON and for matching website permission lists.
 */
public final class ClanMemberRankDisplay
{
	private ClanMemberRankDisplay()
	{
	}

	/**
	 * @return display rank title, or {@code null} when the member has no rank
	 */
	public static String forMember(ClanSettings settings, ClanMember member)
	{
		if (member == null)
		{
			return null;
		}
		ClanRank rank = member.getRank();
		if (rank == null)
		{
			return null;
		}
		if (settings != null)
		{
			ClanTitle title = settings.titleForRank(rank);
			if (title != null && title.getName() != null && !title.getName().isEmpty())
			{
				return title.getName().trim();
			}
		}
		return readableJagexRankName(rank);
	}

	public static String readableJagexRankName(ClanRank rank)
	{
		if (rank.equals(ClanRank.OWNER))
		{
			return "Owner";
		}
		if (rank.equals(ClanRank.DEPUTY_OWNER))
		{
			return "Deputy Owner";
		}
		if (rank.equals(ClanRank.ADMINISTRATOR))
		{
			return "Administrator";
		}
		if (rank.equals(ClanRank.GUEST))
		{
			return "Guest";
		}
		if (rank.equals(ClanRank.JMOD))
		{
			return "Jagex Moderator";
		}
		return "Clan rank slot " + rank.getRank();
	}
}
