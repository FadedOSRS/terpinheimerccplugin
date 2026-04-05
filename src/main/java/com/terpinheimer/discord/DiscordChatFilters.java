package com.terpinheimer.discord;

import net.runelite.api.ChatMessageType;

final class DiscordChatFilters
{
	private DiscordChatFilters()
	{
	}

	static boolean allows(ChatMessageType type)
	{
		if (type == null)
		{
			return false;
		}
		switch (type)
		{
			case GAMEMESSAGE:
			case ENGINE:
			case MESBOX:
			case DIALOG:
				return true;
			default:
				return false;
		}
	}
}
