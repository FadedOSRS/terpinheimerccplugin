package com.terpinheimer.site;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

/**
 * Schedules clog site sync on the client thread. Item data is collected passively by
 * {@link CollectionLogScriptCapture} (script 4100), {@link CollectionLogUnlockCapture} (chat unlocks),
 * plus {@link CollectionLogVarbitSnapshot} when the log UI is not open.
 */
@Singleton
public final class CollectionLogObtainedItemsTracker
{
	private final ClientThread clientThread;

	@Inject
	CollectionLogObtainedItemsTracker(ClientThread clientThread)
	{
		this.clientThread = clientThread;
	}

	public void runBeforeSyncExport(Runnable onReady)
	{
		if (onReady == null)
		{
			return;
		}
		clientThread.invokeLater(onReady);
	}

	/** @deprecated No longer scans widgets; kept for API compatibility. */
	@Deprecated
	public int scanOpenCollectionLogPage(Client client)
	{
		return 0;
	}
}
