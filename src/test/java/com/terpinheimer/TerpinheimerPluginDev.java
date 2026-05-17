package com.terpinheimer;

import java.io.File;
import java.util.Locale;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class TerpinheimerPluginDev
{
	public static void main(String[] args) throws Exception
	{
		warnIfSideloadedTerpinheimerPresent();
		ExternalPluginManager.loadBuiltin(TerpinheimerPlugin.class);
		RuneLite.main(args);
	}

	/**
	 * {@code gradlew run} quarantines sideloaded jars automatically; this catches manual runs or stale quarantine files.
	 */
	private static void warnIfSideloadedTerpinheimerPresent()
	{
		File dir = new File(RuneLite.RUNELITE_DIR, "sideloaded-plugins");
		File[] jars = dir.listFiles((d, name) ->
		{
			String n = name.toLowerCase(Locale.ROOT);
			return n.endsWith(".jar") && n.contains("terpinheimer");
		});
		if (jars != null && jars.length > 0)
		{
			System.err.println("Terpinheimer dev: found sideloaded jar(s) that will duplicate the builtin dev plugin:");
			for (File jar : jars)
			{
				System.err.println("  - " + jar.getAbsolutePath());
			}
			System.err.println("Remove or rename them, or use `gradlew run` (quarantines sideloads for that session).");
		}
	}
}
