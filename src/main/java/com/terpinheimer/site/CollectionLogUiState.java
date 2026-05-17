package com.terpinheimer.site;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Read-only collection log UI checks. No event-bus registration (avoids overlap with RuneProfile). */
@Singleton
public final class CollectionLogUiState
{
	@Inject
	CollectionLogUiState()
	{
	}

	public boolean isCollectionLogOpen(Client client)
	{
		if (client == null)
		{
			return false;
		}
		Widget root = client.getWidget(InterfaceID.COLLECTION_LOG, 0);
		return root != null && !root.isHidden();
	}
}
