# Pojav Tier Tagger

A Fabric client mod for Minecraft 1.21.11 that tags players with their PvP tier and gamemode icon — above their head, in the tab list, and in chat.

## About

Pojav Tier Tagger pulls live ranking data from the MMPvP tier list and displays a small badge next to every player's name in-game, formatted as `[gamemode emoji] [tier] | <username>` (for example, a Mace HT1 player shows the mace icon next to `HT1 | PlayerName`). The badge always reflects the gamemode the player is actually best-ranked in, picked automatically from their full set of gamemode tiers. Data refreshes automatically in the background, and a keybind lets you manually cycle through additional info for a targeted player.

## Features

- Tier + gamemode badges above player heads, in the tab list, and in chat
- Automatically picks the player's single best-scoring gamemode and tier from their full ranking data (no manual configuration needed)
- Custom bitmap font icons for all 8 supported gamemodes: Sword, Mace, SMP, Pot, Vanilla, NethOP, UHC, Axe
- In-game config screen (via Mod Menu) for colors, refresh interval, and display options
- `/pojavtier player <name>` command for on-demand lookups
- Lightweight background polling of the MMPvP rankings API — no impact on gameplay performance

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **0.18.1+**
- Fabric API
- Java **21+**
- Mod Menu (optional, only needed for the in-game config screen)

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11 and place it in your `mods` folder.
3. Drop the `pojavtiertagger-*.jar` into your `mods` folder.
4. (Optional) Install [Mod Menu](https://modrinth.com/mod/modmenu) if you want an in-game settings screen.
5. Launch the game.

## Config

Open the mod's settings from Mod Menu, or edit `config/pojavtiertagger.json` directly. Available options include tier colors, refresh interval, and which surfaces (nametag / tab / chat) show badges.

## License

MIT License — see [LICENSE](LICENSE) for the full text. You're free to use, modify, and redistribute this mod, including in modpacks, provided the original copyright and license notice are retained.

## Changelog

### v1.21.11
- Ported to Minecraft 1.21.11 (Fabric Loom 1.14, Yarn `1.21.11+build.3`, Fabric Loader `0.18.1`, Fabric API `0.141.2+1.21.11`, Java 21)
- Updated for Minecraft API changes in this version: `GameProfile` accessor rename, `Style.withFont()` now takes a `StyleSpriteSource`, `KeyBinding` now requires a `KeyBinding.Category`, and `CyclingButtonWidget.builder()`'s new two-argument signature
- `fabric.mod.json`'s `minecraft` and `fabricloader` dependency ranges are now generated from `gradle.properties` at build time instead of being hardcoded, so they can't drift out of sync with the actual target version again

### v1.0.1
- Fixed the gamemode icon always showing the sword emoji regardless of the player's actual gamemode
- Switched to the real MMPvP rankings API (`/api/rankings`), parsing the full per-gamemode `ranks` map instead of a placeholder single-tier field
- The badge's gamemode icon and tier text are now computed together from whichever gamemode the player scores highest in, so they always match
- `/pojavtier player <name>` now shows accurate points and which gamemode the player's best tier came from

### v1.0.0
- Initial release
- Displays player PvP tiers with gamemode icons in nametags, tab list, and chat
- In-game config screen via Mod Menu

## Community

💬 Join the Discord: [discord.gg/QS3NpPM2dB](https://discord.gg/QS3NpPM2dB)

**About the dev:** Made by a slightly unhinged solo dev who lives on caffeine and stack traces. Built this whole thing from scratch — renamed it, fixed the tier/gamemode logic, ported the entire toolchain to 1.21.11 — through pure stubbornness and way too many failed CI runs. All of it made with love, just for the MMPvP community. ❤️
