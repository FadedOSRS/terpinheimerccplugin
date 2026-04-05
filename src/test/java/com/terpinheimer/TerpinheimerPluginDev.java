package com.terpinheimer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class TerpinheimerPluginDev
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TerpinheimerPlugin.class);
		RuneLite.main(args);
	}
}
