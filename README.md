# Terpinheimer

RuneLite plugin: clan hub sidebar with **Home** (announcements, event status, quick links), **Skill of the Week** and **Boss of the Week** leaderboards from [Wise Old Man](https://wiseoldman.net/), and optional **Discord webhook** notifications (loot, levels, clues, pets, collection log, deaths, etc.).

## Features

- WOM group competitions: auto-detects SOTW / BOTW-style titles and shows top players, countdowns, and links to open competitions on the website.
- Optional profile update on logout when XP thresholds are met (WOM API).
- Discord: single webhook URL, Dink-style embeds where applicable; screenshots use `attachment://` so images sit in the embed.

## Configuration

- Set **Wise Old Man group ID** under General (`0` = not configured; enter your group’s numeric ID).
- Fill **Links** (Discord, WOM group URL, etc.); empty link fields disable those buttons on Home.
- Discord sections: webhook URL, per-category toggles, filters, and message templates.

## Building & running locally

Requires **Java 11** (see the [RuneLite Developer Guide](https://github.com/runelite/runelite/wiki/Developer-Guide)).

```bash
./gradlew run
```

Build the jar used by the client / hub CI:

```bash
./gradlew build
```

Output: `build/libs/terpinheimer-<version>-all.jar` (shadow jar).

Optional: pin the client version with `-Prunelite.version=1.x.x` if `latest.release` causes issues.

## Submitting to the RuneLite Plugin Hub

Follow the official [plugin-hub README](https://github.com/runelite/plugin-hub/blob/master/README.md):

1. Push this project to a **public** GitHub repository.
2. Add **`LICENSE`** (this repo uses BSD 2-Clause, same family as RuneLite).
3. Edit **`runelite-plugin.properties`**: set **`author`**, **`support`** (usually `https://github.com/<you>/<repo>/issues`), and keep **`plugins`** pointing at `com.terpinheimer.TerpinheimerPlugin`.
4. Optionally add **`icon.png`** at the repo root (max **48×72** px per hub docs).
5. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub), add `plugins/<your-plugin-id>` with `repository=` and `commit=` (full 40-char hash), open a PR.

This project uses **only** dependencies already provided by `runelite-client` (e.g. Gson, OkHttp via Guice), so you should **not** need extra third-party dependency verification in the hub.

## Jagex third-party guidelines

Ensure behavior stays within [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1). This plugin does not interact with the game beyond normal RuneLite APIs (UI, events, screenshots, config).
