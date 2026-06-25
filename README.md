# Terpinheimer

A RuneLite plugin that adds a clan hub sidebar with Home, Skill of the Week, and Boss of the Week views.

## Features

### Home panel

Announcements, live event status, and configurable quick links.

### SOTW & BOTW tracking

- Live leaderboards from [Wise Old Man](https://wiseoldman.net/)
- Auto-detects SOTW/BOTW competitions
- Shows rankings, countdown timers, and quick links to competition pages

### Clan coffer donations

When a Terpinheimer clan member deposits into the Jagex clan coffer, the plugin posts a thank-you message to the clan Discord (built-in webhook; not configurable in settings).

## Configuration

- Add your **Clan secret** for live map, collection log, roster, and attendance sync

Clan URLs, API endpoints, Wise Old Man group settings, and feature flags are built into the plugin (`TerpinheimerLinks.java`).

### Website-managed content (`GET /api/runelite/plugin-config`)

Announcements and rank permissions are fetched from terpinheimercc.com (cached locally when offline):

```json
{
  "announcements": {
    "enabled": true,
    "text": "Welcome to the Terpinheimer plug in! ..."
  },
  "permissions": {
    "clanRosterPostRankTitles": "Owner,Deputy Owner,Bandosian",
    "clanEventTrackerRankTitles": "Owner,Deputy Owner,Bandosian"
  }
}
```

- **announcements** — Home tab announcement block (overrides plugin settings when present).
- **clanRosterPostRankTitles** — who may auto/manual POST the clan roster (default when omitted: `Owner`).
- **clanEventTrackerRankTitles** — who may use Clan Event tracker (default when omitted: no restriction).

Matching is case-insensitive. Use the exact title text from the game (e.g. `Ruby`, not `Rank 5`).

## Credits

Inspired by existing RuneLite community tools and patterns:

- Wise Old Man integrations for competition tracking and leaderboards
- Structured clan-management workflows inspired by inventory/setup QoL plugins
- clan-event-attendance for clan attendance tracking inspiration
