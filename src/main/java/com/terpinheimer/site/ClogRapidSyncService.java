package com.terpinheimer.site;

import com.terpinheimer.TerpinheimerConfig;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;

/**
 * Debounced site POST after a new collection log item (~3s, like RuneProfile rapid sync).
 */
@Singleton
public final class ClogRapidSyncService
{
	private static final int RAPID_SYNC_DELAY_SECONDS = 3;

	private final Client client;
	private final TerpinheimerConfig config;
	private final ClogChronicleTracker clogChronicleTracker;
	private final CollectionLogObtainedItemsTracker collectionLogObtainedItemsTracker;
	private final ClogSitePayloadBuilder clogSitePayloadBuilder;
	private final ClogSiteSyncService clogSiteSyncService;
	private final ScheduledExecutorService scheduledExecutor;

	private volatile ExecutorService worker;
	private volatile ScheduledFuture<?> rapidSyncTask;

	@Inject
	ClogRapidSyncService(
		Client client,
		TerpinheimerConfig config,
		ClogChronicleTracker clogChronicleTracker,
		CollectionLogObtainedItemsTracker collectionLogObtainedItemsTracker,
		ClogSitePayloadBuilder clogSitePayloadBuilder,
		ClogSiteSyncService clogSiteSyncService,
		ScheduledExecutorService scheduledExecutor)
	{
		this.client = client;
		this.config = config;
		this.clogChronicleTracker = clogChronicleTracker;
		this.collectionLogObtainedItemsTracker = collectionLogObtainedItemsTracker;
		this.clogSitePayloadBuilder = clogSitePayloadBuilder;
		this.clogSiteSyncService = clogSiteSyncService;
		this.scheduledExecutor = scheduledExecutor;
	}

	public void bindWorker(ExecutorService worker)
	{
		this.worker = worker;
	}

	public void scheduleRapidSync(String eventSource)
	{
		if (!config.clogRapidSyncOnNewItem() || !isClogSyncConfigured())
		{
			return;
		}
		ExecutorService w = worker;
		if (w == null || w.isShutdown())
		{
			return;
		}
		ScheduledFuture<?> prev = rapidSyncTask;
		if (prev != null)
		{
			prev.cancel(false);
		}
		rapidSyncTask = scheduledExecutor.schedule(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			List<ClogChronicleTracker.Line> snap = clogChronicleTracker.snapshotChronicle();
			collectionLogObtainedItemsTracker.runBeforeSyncExport(() ->
			{
				String json = clogSitePayloadBuilder.buildJson(client, snap, eventSource);
				if (json == null || json.isEmpty())
				{
					return;
				}
				w.execute(() -> clogSiteSyncService.postClogJsonAsync(
					config.clogSyncApiUrl().trim(),
					config.clogSyncApiSecret(),
					json));
			});
		}, RAPID_SYNC_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	private boolean isClogSyncConfigured()
	{
		String u = config.clogSyncApiUrl();
		String s = config.clogSyncApiSecret();
		return u != null && u.trim().startsWith("https://")
			&& s != null && !s.trim().isEmpty();
	}
}
