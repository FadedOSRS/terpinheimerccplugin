package com.terpinheimer;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.terpinheimer.discord.ClueScrollEventHandler;
import com.terpinheimer.discord.CollectionLogEventHandler;
import com.terpinheimer.discord.CombatAchievementEventHandler;
import com.terpinheimer.discord.DeathEventHandler;
import com.terpinheimer.discord.DiscordLoginGrace;
import com.terpinheimer.discord.LevelEventHandler;
import com.terpinheimer.discord.LootEventHandler;
import com.terpinheimer.discord.PetEventHandler;
import com.terpinheimer.discord.QuestEventHandler;
import com.terpinheimer.discord.WebhookDispatcher;
import com.terpinheimer.attendance.ClanAttendanceTracker;
import com.terpinheimer.map.LiveMapEventHandler;
import com.terpinheimer.ui.TerpinheimerPanel;
import com.terpinheimer.runeprofile.RuneProfileLogoutPayload;
import com.terpinheimer.runeprofile.RuneProfileUpdateService;
import com.terpinheimer.site.ClanCalendarSummaryService;
import com.terpinheimer.site.ClanCalendarSummaryService.WebEventRow;
import com.terpinheimer.wom.WomCompetitionService;
import com.terpinheimer.wom.WomLeaderboardModels;
import com.terpinheimer.wom.WomPlayerUpdateService;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Terpinheimer",
	description = "Clan hub: Home, Skill of the Week & Boss of the Week (Wise Old Man).",
	tags = {"terpinheimer", "clan", "sotw", "botw", "wom"}
)
public class TerpinheimerPlugin extends Plugin
{
	private static final int WOM_UPDATE_XP_THRESHOLD = 10_000;
	/** When General → Wise Old Man group ID is 0, use this group (legacy profiles often still store 0). */
	private static final int DEFAULT_WOM_GROUP_ID = 23745;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private Gson gson;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private EventBus eventBus;
	@Inject
	private TerpinheimerConfig config;
	@Inject
	private ConfigManager configManager;
	@Inject
	private ScheduledExecutorService scheduledExecutor;
	@Inject
	private WomCompetitionService womCompetitionService;
	@Inject
	private WomPlayerUpdateService womPlayerUpdateService;
	@Inject
	private RuneProfileUpdateService runeProfileUpdateService;
	@Inject
	private WebhookDispatcher webhookDispatcher;
	@Inject
	private LootEventHandler lootEventHandler;
	@Inject
	private ClueScrollEventHandler clueScrollEventHandler;
	@Inject
	private PetEventHandler petEventHandler;
	@Inject
	private DeathEventHandler deathEventHandler;
	@Inject
	private LevelEventHandler levelEventHandler;
	@Inject
	private CollectionLogEventHandler collectionLogEventHandler;
	@Inject
	private QuestEventHandler questEventHandler;
	@Inject
	private CombatAchievementEventHandler combatAchievementEventHandler;
	@Inject
	private DiscordLoginGrace discordLoginGrace;
	@Inject
	private LiveMapEventHandler liveMapEventHandler;
	@Inject
	private ClanCalendarSummaryService clanCalendarSummaryService;
	@Inject
	private ClanAttendanceTracker clanAttendanceTracker;

	private String sessionPlayerName;
	private long sessionBaselineXp;
	private boolean levelUpThisSession;
	private final Map<Skill, Integer> skillLevelBaseline = new EnumMap<>(Skill.class);

	private TerpinheimerPanel panel;
	private NavigationButton navButton;
	private ExecutorService worker;
	private ScheduledFuture<?> refreshTask;

