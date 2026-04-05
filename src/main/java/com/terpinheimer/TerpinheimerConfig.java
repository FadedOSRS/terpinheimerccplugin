package com.terpinheimer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("terpinheimer")
public interface TerpinheimerConfig extends Config
{
	String SEC_GEN = "terpinheimerGeneral";
	String SEC_DISCORD_WEBHOOK = "terpinheimerDiscordWebhook";
	String SEC_DISCORD_LOOT = "terpinheimerDiscordLoot";
	String SEC_DISCORD_CLUE = "terpinheimerDiscordClue";
	String SEC_DISCORD_PET = "terpinheimerDiscordPet";
	String SEC_DISCORD_COLLECTION = "terpinheimerDiscordCollection";
	String SEC_DISCORD_LEVELS = "terpinheimerDiscordLevels";
	String SEC_DISCORD_PROGRESSION = "terpinheimerDiscordProgression";
	String SEC_LINKS = "terpinheimerLinks";
	String SEC_LIVE_MAP = "terpinheimerLiveMap";
	String SEC_ATTENDANCE = "terpinheimerAttendance";

	@ConfigSection(
		name = "General",
		description = "(Do not edit or change — this may break your plugin.) Clan name, WOM group, refresh, announcements.",
		position = 0,
		closedByDefault = true
	)
	String generalSection = SEC_GEN;

	@ConfigSection(
		name = "Live clan map",
		description = "When enabled, POSTs your position on a schedule. Set API base URL and shared key under Links.",
		position = 1,
		closedByDefault = true
	)
	String liveMapSection = SEC_LIVE_MAP;

	@ConfigSection(
		name = "Discord Notifications — Webhook",
		description = "Single webhook URL for all Terpinheimer Discord notifications (Dink-style).",
		position = 2,
		closedByDefault = true
	)
	String discordWebhookSection = SEC_DISCORD_WEBHOOK;

	@ConfigSection(
		name = "Discord Notifications — Loot",
		description = "NPC and player loot (single global webhook).",
		position = 3,
		closedByDefault = true
	)
	String discordLootSection = SEC_DISCORD_LOOT;

	@ConfigSection(
		name = "Discord Notifications — Clue scrolls",
		description = "Treasure Trail reward chest / casket loot.",
		position = 4,
		closedByDefault = true
	)
	String discordClueSection = SEC_DISCORD_CLUE;

	@ConfigSection(
		name = "Discord Notifications — Pet",
		description = "Pet drop chat messages.",
		position = 5,
		closedByDefault = true
	)
	String discordPetSection = SEC_DISCORD_PET;

	@ConfigSection(
		name = "Discord Notifications — Collection log",
		description = "Collection log chat notifications.",
		position = 6,
		closedByDefault = true
	)
	String discordCollectionSection = SEC_DISCORD_COLLECTION;

	@ConfigSection(
		name = "Discord Notifications — Levels",
		description = "Level-up Discord messages (Dink-style): intervals, templates, screenshots, virtual & combat.",
		position = 7,
		closedByDefault = true
	)
	String discordLevelsSection = SEC_DISCORD_LEVELS;

	@ConfigSection(
		name = "Discord Notifications — Progression",
		description = "Deaths, quests, combat achievements.",
		position = 8,
		closedByDefault = true
	)
	String discordProgressionSection = SEC_DISCORD_PROGRESSION;

	@ConfigSection(
		name = "Clan event attendance",
		description = "Built-in tracker (same idea as Plugin Hub Clan Event Attendance). Jagex clan chat only; open from Home → Clan Event tracker.",
		position = 9,
		closedByDefault = true
	)
	String attendanceSection = SEC_ATTENDANCE;

	@ConfigSection(
		name = "Links",
		description = "Home shortcuts, calendar API, and live map POST credentials (base URL + shared key for {base}/post).",
		position = 10,
		closedByDefault = true
	)
	String linksSection = SEC_LINKS;

	// ---- General ----

