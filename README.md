<div align="center">

# ⚔️ Pojav Tier Tagger

**A sleek, performance-friendly Fabric client mod for Minecraft 1.21.11 that seamlessly integrates MMPvP rankings into your HUD, tab list, and chat.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen.svg?style=for-the-badge&logo=minecraft)](https://fabricmc.net/)
[![Fabric](https://img.shields.io/badge/Modloader-Fabric-blue.svg?style=for-the-badge)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg?style=for-the-badge&logo=openjdk)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Join%20Community-5865F2.svg?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/QS3NpPM2dB)

<br/>

> **Example Display:** `[🪓 HT1] | PlayerName`  
> *Dynamically updates based on live MMPvP player ranking data.*

---

</div>

## 🌟 Overview

**Pojav Tier Tagger** pulls live ranking data directly from the MMPvP tier list and attaches a dynamic badge to every player in-game. The mod automatically calculates each player's highest-scoring gamemode and displays their tier alongside custom bitmap icons above player heads, in the tab overlay, and within chat channels.

Everything runs via asynchronous background polling, ensuring **zero impact on your frame rates or gameplay performance**.

---

## ✨ Core Features

* **🎛️ Universal Visibility:** Displays badges above player heads, in the tab list, and inside chat.
* **⚡ Smart Tier Calculation:** Automatically identifies and highlights a player's absolute best gamemode and rank without manual configuration.
* **🎨 Custom Icons:** Features custom bitmap font icons for all **8 supported gamemodes**:
  `Sword` • `Mace` • `SMP` • `Pot` • `Vanilla` • `NethOP` • `UHC` • `Axe`
* **⚙️ Mod Menu Integration:** Easily adjust display colors, refresh intervals, and surface visibility using an in-game config UI.
* **🔍 Instant Lookups:** Run `/pojavtier player <name>` for immediate on-demand player stat lookups.
* **🚀 Lightweight Engine:** Efficient background API polling keeps your game stutter-free.

---

## 🛠️ Requirements

| Dependency | Required Version |
| :--- | :--- |
| **Minecraft** | `1.21.11` |
| **Fabric Loader** | `0.18.1+` |
| **Java** | `21+` |
| **Fabric API** | `Latest for 1.21.11` |
| **Mod Menu** *(Optional)* | For in-game settings screen |

---

## 📥 Installation

1. Download and install **[Fabric Loader](https://fabricmc.net/use/)** for **1.21.11**.
2. Download **[Fabric API](https://modrinth.com/mod/fabric-api)** and drop the `.jar` into your `.minecraft/mods` folder.
3. Download the latest release of `pojavtiertagger-*.jar` and place it in your `mods` directory.
4. *(Optional)* Add **[Mod Menu](https://modrinth.com/mod/modmenu)** to configure settings directly in-game.
5. Launch Minecraft and dominate the arena!

---

## ⚙️ Configuration

You can customize the mod via the **Mod Menu** interface or by editing `.minecraft/config/pojavtiertagger.json`.

<details>
<summary><b>🔧 Configurable Settings</b></summary>

* **Tier Color Schemes:** Assign custom hex colors to distinct rank tiers.
* **Refresh Intervals:** Control how frequently live ranking data is fetched from the API.
* **Surface Toggles:** Enable or disable tier visibility separately for **Nametags**, **Tab List**, or **Chat**.

</details>

---

## 📜 Changelog

<details>
<summary><b>v1.21.11</b> (Current Target)</summary>

* **Ported:** Updated target to Minecraft `1.21.11` (Fabric Loom `1.14`, Yarn `1.21.11+build.3`, Fabric Loader `0.18.1`, Fabric API `0.141.2+1.21.11`, Java `21`).
* **API Fixes:** Adapted to `GameProfile` accessor renames, `Style.withFont()` requiring `StyleSpriteSource`, `KeyBinding` category adjustments, and `CyclingButtonWidget.builder()` signatures.
* **Build System:** `fabric.mod.json` dependency ranges are now automatically generated from `gradle.properties` at build time.

</details>

<details>
<summary><b>v1.0.1</b></summary>

* **Fix:** Resolved issue where gamemode icons defaulted to the sword emoji.
* **API Upgrade:** Switched to `/api/rankings` endpoint, parsing full per-gamemode `ranks` maps.
* **Syncing:** Synchronized gamemode icons and tier text calculations.
* **Commands:** Updated `/pojavtier player <name>` to return accurate point breakdowns.

</details>

<details>
<summary><b>v1.0.0</b></summary>

* Initial release.

</details>

---

## 💬 Community & Credits

<div align="center">

[![Discord Banner](https://img.shields.io/discord/1000000000000000000?color=7289da&label=Discord&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/QS3NpPM2dB)

**Join our Discord community:** [discord.gg/QS3NpPM2dB](https://discord.gg/QS3NpPM2dB)

</div>

<br/>

> **About the Developer:**  
> Made by a solo dev living on caffeine and stack traces. Built from scratch — updated logic, toolchain ports, and API hooks — for the MMPvP community. ❤️

---

<div align="center">

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for details.

</div>too many failed CI runs. All of it made with love, just for the MMPvP community. ❤️