	private volatile WomLeaderboardModels.CompetitionSnapshot sotwSnapshot =
		WomLeaderboardModels.CompetitionSnapshot.empty("Loading…");
	private volatile WomLeaderboardModels.CompetitionSnapshot botwSnapshot =
		WomLeaderboardModels.CompetitionSnapshot.empty("Loading…");
	private volatile String announcementsText = "";
	/** One-line status for Home → Events → Website row (clan calendar summary API). */
	private volatile String clanCalendarSummaryStatus = "—";
	/** Rows for Web Events tab table (?format=array). */
	private volatile List<WebEventRow> clanCalendarWebEventRows = Collections.emptyList();
	/** Epoch millis of last successful calendar API fetch (0 = none). */
	private volatile long clanCalendarLastFetchMs;
	/** Baseline for reverting {@code announcementsText} when the editor is not Owner / Deputy Owner. */
	private String lastAuthorizedAnnouncementsText = "";

	@Provides
	TerpinheimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TerpinheimerConfig.class);
	}

	public WomLeaderboardModels.CompetitionSnapshot getSotwSnapshot()
	{
		return sotwSnapshot;
	}

	public WomLeaderboardModels.CompetitionSnapshot getBotwSnapshot()
	{
		return botwSnapshot;
	}

	public String getAnnouncementsText()
	{
		return announcementsText;
	}

	public String getClanCalendarSummaryStatus()
	{
		return clanCalendarSummaryStatus;
	}

	public List<WebEventRow> getClanCalendarWebEventRows()
	{
		return clanCalendarWebEventRows;
	}

	public long getClanCalendarLastFetchMs()
	{
		return clanCalendarLastFetchMs;
	}

	public TerpinheimerConfig getConfig()
	{
		return config;
	}

	public ClanAttendanceTracker getClanAttendanceTracker()
	{
		return clanAttendanceTracker;
	}

	public void runOnClientThread(Runnable r)
	{
		clientThread.invokeLater(r);
	}

	public void requestFullRefresh()
	{
		if (worker != null)
		{
			worker.execute(this::pullAll);
		}
	}

	@Override
	protected void startUp()
	{
		eventBus.register(this);
		webhookDispatcher.start();
		eventBus.register(discordLoginGrace);
		eventBus.register(lootEventHandler);
		eventBus.register(clueScrollEventHandler);
		eventBus.register(petEventHandler);
		eventBus.register(deathEventHandler);
		eventBus.register(levelEventHandler);
		eventBus.register(collectionLogEventHandler);
		eventBus.register(questEventHandler);
		eventBus.register(combatAchievementEventHandler);
		eventBus.register(liveMapEventHandler);
		eventBus.register(clanAttendanceTracker);
		worker = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "terpinheimer-fetch");
			t.setDaemon(true);
			return t;
		});
		panel = new TerpinheimerPanel(this, config);
		navButton = createNavigationButton();
		clientToolbar.addNavigation(navButton);
		scheduleRefresh();
		String initialAnnouncements = config.announcementsText();
		lastAuthorizedAnnouncementsText = initialAnnouncements != null ? initialAnnouncements : "";
		worker.execute(this::pullAll);
	}

	@Override
	protected void shutDown()
	{
		cancelRefresh();
		eventBus.unregister(clanAttendanceTracker);
		eventBus.unregister(liveMapEventHandler);
		eventBus.unregister(combatAchievementEventHandler);
		eventBus.unregister(questEventHandler);
		eventBus.unregister(collectionLogEventHandler);
		eventBus.unregister(levelEventHandler);
		eventBus.unregister(deathEventHandler);
		eventBus.unregister(petEventHandler);
		eventBus.unregister(clueScrollEventHandler);
		eventBus.unregister(lootEventHandler);
		eventBus.unregister(discordLoginGrace);
		webhookDispatcher.stop();
		eventBus.unregister(this);
		clientToolbar.removeNavigation(navButton);
		if (panel != null)
		{
			panel.shutdown();
		}
		worker.shutdownNow();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged ev)
	{
		if (!"terpinheimer".equals(ev.getGroup()))
		{
			return;
		}
		if ("announcementsText".equals(ev.getKey()))
		{
			String newVal = ev.getNewValue() != null ? ev.getNewValue() : "";
			if (canEditAnnouncementsAsClanOfficer())
			{
				lastAuthorizedAnnouncementsText = newVal;
				requestFullRefresh();
			}
			else if (!newVal.equals(lastAuthorizedAnnouncementsText))
			{
				if (ev.getProfile() != null)
				{
					configManager.setConfiguration(ev.getGroup(), ev.getProfile(), ev.getKey(), lastAuthorizedAnnouncementsText);
				}
				else
				{
					configManager.setConfiguration(ev.getGroup(), ev.getKey(), lastAuthorizedAnnouncementsText);
				}
				requestFullRefresh();
			}
		}
		if ("refreshIntervalMinutes".equals(ev.getKey()))
		{
			scheduleRefresh();
		}
		if ("sidebarButtonPriority".equals(ev.getKey()))
		{
			rebuildNavigationButton();
		}
		if ("clanCalendarPageUrl".equals(ev.getKey()) || "clanCalendarSummaryApiUrl".equals(ev.getKey()))
		{
			requestFullRefresh();
		}
	}

	private NavigationButton createNavigationButton()
	{
		return NavigationButton.builder()
			.tooltip("Terpinheimer")
			.icon(TerpinheimerPanel.loadToolbarIcon())
			.priority(config.sidebarButtonPriority())
			.panel(panel)
			.build();
	}

	private void rebuildNavigationButton()
	{
		if (panel == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (navButton != null)
			{
				clientToolbar.removeNavigation(navButton);
			}
			navButton = createNavigationButton();
			clientToolbar.addNavigation(navButton);
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged ev)
	{
		GameState state = ev.getGameState();
		switch (state)
		{
			case LOGGED_IN:
				skillLevelBaseline.clear();
				levelUpThisSession = false;
				if (client.getLocalPlayer() != null)
				{
					sessionPlayerName = Text.removeTags(client.getLocalPlayer().getName());
				}
				sessionBaselineXp = client.getOverallExperience();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				onWiseOldManSessionEnd();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged ev)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Skill s = ev.getSkill();
		if (s == Skill.OVERALL)
		{
			return;
		}
		int levelAfter = client.getRealSkillLevel(s);
		int levelBefore = skillLevelBaseline.getOrDefault(s, -1);
		if (levelBefore != -1 && levelAfter > levelBefore)
		{
			levelUpThisSession = true;
		}
		skillLevelBaseline.put(s, levelAfter);
	}

	private void onWiseOldManSessionEnd()
	{
		boolean womOn = config.womUpdateProfileOnLogout();
		boolean rpOn = config.runeprofileUpdateOnLogout();
		if (!womOn && !rpOn)
		{
			if (sessionPlayerName != null)
			{
				requestFullRefresh();
			}
			return;
		}
		long totalXp = client.getOverallExperience();
		boolean shouldPost = sessionPlayerName != null
			&& !sessionPlayerName.isEmpty()
			&& (Math.abs(totalXp - sessionBaselineXp) > WOM_UPDATE_XP_THRESHOLD || levelUpThisSession);

		final String nameForWom = sessionPlayerName;
		levelUpThisSession = false;
		sessionBaselineXp = totalXp;

		if (!shouldPost)
		{
			if (sessionPlayerName != null)
			{
				requestFullRefresh();
			}
			return;
		}

		clientThread.invokeLater(() ->
		{
			final String rpPayload = rpOn ? RuneProfileLogoutPayload.buildJson(client, gson) : null;
			worker.execute(() ->
			{
				if (womOn)
				{
					try
					{
						womPlayerUpdateService.requestUpdate(Text.standardize(nameForWom));
					}
					catch (IOException ignored)
					{
					}
				}
				if (rpPayload != null)
				{
					try
					{
						runeProfileUpdateService.postProfileJson(rpPayload);
					}
					catch (IOException ignored)
					{
					}
				}
				pullAll();
			});
		});
	}

	private void scheduleRefresh()
	{
		cancelRefresh();
		long mins = Math.max(1, config.refreshIntervalMinutes());
		refreshTask = scheduledExecutor.scheduleWithFixedDelay(() -> worker.execute(this::pullAll),
			mins, mins, TimeUnit.MINUTES);
	}

	private void cancelRefresh()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
			refreshTask = null;
		}
	}

	/**
	 * Only Owner and Deputy Owner may persist {@link TerpinheimerConfig#announcementsText()}; no other
	 * settings use this check.
	 */
	private boolean canEditAnnouncementsAsClanOfficer()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		ClanSettings settings = client.getClanSettings();
		if (settings == null || client.getLocalPlayer() == null)
		{
			return false;
		}
		String std = Text.standardize(Text.removeTags(client.getLocalPlayer().getName()));
		ClanMember member = settings.findMember(std);
		if (member == null)
		{
			for (Object o : settings.getMembers())
			{
				if (o instanceof ClanMember)
				{
					ClanMember cm = (ClanMember) o;
					if (std.equals(Text.standardize(cm.getName())))
					{
						member = cm;
						break;
					}
				}
			}
		}
		if (member == null)
		{
			return false;
		}
		ClanRank rank = member.getRank();
		return ClanRank.OWNER.equals(rank) || ClanRank.DEPUTY_OWNER.equals(rank);
	}

	private int effectiveWomGroupId()
	{
		int g = config.womGroupId();
		return g > 0 ? g : DEFAULT_WOM_GROUP_ID;
	}

	private void pullAll()
	{
		pullAnnouncements();
		try
		{
			int gid = effectiveWomGroupId();
			sotwSnapshot = womCompetitionService.fetchSnapshot(
				gid,
				0,
				true,
				"",
				"",
				"",
				false
			);
		}
		catch (IOException e)
		{
			sotwSnapshot = WomLeaderboardModels.CompetitionSnapshot.error(e.getMessage() != null ? e.getMessage() : "Network error");
		}
		try
		{
			int gid = effectiveWomGroupId();
			botwSnapshot = womCompetitionService.fetchSnapshot(
				gid,
				0,
				true,
				"",
				"",
				"",
				true
			);
		}
		catch (IOException e)
		{
			botwSnapshot = WomLeaderboardModels.CompetitionSnapshot.error(e.getMessage() != null ? e.getMessage() : "Network error");
		}
		refreshClanCalendarSummaryStatus();
		SwingUtilities.invokeLater(() -> panel.applyFromPlugin());
	}

	private void refreshClanCalendarSummaryStatus()
	{
		String api = config.clanCalendarSummaryApiUrl();
		if (api == null || api.trim().isEmpty() || !api.trim().startsWith("https://"))
		{
			clanCalendarSummaryStatus = "—";
			clanCalendarWebEventRows = Collections.emptyList();
			clanCalendarLastFetchMs = 0L;
			return;
		}
		try
		{
			ClanCalendarSummaryService.CalendarParseResult parsed = clanCalendarSummaryService.fetchCalendar(
				api.trim(),
				ClanCalendarSummaryService.CLAN_CALENDAR_SUMMARY_AUTH_SECRET);
			clanCalendarSummaryStatus = parsed.getStatus();
			clanCalendarWebEventRows = List.copyOf(parsed.getEventRows());
			clanCalendarLastFetchMs = System.currentTimeMillis();
		}
		catch (IOException e)
		{
			clanCalendarSummaryStatus = "Unavailable";
			clanCalendarWebEventRows = Collections.emptyList();
			clanCalendarLastFetchMs = 0L;
		}
	}

	private void pullAnnouncements()
	{
		if (!config.announcementsEnabled())
		{
			announcementsText = "";
			return;
		}
		announcementsText = config.announcementsText() != null ? config.announcementsText() : "";
	}
}
