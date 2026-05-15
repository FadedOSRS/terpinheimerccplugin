package com.terpinheimer.site;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Keeps recent game chat lines that look like collection log notifications, for
 * {@linkplain ClogSitePayloadBuilder website clog / Chronicle sync} on logout.
 */
@Singleton
public class ClogChronicleTracker
{
	private static final int MAX_LINES = 40;

	public static final class Line
	{
		private final long epochMs;
		private final String text;

		public Line(long epochMs, String text)
		{
			this.epochMs = epochMs;
			this.text = text;
		}

		public long getEpochMs()
		{
			return epochMs;
		}

		public String getText()
		{
			return text;
		}
	}

	private final Client client;
	private final ArrayDeque<Line> lines = new ArrayDeque<>();

	@Inject
	ClogChronicleTracker(Client client)
	{
		this.client = client;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (!allowsChatType(event.getType()))
		{
			return;
		}
		String plain = Text.removeTags(event.getMessage());
		if (plain == null || plain.isEmpty())
		{
			return;
		}
		String low = plain.toLowerCase(Locale.ROOT);
		if (!low.contains("collection log") && !low.contains("col log") && !low.contains("new item"))
		{
			return;
		}
		synchronized (lines)
		{
			lines.addLast(new Line(System.currentTimeMillis(), plain));
			while (lines.size() > MAX_LINES)
			{
				lines.removeFirst();
			}
		}
	}

	/** Cleared on hop so chronicle does not leak across worlds in one session file. */
	public void clear()
	{
		synchronized (lines)
		{
			lines.clear();
		}
	}

	public List<Line> snapshotChronicle()
	{
		synchronized (lines)
		{
			return new ArrayList<>(lines);
		}
	}

	private static boolean allowsChatType(ChatMessageType type)
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
