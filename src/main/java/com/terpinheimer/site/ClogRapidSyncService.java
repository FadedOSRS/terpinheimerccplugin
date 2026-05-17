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
	private final CollectionLogUiState collectionLogUiState;

	private volatile ExecutorService worker;
	private volatile ScheduledFuture<?> rapidSyncTask;

	private static final int RAPID_SYNC_RETRY_SECONDS = 8;

	@Inject
	ClogRapidSyncService(
		Client client,
		TerpinheimerConfig config,
		ClogChronicleTracker clogChronicleTracker,
		CollectionLogObtainedItemsTracker collectionLogObtainedItemsTracker,
		ClogSitePayloadBuilder clogSitePayloadBuilder,
		ClogSiteSyncService clogSiteSyncService,
		ScheduledExecutorService scheduledExecutor,
		CollectionLogUiState collectionLogUiState)
	{
		this.client = client;
		this.config = config;
		this.clogChronicleTracker = clogChronicleTracker;
		this.collectionLogObtainedItemsTracker = collectionLogObtainedItemsTracker;
		this.clogSitePayloadBuilder = clogSitePayloadBuilder;
		this.clogSiteSyncService = clogSiteSyncService;
		this.scheduledExecutor = scheduledExecutor;
		this.collectionLogUiState = collectionLogUiState;
	}

	public void bindWorker(ExecutorService worker)
	{
		this.worker = worker;
	}

	/** No-op: collection log site sync is manual-only (sidebar POST button). */
	public void scheduleRapidSync(String eventSource)
	{
	}
}
