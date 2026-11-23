# Changelog

All notable changes to **Cannon Calculator** are documented in this file.

## [1.1.0] - 2026-11-23

### Hotfix
- **Item Duplication in Singleplayer**: Fixed client/server inventory desynchronization by replacing client-side-only slot picking (`inv.pickSlot(i)`) with proper container clicks (`ClickType.SWAP` / `ContainerInput.SWAP`). Eliminated integrated server-thread mutations (`server.execute`), direct `setBlock` calls, and thread-contending manual inventory decrements (`shrink(1)`) that previously led to duplicate items and watchdog tick timeouts.
- **EasyPlace Placement Restrictions**: Fixed coordinate space handling in `isNearCannonStructure` so holding RMB when out of required items (such as rails) reliably cancels placing unintended extra blocks (e.g. black stained glass) around the cannon.
- **Accurate Block Placement Protocol**: Standardized placement across both singleplayer and multiplayer using the Servux V3 / Carpet V2 Accurate Block Placement protocol through `gameMode.useItemOn(...)`.

### Added
- **Moved Return Cannon Generator**: Fully integrated the Return Cannon generator from the Schematic Tiling mod (`schematic-tiling-javafied`).
  - Ender pearl trajectory solver and TNT explosion charge calculator.
  - Red / Blue duper sizing and launch timing computation.
  - Automated `.litematic` schematic assembly with aligners, dupers, and wire bridges.
  - Interactive 3D trajectory particle and line renderers.
  - Route collision scanner and chunk tracking HUD.
  - Color palette selection and custom destinations manager.
- **Russian Localization**: Added comprehensive Russian translations across the cannon encoder, EasyPlace, and Return Cannon menus.

---

## [1.0.0] - 2026-11-22

### Added
- Initial release of Cannon Calculator for Minecraft 26.3, 26.2, 1.21.11, 1.21.10, and 1.21.
- FTL Pearl Cannon trajectory calculator and binary selector matrix projecting.
- Pig Cannon four-stage acceleration timings and true landing prediction.
- Integrated EasyPlace with observer and rail orientation support.
- In-world status HUD and Sequential Rails mode.
