package com.terpinheimer.discord;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * After {@link GameState#LOGGED_IN}, the client often fires a burst of {@code StatChanged} / other
 * events while skills sync. Skip Discord screenshots (and level webhooks) for a few game ticks.
 */
@Singleton
public class DiscordLoginGrace
{
	private static final int GRACE_TICKS = 5;

	private final Client client;

	private int ticksSinceLogin = -1;

	@Inject
	DiscordLoginGrace(Client client)
	{
		this.client = client;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gs = event.getGameState();
		if (gs == GameState.LOGGED_IN)
		{
			ticksSinceLogin = 0;
		}
		else if (gs == GameState.LOGIN_SCREEN || gs == GameState.LOGIN_SCREEN_AUTHENTICATOR
			|| gs == GameState.CONNECTION_LOST)
		{
			ticksSinceLogin = -1;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			ticksSinceLogin = -1;
			return;
		}
		if (ticksSinceLogin >= 0 && ticksSinceLogin < 10_000)
		{
			ticksSinceLogin++;
		}
	}

	public boolean inLoginGracePeriod()
	{
		return ticksSinceLogin >= 0 && ticksSinceLogin < GRACE_TICKS;
	}
}
