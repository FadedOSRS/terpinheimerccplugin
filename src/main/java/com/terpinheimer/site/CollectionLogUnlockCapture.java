package com.terpinheimer.site;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Incremental collection log unlocks (RuneProfile-style): game chat when a new item is logged.
 */
@Singleton
public final class CollectionLogUnlockCapture
{
	private static final Logger log = LoggerFactory.getLogger(CollectionLogUnlockCapture.class);
	private static final Pattern NEW_ITEM =
		Pattern.compile("(?i)new item added to your collection log:\\s*(.+)");

	private final Client client;
	private final ItemManager itemManager;
	private final CollectionLogItemStore collectionLogItemStore;
	private final RuneProfilePresence runeProfilePresence;

	@Inject
	CollectionLogUnlockCapture(
		Client client,
		ItemManager itemManager,
		CollectionLogItemStore collectionLogItemStore,
		RuneProfilePresence runeProfilePresence)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.collectionLogItemStore = collectionLogItemStore;
		this.runeProfilePresence = runeProfilePresence;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			collectionLogItemStore.reloadForCurrentAccount();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (runeProfilePresence.isEnabled())
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String plain = Text.removeTags(event.getMessage());
		if (plain == null || plain.isEmpty())
		{
			return;
		}
		Matcher m = NEW_ITEM.matcher(plain);
		if (!m.find())
		{
			return;
		}
		String itemName = m.group(1).trim();
		if (itemName.isEmpty() || shouldIgnoreAmbiguousName(itemName))
		{
			return;
		}
		int itemId = resolveItemId(itemName);
		if (itemId <= 0)
		{
			log.debug("Terpinheimer: could not resolve clog unlock item name: {}", itemName);
			return;
		}
		collectionLogItemStore.storeItem(itemId, 1);
		collectionLogItemStore.persistNow();
	}

	private int resolveItemId(String itemName)
	{
		try
		{
			java.util.List<ItemPrice> hits = itemManager.search(itemName);
			if (hits == null || hits.isEmpty())
			{
				return -1;
			}
			return hits.get(0).getId();
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	private static boolean shouldIgnoreAmbiguousName(String itemName)
	{
		String low = itemName.toLowerCase(Locale.ROOT);
		return low.contains(" graceful ") || low.startsWith("graceful ")
			|| low.endsWith(" graceful") || low.equals("graceful");
	}
}
