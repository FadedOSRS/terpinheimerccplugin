# Terpinheimer

A RuneLite plugin that adds a clan hub sidebar with Home, Skill of the Week, and Boss of the Week views.

## Features

### Home panel

Announcements, live event status, and configurable quick links.

### SOTW & BOTW tracking

- Live leaderboards from [Wise Old Man](https://wiseoldman.net/)
- Auto-detects SOTW/BOTW competitions
- Shows rankings, countdown timers, and quick links to competition pages

### Discord integration (optional)

- One webhook for enabled notifications
- Sends notifications for loot, levels, clues, pets, collection log, and deaths
- Supports embedded messages and optional screenshots

### Profile sync

Optionally updates Wise Old Man on logout when XP thresholds are met.

## Configuration

- Set your **Wise Old Man group ID**
- Add optional quick links (Discord, WOM group URL, channels, etc.)
- Configure one Discord webhook and notification settings

### Website permissions (`GET /api/runelite/plugin-config`)

Clan officers can control who may use sensitive features by listing **in-game rank titles** (the same labels shown in the Jagex clan panel and roster sync), comma-separated:

```json
{
  "permissions": {
    "clanRosterPostRankTitles": "Owner,Deputy Owner,Ruby",
    "clanEventTrackerRankTitles": "Deputy Owner,Ruby,Administrator"
  }
}
```

- **`clanRosterPostRankTitles`** — who may auto/manual POST the clan roster (default when omitted: `Owner`).
- **`clanEventTrackerRankTitles`** — who may open Clan Event tracker and start/stop/post attendance (default when omitted: no restriction — any logged-in player).

Matching is case-insensitive. Use the exact title text from the game (e.g. `Ruby`, not `Rank 5`).

## Credits

Inspired by existing RuneLite community tools and patterns:

- Wise Old Man integrations for competition tracking and leaderboards
- Notification and webhook systems similar to Dink
- Structured clan-management workflows inspired by inventory/setup QoL plugins
- clan-event-attendance for clan attendance tracking inspiration