	@ConfigItem(keyName = "clanName", name = "Clan name", description = "Shown in the sidebar panel title area", position = 0, section = SEC_GEN)
	default String clanName()
	{
		return "Your clan";
	}

	@Range(min = 1, max = 60)
	@ConfigItem(keyName = "refreshIntervalMinutes", name = "WOM refresh (minutes)", description = "How often to refresh Wise Old Man competition data", position = 1, section = SEC_GEN)
	default int refreshIntervalMinutes()
	{
		return 7;
	}

	@ConfigItem(keyName = "announcementsEnabled", name = "Show announcements", description = "Show the announcements block on Home", position = 2, section = SEC_GEN)
	default boolean announcementsEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "announcementsText",
		name = "Announcements text",
		description = "Static text for the Home tab announcements block (use \\n for line breaks). Only Owner or Deputy Owner (Jagex clan rank, logged in) can save changes; other ranks revert to the last authorized text. Other plugin settings are not rank-locked.",
		position = 3,
		section = SEC_GEN
	)
	default String announcementsText()
	{
		return "Welcome! Check Discord for the latest clan news.";
	}

	@Range(min = 0, max = Integer.MAX_VALUE)
	@ConfigItem(keyName = "womGroupId", name = "Wise Old Man group ID", description = "SOTW and BOTW fetch from this WOM group. Use 0 to use the built-in Terpinheimer group (23745).", position = 4, section = SEC_GEN)
	default int womGroupId()
	{
		return 23745;
	}

	@ConfigItem(keyName = "womUpdateProfileOnLogout", name = "Update WOM profile on logout", description = "When you log out or hop worlds, send a Wise Old Man hiscores update if you gained 10k+ XP or leveled a skill (same idea as the WOM hub plugin). Then refresh this plugin's competition data.", position = 5, section = SEC_GEN)
	default boolean womUpdateProfileOnLogout()
	{
		return true;
	}

	@ConfigItem(keyName = "runeprofileUpdateOnLogout", name = "Update RuneProfile on logout", description = "When the same XP/level condition as the WOM logout update is met, also POST your skills and quests to api.runeprofile.com so your RuneProfile page updates. Requires a Jagex-linked account (account hash). Collection log on the site still benefits from the official RuneProfile plugin.", position = 6, section = SEC_GEN)
	default boolean runeprofileUpdateOnLogout()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clanCalendarPageUrl",
		name = "Clan calendar page URL",
		description = "Opened when you click the Website row on Home. Hash routes (e.g. #/events) are OK.",
		position = 7,
		section = SEC_GEN
	)
	default String clanCalendarPageUrl()
	{
		return "https://terpinheimercc.onrender.com/#/events";
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "sidebarButtonPriority",
		name = "Sidebar icon position",
		description = "Sort order for this plugin's icon on the right sidebar. Lower numbers move it up; higher numbers move it down (same rule as RuneLite's other plugins).",
		position = 8,
		section = SEC_GEN
	)
	default int sidebarButtonPriority()
	{
		return 5;
	}

	// ---- Discord — Webhook ----

	@ConfigItem(
		keyName = "webhookUrl",
		name = "Webhook URL",
		description = "Single Discord webhook URL. All notification types use this URL only.",
		position = 0,
		section = SEC_DISCORD_WEBHOOK,
		secret = true
	)
	default String webhookUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "includePlayerName",
		name = "Include player name",
		description = "Add the local player name to notification embeds.",
		position = 1,
		section = SEC_DISCORD_WEBHOOK
	)
	default boolean includePlayerName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeScreenshot",
		name = "Include screenshot",
		description = "When enabled, attach a game screenshot for deaths (loot and levels use their own Send image toggles).",
		position = 2,
		section = SEC_DISCORD_WEBHOOK
	)
	default boolean includeScreenshot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "discordPreviewToLog",
		name = "Preview messages (debug)",
		description = "If enabled, log the JSON payload instead of sending to Discord (for debugging).",
		position = 3,
		section = SEC_DISCORD_WEBHOOK
	)
	default boolean discordPreviewToLog()
	{
		return false;
	}

	// ---- Discord — Loot ----

	@ConfigItem(
		keyName = "sendLoot",
		name = "Enable loot",
		description = "Notify on NPC and player loot (subject to filters below).",
		position = 0,
		section = SEC_DISCORD_LOOT
	)
	default boolean sendLoot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "lootSendImage",
		name = "Send image",
		description = "Attach a screenshot to loot webhooks (not the global Include screenshot toggle).",
		position = 1,
		section = SEC_DISCORD_LOOT
	)
	default boolean lootSendImage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lootShowLootIcons",
		name = "Show loot icons",
		description = "Add a thumbnail to the embed from the first item (wiki-style URL; may 404 for some items).",
		position = 2,
		section = SEC_DISCORD_LOOT
	)
	default boolean lootShowLootIcons()
	{
		return false;
	}

	@Range(min = 0, max = 2_147_483_647)
	@ConfigItem(
		keyName = "lootValueThreshold",
		name = "Min loot value",
		description = "Minimum total GE value before a loot notification is sent (0 = always, if rarity rules pass).",
		position = 3,
		section = SEC_DISCORD_LOOT
	)
	default int lootValueThreshold()
	{
		return 500_000;
	}

	@ConfigItem(
		keyName = "lootIncludePkLoot",
		name = "Include PK loot",
		description = "Include loot from player kills (Player loot received).",
		position = 4,
		section = SEC_DISCORD_LOOT
	)
	default boolean lootIncludePkLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lootIncludeBaGambles",
		name = "Include BA gambles",
		description = "Include Barbarian Assault / gamble-style sources (matched by source name).",
		position = 5,
		section = SEC_DISCORD_LOOT
	)
	default boolean lootIncludeBaGambles()
	{
		return false;
	}

	@ConfigItem(
		keyName = "lootIncludeClueLoot",
		name = "Include clue loot",
		description = "Include clue / treasure chest style loot here. Turn off to only use the Clue scrolls section.",
		position = 6,
		section = SEC_DISCORD_LOOT
	)
	default boolean lootIncludeClueLoot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "lootItemAllowlist",
		name = "Item allowlist",
		description = "If non-empty, at least one dropped item name must match a line (substring, one entry per line).",
		position = 7,
		section = SEC_DISCORD_LOOT
	)
	default String lootItemAllowlist()
	{
		return "";
	}

	@ConfigItem(
		keyName = "lootItemDenylist",
		name = "Item denylist",
		description = "Never notify if any dropped item matches a line (substring).",
		position = 8,
		section = SEC_DISCORD_LOOT
	)
	default String lootItemDenylist()
	{
		return "";
	}

	@ConfigItem(
		keyName = "lootSourceDenylist",
		name = "Source denylist",
		description = "Never notify if the loot source (NPC name) matches a line (substring).",
		position = 9,
		section = SEC_DISCORD_LOOT
	)
	default String lootSourceDenylist()
	{
		return "";
	}

	@Range(min = 0, max = 10_000)
	@ConfigItem(
		keyName = "lootRarityOneInX",
		name = "Rarity override (1 in X)",
		description = "0 = off. When > 0, uses an extra rarity check on the largest stack value vs total (approximate rare-drop filter).",
		position = 10,
		section = SEC_DISCORD_LOOT
	)
	default int lootRarityOneInX()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "lootRequireRarityAndValue",
		name = "Require both rarity and value",
		description = "When rarity override is on: if checked, both value threshold and rarity must pass; if off, either can pass.",
		position = 11,
		section = SEC_DISCORD_LOOT
	)
	default boolean lootRequireRarityAndValue()
	{
		return false;
	}

	@ConfigItem(
		keyName = "lootNotificationMessage",
		name = "Notification message",
		description = "Placeholders: %USERNAME%, %LOOT% (wiki markdown lines), %SOURCE% (wiki link), %VALUE% (compact gp). Use \\n for new lines.",
		position = 12,
		section = SEC_DISCORD_LOOT
	)
	default String lootNotificationMessage()
	{
		return "%USERNAME% has looted:\n\n%LOOT%\n\nFrom: %SOURCE%";
	}

	// ---- Discord — Clue scrolls ----

	@ConfigItem(
		keyName = "sendClueScrolls",
		name = "Enable clue scrolls",
		description = "Notify on Treasure Trail reward loot (separate from generic loot).",
		position = 0,
		section = SEC_DISCORD_CLUE
	)
	default boolean sendClueScrolls()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clueSendImage",
		name = "Send image",
		description = "Attach a screenshot to clue reward webhooks.",
		position = 1,
		section = SEC_DISCORD_CLUE
	)
	default boolean clueSendImage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clueShowItemIcons",
		name = "Show item icons",
		description = "Thumbnail from the first reward item (wiki-style URL).",
		position = 2,
		section = SEC_DISCORD_CLUE
	)
	default boolean clueShowItemIcons()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clueMinTier",
		name = "Min tier",
		description = "Minimum clue tier (from recent chat or default).",
		position = 3,
		section = SEC_DISCORD_CLUE
	)
	default ClueNotifyTier clueMinTier()
	{
		return ClueNotifyTier.MEDIUM;
	}

	@Range(min = 0, max = 2_147_483_647)
	@ConfigItem(
		keyName = "clueMinValue",
		name = "Min value",
		description = "Minimum total GE value of the reward to notify.",
		position = 4,
		section = SEC_DISCORD_CLUE
	)
	default int clueMinValue()
	{
		return 500_000;
	}

	@ConfigItem(
		keyName = "clueNotificationMessage",
		name = "Notification message",
		description = "Placeholders: %USERNAME%, %CLUE%, %COUNT%, %LOOT% (wiki lines), %VALUE% (compact gp). Use \\n for new lines.",
		position = 5,
		section = SEC_DISCORD_CLUE
	)
	default String clueNotificationMessage()
	{
		return "%USERNAME% has completed a\n%CLUE% clue, they have completed\n%COUNT%.\nThey obtained:\n\n%LOOT%";
	}

	// ---- Discord — Pet ----

	@ConfigItem(
		keyName = "sendPet",
		name = "Enable pets",
		description = "Notify on pet-related game messages.",
		position = 0,
		section = SEC_DISCORD_PET
	)
	default boolean sendPet()
	{
		return false;
	}

	@ConfigItem(
		keyName = "petSendImage",
		name = "Send image",
		description = "Attach a screenshot to pet webhooks.",
		position = 1,
		section = SEC_DISCORD_PET
	)
	default boolean petSendImage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "petNotifyMessage",
		name = "Notification message",
		description = "Placeholders: %USERNAME%, %GAME_MESSAGE%. Use \\n for new lines.",
		position = 2,
		section = SEC_DISCORD_PET
	)
	default String petNotifyMessage()
	{
		return "%USERNAME% %GAME_MESSAGE%";
	}

	// ---- Discord — Collection log ----

	@ConfigItem(
		keyName = "sendCollectionLog",
		name = "Enable collection log",
		description = "Notify on collection log game messages.",
		position = 0,
		section = SEC_DISCORD_COLLECTION
	)
	default boolean sendCollectionLog()
	{
		return false;
	}

	@ConfigItem(
		keyName = "collectionLogSendImage",
		name = "Send image",
		description = "Attach a screenshot to collection log webhooks.",
		position = 1,
		section = SEC_DISCORD_COLLECTION
	)
	default boolean collectionLogSendImage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "collectionLogItemDenylist",
		name = "Item denylist",
		description = "Skip notify if the collected item name matches a line (substring).",
		position = 2,
		section = SEC_DISCORD_COLLECTION
	)
	default String collectionLogItemDenylist()
	{
		return "";
	}

	@ConfigItem(
		keyName = "collectionLogMessage",
		name = "Notification message",
		description = "Placeholders: %USERNAME%, %ITEM% (wiki link), %GAME_MESSAGE%. Use \\n for new lines.",
		position = 3,
		section = SEC_DISCORD_COLLECTION
	)
	default String collectionLogMessage()
	{
		return "%USERNAME% has added %ITEM% to their collection";
	}

	// ---- Discord — Levels ----

	@ConfigItem(
		keyName = "sendLevels",
		name = "Enable level",
		description = "Send Discord notifications for skill level ups (subject to the options below).",
		position = 0,
		section = SEC_DISCORD_LEVELS
	)
	default boolean sendLevels()
	{
		return false;
	}

	@ConfigItem(
		keyName = "levelNotifySendImage",
		name = "Send image",
		description = "Attach a game screenshot to level-up webhooks (independent of global loot screenshots).",
		position = 1,
		section = SEC_DISCORD_LEVELS
	)
	default boolean levelNotifySendImage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "levelNotifyVirtualLevels",
		name = "Notify on virtual levels",
		description = "After 99, notify when your virtual level (from XP) increases.",
		position = 2,
		section = SEC_DISCORD_LEVELS
	)
	default boolean levelNotifyVirtualLevels()
	{
		return true;
	}

	@ConfigItem(
		keyName = "levelNotifyCombatLevels",
		name = "Notify for combat levels",
		description = "Send a separate webhook when your combat level increases.",
		position = 3,
		section = SEC_DISCORD_LEVELS
	)
	default boolean levelNotifyCombatLevels()
	{
		return false;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "levelNotifyEveryNLevels",
		name = "Notify interval",
		description = "Only notify when the new level is a multiple of this (e.g. 5 → 60, 65, 70). Use 1 to notify every level.",
		position = 4,
		section = SEC_DISCORD_LEVELS
	)
	default int levelNotifyEveryNLevels()
	{
		return 5;
	}

	@Range(min = 1, max = 126)
	@ConfigItem(
		keyName = "levelNotifyMinSkillLevel",
		name = "Minimum skill level",
		description = "Only notify when the new level is at least this value.",
		position = 5,
		section = SEC_DISCORD_LEVELS
	)
	default int levelNotifyMinSkillLevel()
	{
		return 60;
	}

	@Range(min = 1, max = 126)
	@ConfigItem(
		keyName = "levelNotifyIntervalOverrideLevel",
		name = "Interval override level",
		description = "When the new level is at or above this, use \"Interval after override\" instead of \"Notify interval\".",
		position = 6,
		section = SEC_DISCORD_LEVELS
	)
	default int levelNotifyIntervalOverrideLevel()
	{
		return 90;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "levelNotifyEveryNLevelsAfterOverride",
		name = "Interval after override",
		description = "Same as notify interval, but applied when new level ≥ the override level (1 = every level).",
		position = 7,
		section = SEC_DISCORD_LEVELS
	)
	default int levelNotifyEveryNLevelsAfterOverride()
	{
		return 1;
	}

	@Range(min = 0, max = 2_000_000_000)
	@ConfigItem(
		keyName = "levelNotifyPost99XpInterval",
		name = "Post-99 XP interval",
		description = "After level 99, minimum XP gained (on that skill) between notifications when virtual levels are off. 0 disables this throttle.",
		position = 8,
		section = SEC_DISCORD_LEVELS
	)
	default int levelNotifyPost99XpInterval()
	{
		return 5_000_000;
	}

	@ConfigItem(
		keyName = "levelNotifyMessage",
		name = "Notification message",
		description = "Embed description (Discord markdown). Default bolds the skill: **%SKILL%**. Placeholders: %USERNAME%, %SKILL%, %LEVEL%, %XP%, %VLEVEL%. Use \\n for new lines.",
		position = 9,
		section = SEC_DISCORD_LEVELS
	)
	default String levelNotifyMessage()
	{
		return "%USERNAME% has levelled **%SKILL%** to %LEVEL%";
	}

	@ConfigItem(
		keyName = "levelCombatNotifyMessage",
		name = "Combat level message",
		description = "Combat level template. Placeholders: %USERNAME%, %COMBATLEVEL%, %COMBAT%.",
		position = 10,
		section = SEC_DISCORD_LEVELS
	)
	default String levelCombatNotifyMessage()
	{
		return "%USERNAME% is now combat level %COMBATLEVEL%";
	}

	// ---- Discord — Progression ----

	@ConfigItem(
		keyName = "sendDeaths",
		name = "Send deaths",
		description = "Notify when your character dies.",
		position = 0,
		section = SEC_DISCORD_PROGRESSION
	)
	default boolean sendDeaths()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sendQuests",
		name = "Send quests",
		description = "Notify on quest completion messages in chat.",
		position = 1,
		section = SEC_DISCORD_PROGRESSION
	)
	default boolean sendQuests()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sendCombatAchievements",
		name = "Send combat achievements",
		description = "Notify on combat achievement messages in chat.",
		position = 2,
		section = SEC_DISCORD_PROGRESSION
	)
	default boolean sendCombatAchievements()
	{
		return false;
	}

	// ---- Links ----

	@ConfigItem(keyName = "linkDiscord", name = "Discord invite", description = "Invite or server link opened from Home (leave empty to hide the button).", position = 0, section = SEC_LINKS, secret = true)
	default String linkDiscord()
	{
		return "https://discord.gg/NTWqmhSx4U";
	}

	@ConfigItem(keyName = "linkNameChanges", name = "Name changes channel", description = "Discord channel URL (optional).", position = 1, section = SEC_LINKS, secret = true)
	default String linkNameChanges()
	{
		return "https://discord.com/channels/1392895914762567861/1392903002507182150";
	}

	@ConfigItem(keyName = "linkAnnouncements", name = "Announcements channel", description = "Discord channel URL (optional).", position = 2, section = SEC_LINKS, secret = true)
	default String linkAnnouncements()
	{
		return "https://discord.com/channels/1392895914762567861/1460796088524210276";
	}

	@ConfigItem(keyName = "linkEvents", name = "Events channel", description = "Discord channel URL (optional).", position = 3, section = SEC_LINKS, secret = true)
	default String linkEvents()
	{
		return "https://discord.com/channels/1392895914762567861/1392903038066491432";
	}

	@ConfigItem(
		keyName = "linkWebsite",
		name = "Website",
		description = "Clan website URL opened from Home → Links → Website (leave empty to hide the button).",
		position = 4,
		section = SEC_LINKS,
		secret = true
	)
	default String linkWebsite()
	{
		return "https://terpinheimercc.onrender.com/";
	}

	@ConfigItem(keyName = "linkWiseOldManGroup", name = "Wise Old Man group", description = "Opens your clan group on wiseoldman.net (optional).", position = 5, section = SEC_LINKS, secret = true)
	default String linkWiseOldManGroup()
	{
		return "https://wiseoldman.net/groups/23745";
	}

	@ConfigItem(keyName = "linkLiveClanMap", name = "Live clan map", description = "Opens your live map page in the browser (optional; you host the site that consumes the Live clan map API).", position = 6, section = SEC_LINKS, secret = true)
	default String linkLiveClanMap()
	{
		return "https://terpinheimercc.onrender.com/#/map";
	}

	@ConfigItem(
		keyName = "clanCalendarSummaryApiUrl",
		name = "Clan calendar summary API (GET)",
		description = "HTTPS GET URL returning JSON: use ?format=array for a list of events (startsAt/endsAt), or a summary object (activeCount, phase, etc.). Drives Website status and Web Events list. API authorization is built into the plugin.",
		position = 7,
		section = SEC_LINKS
	)
	default String clanCalendarSummaryApiUrl()
	{
		return "https://terpinheimercc.onrender.com/api/runelite/clan-calendar-summary?format=array";
	}

	@ConfigItem(
		keyName = "liveMapApiBaseUrl",
		name = "Live map API base URL",
		description = "HTTPS base only, no path — the live map plugin POSTs to {base}/post. Configure with Live clan map → Enable.",
		position = 8,
		section = SEC_LINKS,
		secret = true
	)
	default String liveMapApiBaseUrl()
	{
		return "https://terpinheimercc.onrender.com";
	}

	@ConfigItem(
		keyName = "liveMapApiKey",
		name = "Live map API shared key",
		description = "Sent as the Authorization header on live map POSTs (Goblin Scape pattern).",
		position = 9,
		section = SEC_LINKS,
		secret = true
	)
	default String liveMapApiKey()
	{
		return "";
	}

	// ---- Clan event attendance ----

	@Range(min = 0, max = 7200)
	@ConfigItem(
		keyName = "attendancePresentThresholdSeconds",
		name = "Present time threshold",
		description = "Minimum time in the area (logged) to count in the “Present members” list when the event stops.",
		position = 0,
		section = SEC_ATTENDANCE
	)
	@Units(Units.SECONDS)
	default int attendancePresentThresholdSeconds()
	{
		return 600;
	}

	@Range(min = 0, max = 7200)
	@ConfigItem(
		keyName = "attendanceLateThresholdSeconds",
		name = "Late threshold",
		description = "If “late” column is on, arrival after this many seconds from event start is marked late.",
		position = 1,
		section = SEC_ATTENDANCE
	)
	@Units(Units.SECONDS)
	default int attendanceLateThresholdSeconds()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "attendanceShowLateColumn",
		name = "Show late column",
		description = "Include a Late column in the text report.",
		position = 2,
		section = SEC_ATTENDANCE
	)
	default boolean attendanceShowLateColumn()
	{
		return true;
	}

	@ConfigItem(
		keyName = "attendanceDiscordCodeFence",
		name = "Discord code block on stop",
		description = "When you stop the event, wrap the final report in ``` for Discord.",
		position = 3,
		section = SEC_ATTENDANCE
	)
	default boolean attendanceDiscordCodeFence()
	{
		return false;
	}

	@ConfigItem(
		keyName = "attendanceConfirmStartStop",
		name = "Confirm start / stop",
		description = "Ask before starting (clears data) or stopping (finalizes report).",
		position = 4,
		section = SEC_ATTENDANCE
	)
	default boolean attendanceConfirmStartStop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "attendanceBlockCopyWhileRunning",
		name = "Block copy while event runs",
		description = "When on, Copy is only enabled after you stop the event (like the hub plugin option).",
		position = 5,
		section = SEC_ATTENDANCE
	)
	default boolean attendanceBlockCopyWhileRunning()
	{
		return false;
	}

	// ---- Live clan map ----

	@ConfigItem(
		keyName = "liveMapEnabled",
		name = "Enable live map API",
		description = "When on, periodically POST your position to Links → Live map API base URL ({base}/post).",
		position = 0,
		section = SEC_LIVE_MAP
	)
	default boolean liveMapEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "liveMapEventHide",
		name = "Event: hide on live map",
		description = "While on, your position is never sent to the live map API (hide and seek, events, etc.). Your pin may linger on the website until your server expires stale data.",
		position = 1,
		section = SEC_LIVE_MAP
	)
	default boolean liveMapEventHide()
	{
		return false;
	}

	@ConfigItem(
		keyName = "liveMapSendInWilderness",
		name = "Send position in Wilderness",
		description = "When off, positions are not sent while the Wilderness varbit is set (matches Goblin Scape default).",
		position = 2,
		section = SEC_LIVE_MAP
	)
	default boolean liveMapSendInWilderness()
	{
		return false;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "liveMapIntervalTicks",
		name = "Update every N game ticks",
		description = "1 = every tick (~0.6s), like the reference plugin; higher values reduce API traffic.",
		position = 3,
		section = SEC_LIVE_MAP
	)
	default int liveMapIntervalTicks()
	{
		return 5;
	}
}
