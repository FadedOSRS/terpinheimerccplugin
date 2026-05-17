package com.terpinheimer.site;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RuneProfile-style passive collection log capture: listen for script {@code 4100} item rows only.
 * Does not run in-game Search, {@code menuAction}, or widget scans (those caused client freezes).
 */
@Singleton
public final class CollectionLogScriptCapture
{
	private static final Logger log = LoggerFactory.getLogger(CollectionLogScriptCapture.class);
	private static final int SCRIPT_COLLECTION_LOG_POPULATE_ITEMS = 4100;

	private final Client client;
	private final CollectionLogItemStore collectionLogItemStore;
	private final CollectionLogUiState collectionLogUiState;
	private final ClogManualSession clogManualSession;

	@Inject
	CollectionLogScriptCapture(
		Client client,
		CollectionLogItemStore collectionLogItemStore,
		CollectionLogUiState collectionLogUiState,
		ClogManualSession clogManualSession)
	{
		this.client = client;
		this.collectionLogItemStore = collectionLogItemStore;
		this.collectionLogUiState = collectionLogUiState;
		this.clogManualSession = clogManualSession;
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (!clogManualSession.isManualPostInProgress())
		{
			return;
		}
		if (event.getScriptId() != SCRIPT_COLLECTION_LOG_POPULATE_ITEMS)
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN
			|| isPohAdventureLogView()
			|| !collectionLogUiState.isCollectionLogOpen(client))
		{
			return;
		}
		try
		{
			Object[] args = event.getScriptEvent().getArguments();
			if (args == null || args.length < 3)
			{
				return;
			}
			int itemId = scriptArgInt(args[1]);
			int quantity = scriptArgInt(args[2]);
			if (itemId <= 0 || quantity <= 0)
			{
				return;
			}
			collectionLogItemStore.storeItem(itemId, quantity);
		}
		catch (Exception e)
		{
			log.debug("Terpinheimer: ignored collection log script row: {}", e.toString());
		}
	}

	private boolean isPohAdventureLogView()
	{
		try
		{
			return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) != 0;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	private static int scriptArgInt(Object arg)
	{
		if (arg instanceof Number)
		{
			return ((Number) arg).intValue();
		}
		return -1;
	}
}
