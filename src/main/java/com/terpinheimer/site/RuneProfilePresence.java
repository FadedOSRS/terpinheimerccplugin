package com.terpinheimer.site;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Detects whether the RuneProfile hub plugin is enabled. Its
 * {@code CollectionLogWidgetSubscriber} calls {@code runScript} from script callbacks and
 * conflicts with other collection-log tooling.
 */
@Singleton
public final class RuneProfilePresence
{
	private final PluginManager pluginManager;

	@Inject
	RuneProfilePresence(PluginManager pluginManager)
	{
		this.pluginManager = pluginManager;
	}

	public boolean isEnabled()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin == null)
			{
				continue;
			}
			String name = plugin.getClass().getName();
			if (name.startsWith("com.runeprofile.") && pluginManager.isPluginEnabled(plugin))
			{
				return true;
			}
		}
		return false;
	}
}
