package com.terpinheimer;

import com.google.inject.Provides;
import com.terpinheimer.attendance.ClanAttendanceTracker;
import com.terpinheimer.discord.ClanCofferDonationEventHandler;
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
import com.terpinheimer.map.LiveMapEventHandler;
import com.terpinheimer.party.PartyLootTracker;
import com.terpinheimer.ui.TerpinheimerPanel;
import com.terpinheimer.site.ClanCalendarSummaryService;
import com.terpinheimer.site.ClanCalendarSummaryService.WebEventRow;
import com.terpinheimer.site.ClanRosterSitePayloadBuilder;
import com.terpinheimer.site.ClanRosterSnapshotTracker;
import com.terpinheimer.site.ClogChronicleTracker;
import com.terpinheimer.site.ClogCaptureLifecycle;
import com.terpinheimer.site.RuneProfilePresence;
import com.terpinheimer.site.ClogSitePayloadBuilder;
import com.terpinheimer.site.ClogSiteSyncService;
import com.terpinheimer.site.ClogRapidSyncService;
import com.terpinheimer.site.CollectionLogUiState;
import com.terpinheimer.site.CollectionLogItemStore;
import com.terpinheimer.site.CollectionLogObtainedItemsTracker;
import com.terpinheimer.site.CollectionLogScriptCapture;
import com.terpinheimer.site.CollectionLogUnlockCapture;
import com.terpinheimer.wom.WomCompetitionService;
import com.terpinheimer.wom.WomLeaderboardModels;
import com.terpinheimer.wom.WomPlayerUpdateService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
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
	/** Batch rapid XP ticks into one site POST. */
	private static final int CLOG_XP_SYNC_DEBOUNCE_SECONDS = 15;
	private static final int CLOG_XP_SYNC_RETRY_SECONDS = 10;
	/** ~45 minutes between automatic roster snapshots while logged in (600 ticks/min at normal game rate). */
	private static final int CLAN_ROSTER_PERIODIC_TICKS = 4_500;
	/** When General → Wise Old Man group ID is 0, use this group (legacy profiles often still store 0). */
	private static final int DEFAULT_WOM_GROUP_ID = 23745;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
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
	private ClanCofferDonationEventHandler clanCofferDonationEventHandler;
	@Inject
	private DiscordLoginGrace discordLoginGrace;
	@Inject
	private LiveMapEventHandler liveMapEventHandler;
	@Inject
	private ClanCalendarSummaryService clanCalendarSummaryService;
	@Inject
	private ClanAttendanceTracker clanAttendanceTracker;
	@Inject
	private PartyLootTracker partyLootTracker;
	@Inject
	private ClogChronicleTracker clogChronicleTracker;
	@Inject
	private CollectionLogObtainedItemsTracker collectionLogObtainedItemsTracker;
	@Inject
	private CollectionLogItemStore collectionLogItemStore;
	@Inject
	private CollectionLogUnlockCapture collectionLogUnlockCapture;
	@Inject
	private CollectionLogScriptCapture collectionLogScriptCapture;
	@Inject
	private ClogRapidSyncService clogRapidSyncService;
	@Inject
	private ClogSitePayloadBuilder clogSitePayloadBuilder;
	@Inject
	private ClogSiteSyncService clogSiteSyncService;
	@Inject
	private ClanRosterSitePayloadBuilder clanRosterSitePayloadBuilder;
	@Inject
	private ClanRosterSnapshotTracker clanRosterSnapshotTracker;
	@Inject
	private CollectionLogUiState collectionLogUiState;
	@Inject
	private ClogCaptureLifecycle clogCaptureLifecycle;
	@Inject
	private RuneProfilePresence runeProfilePresence;
	@Inject
	private PluginManager pluginManager;

	private boolean runeProfileClogConflictWarned;

	private String sessionPlayerName;
	private long sessionBaselineXp;
	private boolean levelUpThisSession;
	private final Map<Skill, Integer> skillLevelBaseline = new EnumMap<>(Skill.class);
	private final Map<Skill, Integer> clogLastSkillXp = new EnumMap<>(Skill.class);

	private TerpinheimerPanel panel;
	private NavigationButton navButton;
	private ExecutorService worker;
	private ScheduledFuture<?> refreshTask;
	private volatile ScheduledFuture<?> clogXpSyncDebounce;
	private volatile ScheduledFuture<?> clanRosterLoginDelayTask;
	private int ticksUntilClanRosterPeriodic = -1;
	/**
	 * Refreshed every {@link GameTick} on the client thread while the plugin is running. {@code true} when
	 * logged in and the local player is the Jagex clan Owner. Used for automatic roster POST and for the
	 * sidebar Home button (read from the EDT via {@link #isLocalPlayerJagexClanOwnerCached()}).
	 */
	private volatile boolean clanOwnerForRosterAutoSync;
	/** Previous game state for roster snapshot reset (skip reset on world hop). */
	private GameState rosterDiffPrevGameState = GameState.LOGIN_SCREEN;

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

	private static final String CONFIG_GROUP = "terpinheimer";

	/** Copies legacy per-feature secrets into {@link TerpinheimerConfig#clanSecret()} when empty. */
	private void warnIfDuplicateTerpinheimerLoaded()
	{
		long count = pluginManager.getPlugins().stream()
			.filter(p -> p.getClass() == TerpinheimerPlugin.class)
			.count();
		if (count <= 1)
		{
			return;
		}
		clientThread.invokeLater(() -> client.addChatMessage(
			ChatMessageType.CONSOLE,
			"Terpinheimer",
			"Terpinheimer is loaded " + count + " times — remove Plugin Hub / sideloaded copies or only one will work.",
			"Terpinheimer"));
	}

	private void scheduleClogSiteSyncFromXpWhenSafe()
	{
		if (client.getGameState() != GameState.LOGGED_IN || worker == null || worker.isShutdown())
		{
			return;
		}
		if (!isAutomaticClogSiteSyncEnabled())
		{
			return;
		}
		if (collectionLogUiState.isCollectionLogOpen(client))
		{
			clogXpSyncDebounce = scheduledExecutor.schedule(
				() -> clientThread.invokeLater(this::scheduleClogSiteSyncFromXpWhenSafe),
				CLOG_XP_SYNC_RETRY_SECONDS,
				TimeUnit.SECONDS);
			return;
		}
		List<ClogChronicleTracker.Line> snap = clogChronicleTracker.snapshotChronicle();
		collectionLogObtainedItemsTracker.runBeforeSyncExport(() ->
		{
			String json = clogSitePayloadBuilder.buildJson(client, snap, "xp-debounce-sync");
			if (json == null)
			{
				return;
			}
			worker.execute(() -> clogSiteSyncService.postClogJsonAsync(
				config.clogSyncApiUrl(),
				resolveClanSecret(),
				json));
		});
	}

	private void migrateLegacyClanSecretIfNeeded()
	{
		String current = configManager.getConfiguration(CONFIG_GROUP, "clanSecret");
		if (current != null && !current.trim().isEmpty())
		{
			return;
		}
		for (String legacyKey : new String[] {"clogSyncApiSecret", "clanRosterSyncApiSecret", "liveMapApiKey"})
		{
			String legacy = configManager.getConfiguration(CONFIG_GROUP, legacyKey);
			if (legacy != null && !legacy.trim().isEmpty())
			{
				configManager.setConfiguration(CONFIG_GROUP, "clanSecret", legacy.trim());
				return;
			}
		}
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

	/** Manual roster POST from the sidebar (does not require "Sync Jagex clan roster" to be on). */
	public void requestClanRosterSiteSync()
	{
		enqueueClanRosterSitePost(false);
	}

	public boolean isClanRosterManualSyncReady()
	{
		return isClanRosterSyncConfigured();
	}

	/**
	 * Manual collection log POST from the Terpinheimer sidebar (does not require "Sync collection log to website").
	 * Jagex removed/changed in-game collection log menu entries; use this button for an on-demand site sync.
	 */
	public void requestClogSiteManualSync()
	{
		if (!isClogSyncConfigured())
		{
			return;
		}
		if (worker == null || worker.isShutdown())
		{
			clientThread.invokeLater(() -> clogManualSyncChat(
				"Cannot sync collection log: plugin background worker is not running. Try toggling the plugin off and on."));
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (worker == null || worker.isShutdown())
			{
				clogManualSyncChat(
					"Cannot sync collection log: plugin background worker is not running. Try toggling the plugin off and on.");
				return;
			}
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				clogManualSyncChat("Cannot sync collection log: log in first.");
				return;
			}
			clogCaptureLifecycle.runDuringManualPost(() ->
			{
				collectionLogItemStore.reloadForCurrentAccount();
				List<ClogChronicleTracker.Line> snap = clogChronicleTracker.snapshotChronicle();
				String json = clogSitePayloadBuilder.buildJson(client, snap, "manual-sync");
				if (json == null || json.isEmpty())
				{
					clogManualSyncChat("Collection log sync: could not build payload (log in and try again).");
					return;
				}
				String url = config.clogSyncApiUrl().trim();
				String secret = resolveClanSecret();
				worker.execute(() -> clogSiteSyncService.postClogJsonAsync(url, secret, json, true));
			});
		});
	}

	public boolean isClogSiteManualSyncReady()
	{
		return isClogSyncConfigured();
	}

	@Override
	protected void startUp()
	{
		migrateLegacyClanSecretIfNeeded();
		warnIfDuplicateTerpinheimerLoaded();
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
		eventBus.register(clanCofferDonationEventHandler);
		eventBus.register(liveMapEventHandler);
		eventBus.register(clanAttendanceTracker);
		eventBus.register(collectionLogUnlockCapture);
		collectionLogItemStore.reloadForCurrentAccount();
		partyLootTracker.start();
		eventBus.register(partyLootTracker);
		maybeWarnRuneProfileClogConflict();
		worker = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "terpinheimer-fetch");
			t.setDaemon(true);
			return t;
		});
		panel = new TerpinheimerPanel(this, config, partyLootTracker);
		partyLootTracker.setUiRefresh(panel::syncPartyGroupTabUi);
		partyLootTracker.syncVisibility();
		navButton = createNavigationButton();
		clientToolbar.addNavigation(navButton);
		scheduleRefresh();
		String initialAnnouncements = config.announcementsText();
		lastAuthorizedAnnouncementsText = initialAnnouncements != null ? initialAnnouncements : "";
		clogRapidSyncService.bindWorker(worker);
		worker.execute(this::pullAll);
		rosterDiffPrevGameState = client.getGameState();
	}

	@Override
	protected void shutDown()
	{
		cancelClogXpSyncDebounce();
		cancelClanRosterLoginDelay();
		clogCaptureLifecycle.unregisterAll();
		clanRosterSnapshotTracker.reset();
		cancelRefresh();
		eventBus.unregister(clanAttendanceTracker);
		eventBus.unregister(collectionLogUnlockCapture);
		eventBus.unregister(partyLootTracker);
		partyLootTracker.stop();
		partyLootTracker.setUiRefresh(null);
		eventBus.unregister(liveMapEventHandler);
		eventBus.unregister(combatAchievementEventHandler);
		eventBus.unregister(clanCofferDonationEventHandler);
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
			if ("clanRosterSyncEnabled".equals(ev.getKey()) || "clanRosterSyncApiUrl".equals(ev.getKey())
				|| "clanSecret".equals(ev.getKey()) || "clanRosterSyncApiSecret".equals(ev.getKey()))
			{
				if (client.getGameState() == GameState.LOGGED_IN
					&& config.clanRosterSyncEnabled() && isClanRosterSyncConfigured()
					&& isLocalPlayerJagexClanOwner())
				{
					ticksUntilClanRosterPeriodic = CLAN_ROSTER_PERIODIC_TICKS;
					scheduleClanRosterSyncAfterLogin();
				}
			else
			{
				ticksUntilClanRosterPeriodic = -1;
				cancelClanRosterLoginDelay();
			}
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
		GameState prev = rosterDiffPrevGameState;
		switch (state)
		{
			case LOGGED_IN:
				collectionLogItemStore.reloadForCurrentAccount();
				if (prev != GameState.HOPPING)
				{
					clanRosterSnapshotTracker.reset();
				}
				cancelClogXpSyncDebounce();
				cancelClanRosterLoginDelay();
				clogLastSkillXp.clear();
				clogChronicleTracker.clear();
				skillLevelBaseline.clear();
				levelUpThisSession = false;
				if (client.getLocalPlayer() != null)
				{
					sessionPlayerName = Text.removeTags(client.getLocalPlayer().getName());
				}
				sessionBaselineXp = client.getOverallExperience();
				clanOwnerForRosterAutoSync = isLocalPlayerJagexClanOwner();
				if (config.clanRosterSyncEnabled() && isClanRosterSyncConfigured() && clanOwnerForRosterAutoSync)
				{
					ticksUntilClanRosterPeriodic = CLAN_ROSTER_PERIODIC_TICKS;
					scheduleClanRosterSyncAfterLogin();
				}
				else
				{
					ticksUntilClanRosterPeriodic = -1;
					cancelClanRosterLoginDelay();
				}
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				onSessionEndExternalSync();
				clanOwnerForRosterAutoSync = false;
				break;
			default:
				break;
		}
		rosterDiffPrevGameState = state;
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

		maybeScheduleClogSiteSyncAfterSkillXp(s, ev.getXp());
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clanOwnerForRosterAutoSync = isLocalPlayerJagexClanOwner();
		}
		else
		{
			clanOwnerForRosterAutoSync = false;
		}

		if (!config.clanRosterSyncEnabled() || !isClanRosterSyncConfigured()
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			ticksUntilClanRosterPeriodic = -1;
			return;
		}
		if (!clanOwnerForRosterAutoSync)
		{
			ticksUntilClanRosterPeriodic = -1;
			return;
		}
		if (ticksUntilClanRosterPeriodic <= 0)
		{
			return;
		}
		ticksUntilClanRosterPeriodic--;
		if (ticksUntilClanRosterPeriodic == 0)
		{
			ticksUntilClanRosterPeriodic = CLAN_ROSTER_PERIODIC_TICKS;
			enqueueClanRosterSitePost(true);
		}
	}

	private void onSessionEndExternalSync()
	{
		boolean womWant = config.womUpdateProfileOnLogout();
		boolean clogWant = isAutomaticClogSiteSyncEnabled();
		boolean rosterWant = config.clanRosterSyncEnabled() && isClanRosterSyncConfigured()
			&& clanOwnerForRosterAutoSync;
		if (!womWant && !clogWant && !rosterWant)
		{
			if (sessionPlayerName != null)
			{
				requestFullRefresh();
			}
			return;
		}
		long totalXp = client.getOverallExperience();
		boolean hadProgress = Math.abs(totalXp - sessionBaselineXp) > WOM_UPDATE_XP_THRESHOLD || levelUpThisSession;

		final String nameForWom = sessionPlayerName;
		levelUpThisSession = false;
		sessionBaselineXp = totalXp;

		boolean womShould = womWant && nameForWom != null && !nameForWom.isEmpty()
			&& (!config.womSyncOnlyAfterProgress() || hadProgress);
		boolean clogShould = clogWant && nameForWom != null && !nameForWom.isEmpty();

		if (!womShould && !clogShould && !rosterWant)
		{
			if (sessionPlayerName != null)
			{
				requestFullRefresh();
			}
			return;
		}

		final List<ClogChronicleTracker.Line> chronicleSnap = clogChronicleTracker.snapshotChronicle();

		clientThread.invokeLater(() ->
		{
			final boolean clogShouldFinal = clogShould
				&& !collectionLogUiState.isCollectionLogOpen(client);
			Runnable buildAndEnqueue = () ->
			{
				final String clogJson = clogShouldFinal
					? clogSitePayloadBuilder.buildJson(client, chronicleSnap, "logout-sync")
					: null;
				final String rosterJson = rosterWant
					? clanRosterSitePayloadBuilder.buildJson(client)
					: null;
				worker.execute(() ->
				{
					if (womShould)
					{
						try
						{
							womPlayerUpdateService.requestUpdate(Text.standardize(nameForWom));
						}
						catch (IOException ignored)
						{
						}
					}
					if (clogShouldFinal && clogJson != null)
					{
						clogSiteSyncService.postClogJsonAsync(
							config.clogSyncApiUrl(),
							resolveClanSecret(),
							clogJson);
					}
					if (rosterWant && rosterJson != null)
					{
						clogSiteSyncService.postSiteJsonAsync(
							config.clanRosterSyncApiUrl(),
							resolveClanSecret(),
							rosterJson);
					}
					pullAll();
				});
			};
			if (clogShouldFinal)
			{
				collectionLogObtainedItemsTracker.runBeforeSyncExport(buildAndEnqueue);
			}
			else
			{
				buildAndEnqueue.run();
			}
		});
	}

	private void cancelClogXpSyncDebounce()
	{
		ScheduledFuture<?> t = clogXpSyncDebounce;
		if (t != null)
		{
			t.cancel(false);
			clogXpSyncDebounce = null;
		}
	}

	private void maybeScheduleClogSiteSyncAfterSkillXp(Skill skill, int xpNow)
	{
		if (!isAutomaticClogSiteSyncEnabled())
		{
			return;
		}
		if (discordLoginGrace.inLoginGracePeriod())
		{
			clogLastSkillXp.put(skill, xpNow);
			return;
		}
		Integer previous = clogLastSkillXp.put(skill, xpNow);
		if (previous != null && xpNow > previous)
		{
			scheduleDebouncedClogSiteSyncFromXpGain();
		}
	}

	private void scheduleDebouncedClogSiteSyncFromXpGain()
	{
		if (scheduledExecutor == null || worker == null || worker.isShutdown())
		{
			return;
		}
		cancelClogXpSyncDebounce();
		clogXpSyncDebounce = scheduledExecutor.schedule(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			if (!isAutomaticClogSiteSyncEnabled())
			{
				return;
			}
			clientThread.invokeLater(() -> scheduleClogSiteSyncFromXpWhenSafe());
		}, CLOG_XP_SYNC_DEBOUNCE_SECONDS, TimeUnit.SECONDS);
	}

	private boolean isAutomaticClogSiteSyncEnabled()
	{
		return false;
	}

	private boolean isClogSyncConfigured()
	{
		String u = config.clogSyncApiUrl();
		return u != null && u.trim().startsWith("https://")
			&& !resolveClanSecret().isEmpty();
	}

	private boolean isClanRosterSyncConfigured()
	{
		String u = config.clanRosterSyncApiUrl();
		return u != null && u.trim().startsWith("https://")
			&& !resolveClanSecret().isEmpty();
	}

	/** Clan secret from RuneLite config storage (includes legacy keys). */
	private String resolveClanSecret()
	{
		String st = configManager.getConfiguration(CONFIG_GROUP, "clanSecret");
		if (st == null || st.trim().isEmpty())
		{
			st = config.clanSecret();
		}
		if (st == null || st.trim().isEmpty())
		{
			for (String legacyKey : new String[] {"clogSyncApiSecret", "clanRosterSyncApiSecret", "liveMapApiKey"})
			{
				String legacy = configManager.getConfiguration(CONFIG_GROUP, legacyKey);
				if (legacy != null && !legacy.trim().isEmpty())
				{
					st = legacy;
					break;
				}
			}
		}
		if (st == null)
		{
			return "";
		}
		return st.trim();
	}

	private void cancelClanRosterLoginDelay()
	{
		ScheduledFuture<?> t = clanRosterLoginDelayTask;
		if (t != null)
		{
			t.cancel(false);
			clanRosterLoginDelayTask = null;
		}
	}

	private void scheduleClanRosterSyncAfterLogin()
	{
		if (scheduledExecutor == null || !config.clanRosterSyncEnabled() || !isClanRosterSyncConfigured())
		{
			return;
		}
		cancelClanRosterLoginDelay();
		clanRosterLoginDelayTask = scheduledExecutor.schedule(() ->
		{
			clanRosterLoginDelayTask = null;
			enqueueClanRosterSitePost(true);
		}, 4, TimeUnit.SECONDS);
	}

	private void enqueueClanRosterSitePost(boolean requireAutoSyncEnabled)
	{
		if (requireAutoSyncEnabled && !config.clanRosterSyncEnabled())
		{
			return;
		}
		if (!isClanRosterSyncConfigured())
		{
			return;
		}
		if (worker == null || worker.isShutdown())
		{
			if (!requireAutoSyncEnabled)
			{
				clientThread.invokeLater(() -> rosterManualSyncChat(
					"Cannot post clan roster: plugin background worker is not running. Try toggling the plugin off and on."));
			}
			return;
		}
		final boolean manual = !requireAutoSyncEnabled;
		clientThread.invokeLater(() ->
		{
			if (requireAutoSyncEnabled && !isLocalPlayerJagexClanOwner())
			{
				return;
			}
			if (worker == null || worker.isShutdown())
			{
				if (manual)
				{
					rosterManualSyncChat(
						"Cannot post clan roster: plugin background worker is not running. Try toggling the plugin off and on.");
				}
				return;
			}
			String json = clanRosterSitePayloadBuilder.buildJson(client);
			if (json == null)
			{
				if (manual)
				{
					rosterManualSyncChat(
						"Cannot post clan roster: Jagex clan data is not loaded. Be logged in, open the clan interface or clan channel, then try again.");
				}
				return;
			}
			worker.execute(() -> clogSiteSyncService.postSiteJsonAsync(
				config.clanRosterSyncApiUrl().trim(),
				resolveClanSecret(),
				json,
				manual));
		});
	}

	/** Game chat for manual collection log POST problems (must run on client thread). */
	private void clogManualSyncChat(String message)
	{
		client.addChatMessage(ChatMessageType.CONSOLE, "Terpinheimer", message, "Terpinheimer");
	}

	private void maybeWarnRuneProfileClogConflict()
	{
		if (runeProfileClogConflictWarned || !runeProfilePresence.isEnabled())
		{
			return;
		}
		runeProfileClogConflictWarned = true;
		clientThread.invokeLater(() -> clogManualSyncChat(
			"RuneProfile is enabled: Terpinheimer does nothing with the collection log until you press Home → POST. "
				+ "Use RuneProfile to browse the log; use POST only for the clan site."));
	}

	/** Game chat for manual roster POST problems (must run on client thread). */
	private void rosterManualSyncChat(String message)
	{
		client.addChatMessage(ChatMessageType.CONSOLE, "Terpinheimer", message, "Terpinheimer");
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
		ClanMember member = findLocalPlayerInClanSettings();
		if (member == null)
		{
			return false;
		}
		ClanRank rank = member.getRank();
		return ClanRank.OWNER.equals(rank) || ClanRank.DEPUTY_OWNER.equals(rank);
	}

	/**
	 * Whether the logged-in player is the Jagex clan Owner (not Deputy Owner). Must run on the client thread.
	 */
	public boolean isLocalPlayerJagexClanOwner()
	{
		ClanMember member = findLocalPlayerInClanSettings();
		if (member == null)
		{
			return false;
		}
		ClanRank rank = member.getRank();
		return ClanRank.OWNER.equals(rank);
	}

	/**
	 * Same meaning as {@link #isLocalPlayerJagexClanOwner()} for UI that runs on the Swing EDT: value is
	 * updated each game tick on the client thread (and cleared when not logged in). Safe to call from the panel timer.
	 */
	public boolean isLocalPlayerJagexClanOwnerCached()
	{
		return clanOwnerForRosterAutoSync;
	}

	/** @return clan member row for the local player, or {@code null} */
	private ClanMember findLocalPlayerInClanSettings()
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return null;
		}
		ClanSettings settings = client.getClanSettings();
		if (settings == null)
		{
			return null;
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
		return member;
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
