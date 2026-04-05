package com.terpinheimer.discord;

import com.terpinheimer.TerpinheimerConfig;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class LevelEventHandler
{
	private static final int LEVEL_SCREENSHOT_DELAY_MS = 500;

	private final Client client;
	private final TerpinheimerConfig config;
	private final WebhookMessageBuilder messageBuilder;
	private final WebhookDispatcher dispatcher;
	private final ClientThread clientThread;
	private final ClientScreenshot screenshot;
	private final ScheduledExecutorService scheduledExecutor;
	private final DiscordLoginGrace loginGrace;

	private final Map<Skill, Integer> previousReal = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> previousVirtual = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> lastPost99NotifyXp = new EnumMap<>(Skill.class);

	private int previousCombat = -1;

	@Inject
	LevelEventHandler(
		Client client,
		TerpinheimerConfig config,
		WebhookMessageBuilder messageBuilder,
		WebhookDispatcher dispatcher,
		ClientThread clientThread,
		ClientScreenshot screenshot,
		ScheduledExecutorService scheduledExecutor,
		DiscordLoginGrace loginGrace)
	{
		this.client = client;
		this.config = config;
		this.messageBuilder = messageBuilder;
		this.dispatcher = dispatcher;
		this.clientThread = clientThread;
		this.screenshot = screenshot;
		this.scheduledExecutor = scheduledExecutor;
		this.loginGrace = loginGrace;
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Skill s = event.getSkill();
		if (s == Skill.OVERALL)
		{
			return;
		}

		int xp = event.getXp();
		int real = client.getRealSkillLevel(s);
		int virtual = Experience.getLevelForXp(xp);

		if (loginGrace.inLoginGracePeriod())
		{
			int newCombat = computeCombat();
			previousCombat = newCombat;
			previousReal.put(s, real);
			previousVirtual.put(s, virtual);
			if (real >= Experience.MAX_REAL_LEVEL)
			{
				lastPost99NotifyXp.put(s, xp);
			}
			return;
		}

		int newCombat = computeCombat();
		if (newCombat > previousCombat && config.levelNotifyCombatLevels())
		{
			String msg = LevelNotificationFormatter.formatCombat(
				config.levelCombatNotifyMessage(), client, newCombat);
			String json = messageBuilder.toWebhookJson(messageBuilder.combatLevelDescriptionEmbed(msg));
			dispatcher.enqueue(new WebhookPayload(json, null));
		}
		previousCombat = newCombat;

		int wasReal = previousReal.getOrDefault(s, real);
		boolean realIncreased = real > wasReal;
		previousReal.put(s, real);

		if (config.sendLevels() && realIncreased)
		{
			if (real >= config.levelNotifyMinSkillLevel())
			{
				int nBelow = config.levelNotifyEveryNLevels();
				int nAfter = config.levelNotifyEveryNLevelsAfterOverride();
				int overrideAt = config.levelNotifyIntervalOverrideLevel();
				int n = real >= overrideAt ? nAfter : nBelow;
				boolean onInterval = n <= 1 || real % n == 0;
				if (onInterval)
				{
					String msg = LevelNotificationFormatter.formatSkill(
						config.levelNotifyMessage(), client, s, real, virtual, xp);
					enqueueLevelPayload(msg, s);
				}
			}
		}

		if (real >= Experience.MAX_REAL_LEVEL)
		{
			int prevV = previousVirtual.getOrDefault(s, virtual);
			if (config.sendLevels() && config.levelNotifyVirtualLevels() && virtual > prevV && !realIncreased)
			{
				String msg = LevelNotificationFormatter.formatSkill(
					config.levelNotifyMessage(), client, s, real, virtual, xp);
				enqueueLevelPayload(msg, s);
			}

			if (config.sendLevels() && !config.levelNotifyVirtualLevels())
			{
				int interval = config.levelNotifyPost99XpInterval();
				if (interval > 0 && !realIncreased)
				{
					if (!lastPost99NotifyXp.containsKey(s))
					{
						lastPost99NotifyXp.put(s, xp);
					}
					else if (xp - lastPost99NotifyXp.get(s) >= interval)
					{
						lastPost99NotifyXp.put(s, xp);
						String msg = LevelNotificationFormatter.formatSkill(
							config.levelNotifyMessage(), client, s, real, virtual, xp);
						enqueueLevelPayload(msg, s);
					}
				}
			}
		}

		previousVirtual.put(s, virtual);
	}

	private void enqueueLevelPayload(String message, Skill skill)
	{
		String author = client.getLocalPlayer() != null
			? Text.removeTags(client.getLocalPlayer().getName()) : "Player";
		String thumb = SkillWikiIcons.iconUrl(skill);
		if (config.levelNotifySendImage())
		{
			scheduledExecutor.schedule(() -> clientThread.invokeLater(() ->
			{
				byte[] png = screenshot.capturePngOrNull();
				String json = messageBuilder.toWebhookJson(
					messageBuilder.levelUpDinkStyleEmbed(author, message, thumb));
				dispatcher.enqueue(new WebhookPayload(json, png));
			}), LEVEL_SCREENSHOT_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		else
		{
			String json = messageBuilder.toWebhookJson(
				messageBuilder.levelUpDinkStyleEmbed(author, message, thumb));
			dispatcher.enqueue(new WebhookPayload(json, null));
		}
	}

	private int computeCombat()
	{
		return Experience.getCombatLevel(
			client.getRealSkillLevel(Skill.ATTACK),
			client.getRealSkillLevel(Skill.STRENGTH),
			client.getRealSkillLevel(Skill.DEFENCE),
			client.getRealSkillLevel(Skill.HITPOINTS),
			client.getRealSkillLevel(Skill.MAGIC),
			client.getRealSkillLevel(Skill.RANGED),
			client.getRealSkillLevel(Skill.PRAYER)
		);
	}
}
