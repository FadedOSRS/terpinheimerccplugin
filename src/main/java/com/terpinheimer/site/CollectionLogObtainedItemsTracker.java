package com.terpinheimer.site;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Passive collection log capture (RuneProfile-style): script 4100 rows + optional UI scan; no auto-Search on open.
 */
@Singleton
public final class CollectionLogObtainedItemsTracker
{
	private static final Logger log = LoggerFactory.getLogger(CollectionLogObtainedItemsTracker.class);

	/** Pre-fired per slot when collection log search results are built (same id RuneProfile uses). */
	private static final int SCRIPT_COLLECTION_LOG_POPULATE_ITEMS = 4100;
	private static final int SCRIPT_COLLECTION_LOG_SEARCH = 2240;

	private final Client client;
	private final ClientThread clientThread;
	private final CollectionLogItemStore collectionLogItemStore;
	private final com.terpinheimer.TerpinheimerConfig config;
	private volatile boolean exportSearchInFlight;

	@Inject
	CollectionLogObtainedItemsTracker(
		Client client,
		ClientThread clientThread,
		CollectionLogItemStore collectionLogItemStore,
		com.terpinheimer.TerpinheimerConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.collectionLogItemStore = collectionLogItemStore;
		this.config = config;
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
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION_LOG)
		{
			clientThread.invokeLater(() -> scanOpenCollectionLogPage(client));
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != SCRIPT_COLLECTION_LOG_POPULATE_ITEMS)
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN || isBlockedCollectionLogContext())
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
			log.debug("Terpinheimer: ignored collection log script item row: {}", e.toString());
		}
	}

	public void runBeforeSyncExport(Runnable onReady)
	{
		if (onReady == null)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				onReady.run();
				return;
			}
			scanOpenCollectionLogPage(client);
			boolean triggered = false;
			if (config.clogRunSearchOnManualSync()
				&& client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS) != null
				&& !isBlockedCollectionLogContext()
				&& !exportSearchInFlight)
			{
				triggered = triggerFullCollectionLogSearch();
			}
			if (!triggered)
			{
				onReady.run();
				return;
			}
			scheduleAfterTicks(4, onReady);
		});
	}

	public int scanOpenCollectionLogPage(Client client)
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return 0;
		}
		Widget itemsContainer = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
		if (itemsContainer == null)
		{
			return 0;
		}
		Widget[] widgetItems = itemsContainer.getDynamicChildren();
		if (widgetItems == null || widgetItems.length == 0)
		{
			return 0;
		}
		int added = 0;
		for (Widget widgetItem : widgetItems)
		{
			if (widgetItem == null)
			{
				continue;
			}
			int itemId = widgetItem.getItemId();
			if (itemId <= 0 || !isWidgetSlotObtained(widgetItem))
			{
				continue;
			}
			int qty = Math.max(1, widgetItem.getItemQuantity());
			int before = collectionLogItemStore.size();
			collectionLogItemStore.storeItem(itemId, qty);
			if (collectionLogItemStore.size() > before)
			{
				added++;
			}
		}
		return added;
	}

	private boolean triggerFullCollectionLogSearch()
	{
		if (exportSearchInFlight)
		{
			return false;
		}
		int searchPackedId = findSearchWidgetPackedId();
		if (searchPackedId < 0)
		{
			return false;
		}
		exportSearchInFlight = true;
		try
		{
			client.menuAction(-1, searchPackedId, MenuAction.CC_OP, 1, -1, "Search", "");
			client.runScript(SCRIPT_COLLECTION_LOG_SEARCH);
			return true;
		}
		catch (Exception e)
		{
			log.warn("Terpinheimer: collection log Search failed: {}", e.toString());
			return false;
		}
		finally
		{
			clientThread.invokeLater(() -> exportSearchInFlight = false);
		}
	}

	private int findSearchWidgetPackedId()
	{
		Widget container = client.getWidget(ComponentID.COLLECTION_LOG_CONTAINER);
		int id = findWidgetWithActionRecursive(container, "Search");
		if (id >= 0)
		{
			return id;
		}
		Widget root = client.getWidget(InterfaceID.COLLECTION_LOG, 0);
		return findWidgetWithActionRecursive(root, "Search");
	}

	private static int findWidgetWithActionRecursive(Widget widget, String action)
	{
		if (widget == null || action == null || action.isEmpty())
		{
			return -1;
		}
		String[] actions = widget.getActions();
		if (actions != null)
		{
			for (String a : actions)
			{
				if (action.equalsIgnoreCase(a))
				{
					return widget.getId();
				}
			}
		}
		Widget[] staticChildren = widget.getStaticChildren();
		if (staticChildren != null)
		{
			for (Widget child : staticChildren)
			{
				int found = findWidgetWithActionRecursive(child, action);
				if (found >= 0)
				{
					return found;
				}
			}
		}
		Widget[] dynamicChildren = widget.getDynamicChildren();
		if (dynamicChildren != null)
		{
			for (Widget child : dynamicChildren)
			{
				int found = findWidgetWithActionRecursive(child, action);
				if (found >= 0)
				{
					return found;
				}
			}
		}
		Widget[] children = widget.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				int found = findWidgetWithActionRecursive(child, action);
				if (found >= 0)
				{
					return found;
				}
			}
		}
		return -1;
	}

	private boolean isBlockedCollectionLogContext()
	{
		try
		{
			return client.getVarbitValue(12061) != 0;
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

	private void scheduleAfterTicks(int ticksRemaining, Runnable task)
	{
		if (ticksRemaining <= 0)
		{
			scanOpenCollectionLogPage(client);
			task.run();
			return;
		}
		clientThread.invokeLater(() -> scheduleAfterTicks(ticksRemaining - 1, task));
	}

	private static boolean isWidgetSlotObtained(Widget widgetItem)
	{
		if (widgetItem.getOpacity() == 0)
		{
			return true;
		}
		return widgetItem.getItemQuantity() > 0;
	}
}
