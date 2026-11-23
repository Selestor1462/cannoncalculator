# Cannon Calculator

<p align="left">
  <img height="20" src="https://img.shields.io/badge/Minecraft-26.3%20%7C%2026.2%20%7C%201.21.x-3C8527?logo=minecraft&logoColor=white" alt="Minecraft" />
  <img height="20" src="https://img.shields.io/badge/Modloader-Fabric-dbb884?logo=fabric&logoColor=white" alt="Fabric" />
  <img height="20" src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" />
</p>

A client-side Minecraft Fabric mod for calculating, encoding, and validating trajectories for the [FTL Pearl Cannon](https://youtu.be/VRyeTVxz5yY) and the [Pig Cannon](https://youtu.be/ItaWA2U5is0).

The main command to open the interface is:
```
/cannon
```
*(You can also use `/cannon gui`)*

---

## Overview

**Cannon Calculator** eliminates external spreadsheets and manual binary math when configuring the cannon in survival and technical Minecraft. The mod computes solutions client-side, maps them onto the cannon's 4×9 selector matrix, and displays in-world holograms showing exactly where blocks and rails must be placed.

- For the **[FTL Pearl Cannon](https://youtu.be/VRyeTVxz5yY)**, the solver calculates travel ticks, TNT charges, distance, exact catcher and landing coordinates, and projects the selector matrix. It also supports sub-block precision (`X.5`, `Z.5`) and auto-generates terrain-adaptive catcher structures.
- For the **[Pig Cannon](https://youtu.be/ItaWA2U5is0)**, the mod calculates four-stage acceleration timings, travel duration, true landing coordinates, and automatically positions landing markers (Twisting Vines) onto the highest terrain block.
- For **Return Pearl Cannons** (versions 1.21.10+), the built-in generator calculates TNT charges, duper sizing, and launch timing to propel an ender pearl back to a designated catch station or origin, automatically assembling a complete ready-to-build `.litematic` schematic with all dupers, aligners, wire bridges, and catcher blocks.

Integrated **EasyPlace** allows placing blocks directly from inventory onto ghost outlines with correct observer and rail rotations.

---

## Commands

| Command | Description |
| :--- | :--- |
| `/cannon` | **Main command**: Opens the calculator GUI |
| `/cannon gui` | Opens the calculator GUI |
| `/cannon generator` | Opens the Return Cannon Generator dialog (1.21.10+) |
| `/cannon generator hud` | Toggles the in-flight chunk scan HUD overlay |
| `/cannon generator rescan` | Restarts the trajectory chunk collision scan |
| `/cannon generator debug` | Toggles / opens Return Cannon debug generator menu |
| `/cannon tp` | Teleports directly to the active catcher block or landing position |
| `/cannon check` | Validates placed in-world blocks against the active encoding plan |
| `/cannon easyplace` | Toggles EasyPlace auto-placement |
| `/cannon rails` | Toggles sequential rails mode |
| `/cannon toggle` | Toggles in-world hologram rendering on or off |
| `/cannon clear` | Clears active calculation and in-world highlights |
| `/cannon hud` | Toggles the action-bar status overlay |
| `/cannon mode <pig\|ftl>` | Switches cannon mode between Pig Cannon and FTL Pearl Cannon |
| `/cannon catcher` | Prints active catcher coordinates and distance |
| `/cannon anchor auto` | Auto-detects nearby cannon anchor structure |
| `/cannon anchor set <x> <z>` | Manually sets cannon anchor coordinates |
| `/cannon anchor set here` | Sets cannon anchor to your current coordinates |

---

## Return Cannon Generator

In Minecraft 1.21.10+, Cannon Calculator includes an in-game Return Cannon generator powered by a pearl trajectory calculator. It calculates the necessary TNT explosion charges, duper sizing, and launch timing to propel an ender pearl back to a designated catch station or origin, automatically assembling a complete ready-to-build `.litematic` schematic with all dupers, aligners, wire bridges, and catcher blocks.

To open the Return Cannon menu, run:
```
/cannon generator
```
*(If you have an active selection in Litematica, it will automatically use its origin; otherwise it defaults to your player position).*

---

## Supported Versions

Dedicated branches are maintained and kept synchronized for each supported Minecraft release:

| Branch | Minecraft Version | Requirements | Features |
| :---: | :---: | :--- | :--- |
| `26.3` | **26.3** | Fabric Loader 0.20.1+, Fabric API 0.166.2+, Litematica, MaLiLib, Java 25 | Pig Cannon, FTL Cannon, Return Cannon Generator, EasyPlace |
| `26.2` | **26.2** | Fabric Loader 0.20.1+, Fabric API 0.161.0+, Litematica, MaLiLib, Java 25 | Pig Cannon, FTL Cannon, Return Cannon Generator, EasyPlace |
| `1.21.11` | **1.21.11** | Fabric Loader 0.19.5+, Fabric API 0.141.6+, Litematica, MaLiLib, Java 25 / 21 | Pig Cannon, FTL Cannon, Return Cannon Generator, EasyPlace |
| `1.21.10` | **1.21.10** | Fabric Loader 0.19.5+, Fabric API 0.138.4+, Litematica, MaLiLib, Java 25 / 21 | Pig Cannon, FTL Cannon, Return Cannon Generator, EasyPlace |
| `1.21` | **1.21** | Fabric Loader 0.16.0+, Fabric API 0.100.0+, Java 21 | Pig Cannon, EasyPlace |

---

## Building from Source

```bash
git clone https://github.com/Selestor1462/cannoncalculator.git
cd cannoncalculator

# Switch to desired branch (e.g., 26.3, 26.2, 1.21.11, 1.21.10, 1.21)
git checkout 26.3

# Build with Gradle (requires JDK 21+ or JDK 25 depending on branch)
./gradlew build
```

Compiled jar files will be located in `build/libs/cannoncalculator-<version>.jar`.

---

## Credits

- Trajectory simulation and mathematical cannon algorithms based on calculators by [garlic bread](https://github.com/garlic-bred).
- Pearl trajectory calculation logic adapted from [PearlCalculatorBlazor by whats2000](https://github.com/whats2000/PearlCalculatorBlazor).
- Return pearl cannon design and redstone architecture by garlic bread, documented in the [Return Cannon Tutorial](https://www.youtube.com/watch?v=MkEJpBAtin8).
- [Litematica](https://github.com/maruohon/litematica) and [MaLiLib](https://github.com/maruohon/malilib) by [masa](https://github.com/maruohon) and [Sakura-Ryoko](https://github.com/Sakura-Ryoko).

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

## AI Disclosure

Portions of this project were developed with the assistance of AI.
