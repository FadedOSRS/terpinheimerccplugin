package com.terpinheimer.site;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * True only while Home → POST collection log is building/sending a payload on the client thread.
 */
@Singleton
public final class ClogManualSession
{
	private volatile boolean manualPostInProgress;

	@Inject
	ClogManualSession()
	{
	}

	public boolean isManualPostInProgress()
	{
		return manualPostInProgress;
	}

	void beginManualPost()
	{
		manualPostInProgress = true;
	}

	void endManualPost()
	{
		manualPostInProgress = false;
	}
}
