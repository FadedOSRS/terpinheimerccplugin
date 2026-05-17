package com.terpinheimer.site;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.eventbus.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers collection-log capture listeners only for the duration of a sidebar POST, and never
 * while RuneProfile is enabled.
 */
@Singleton
public final class ClogCaptureLifecycle
{
	private static final Logger log = LoggerFactory.getLogger(ClogCaptureLifecycle.class);

	private final EventBus eventBus;
	private final ClogManualSession clogManualSession;
	private final RuneProfilePresence runeProfilePresence;
	private final CollectionLogScriptCapture collectionLogScriptCapture;
	private final CollectionLogUnlockCapture collectionLogUnlockCapture;
	private final ClogChronicleTracker clogChronicleTracker;

	private volatile boolean captureListenersRegistered;

	@Inject
	ClogCaptureLifecycle(
		EventBus eventBus,
		ClogManualSession clogManualSession,
		RuneProfilePresence runeProfilePresence,
		CollectionLogScriptCapture collectionLogScriptCapture,
		CollectionLogUnlockCapture collectionLogUnlockCapture,
		ClogChronicleTracker clogChronicleTracker)
	{
		this.eventBus = eventBus;
		this.clogManualSession = clogManualSession;
		this.runeProfilePresence = runeProfilePresence;
		this.collectionLogScriptCapture = collectionLogScriptCapture;
		this.collectionLogUnlockCapture = collectionLogUnlockCapture;
		this.clogChronicleTracker = clogChronicleTracker;
	}

	/**
	 * Runs {@code clientWork} on the client thread inside a short-lived capture window, or with no
	 * capture hooks when RuneProfile is enabled.
	 */
	public void runDuringManualPost(Runnable clientWork)
	{
		if (clientWork == null)
		{
			return;
		}
		if (runeProfilePresence.isEnabled())
		{
			clogManualSession.beginManualPost();
			try
			{
				clientWork.run();
			}
			finally
			{
				clogManualSession.endManualPost();
			}
			return;
		}
		clogManualSession.beginManualPost();
		registerCaptureListeners();
		try
		{
			clientWork.run();
		}
		finally
		{
			unregisterAll();
			clogManualSession.endManualPost();
		}
	}

	public void unregisterAll()
	{
		if (captureListenersRegistered)
		{
			eventBus.unregister(collectionLogScriptCapture);
			eventBus.unregister(collectionLogUnlockCapture);
			eventBus.unregister(clogChronicleTracker);
			captureListenersRegistered = false;
			log.debug("Terpinheimer: collection log capture listeners unregistered");
		}
	}

	private void registerCaptureListeners()
	{
		if (captureListenersRegistered)
		{
			return;
		}
		eventBus.register(collectionLogScriptCapture);
		eventBus.register(collectionLogUnlockCapture);
		eventBus.register(clogChronicleTracker);
		captureListenersRegistered = true;
		log.debug("Terpinheimer: collection log capture listeners registered for manual POST");
	}
}
