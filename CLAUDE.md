# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

BestestTool is a Paper Minecraft server plugin ("Auto-Tool" plugin) that automatically switches
a player's held item to the best tool for the block they're about to break, or the best weapon when
attacking a mob. It also has an optional hotbar "Refill" feature. Package root: `net.kccricket.bestesttool`.

## Build

Gradle project, uses the wrapper.

```bash
./gradlew build
```

- Produces a shaded jar at `build/libs/BestestTool-<version>.jar` via the Shadow plugin.
  Shaded/relocated dependencies: `org.bstats`, `com.jeff-media:MorePersistentDataTypes` (relocated under
  `net.kccricket.bestesttool.*`).
- Compiles to Java 21 (`options.release` in `build.gradle.kts`), built against
  `io.papermc.paper:paper-api:26.2.build.+`. `plugin.yml` declares `api-version: "1.20.5"`, the minimum
  supported server version (see `COMPATABILITY.md`) — do not casually lower it back below 1.20.5, since
  the codebase now assumes direct `Material`/`Tag` references that only exist from that floor onward.
- There are no automated tests in this repo. Verify changes by building the jar and manually testing
  against a running Paper server — `./gradlew runServer` spins up a local dev server with the plugin
  already loaded (see the `org.bxteam.runserver` config in `build.gradle.kts`; override the version with
  `-PmcVersion=<version>`).
- `.vscode/launch.json` is configured for remote debug attach on port 5005.

## Material/Tag model

BestestTool's floor is Paper 1.20.5, so `BestToolsUtils.initMap()` builds its Material→Tool lookup table
using direct, compile-time references — no more per-version compatibility scaffolding:

- Materials are referenced directly as `Material.XXX` enum constants. A material that doesn't exist on
  the current Paper API is a compile error, not a silent runtime skip — this is a deliberate trade-off
  for correctness/clarity now that the floor is a modern, actively-maintained API.
- Bulk registration goes through `Tag`-based `tagToMap(...)` calls (e.g. `Tag.LOGS`, `Tag.LEAVES`,
  `Tag.MINEABLE_AXE/HOE/PICKAXE/SHOVEL`) run directly, with no `try`/`catch` version guards — every `Tag`
  referenced here has existed since 1.17 or earlier, well within the 1.20.5+ floor.
- Individual `addToMap(Material, Tool)` calls fill in exceptions the tags don't (or shouldn't) cover:
  torches/instant-break blocks, crops (`Tool.NONE`), leaves/wool/cobweb (`Tool.SHEARS`), and a handful of
  materials verified (via a live-server `toolMap` dump) to need an explicit override even though a
  `Tag.MINEABLE_*` pass runs at the end of `initMap()` (e.g. `GLOWSTONE`, `MOVING_PISTON`,
  `BAMBOO_SAPLING` are not covered by any `MINEABLE_*` tag).
- Enchantments are looked up dynamically through the Bukkit `Registry.ENCHANTMENT` (`EnchantmentUtils.
  getEnchantment`), not deprecated static `Enchantment` fields.
- When adding new block/tool mappings, prefer extending `BestToolsUtils.initMap()`: Tag-based bulk
  registration first, then individual `addToMap(...)` calls for exceptions/overrides. Order matters in
  several places — see the `WATCH OUT FOR ORDER` / `Order important` comments, since some Tag
  registrations intentionally get overwritten by more specific ones immediately after (e.g. stone
  buttons/doors/trapdoors get PICKAXE after AXE). If you think an explicit mapping is now redundant with
  a `Tag.MINEABLE_*` catch-all, verify with a before/after `toolMap` dump on a live server rather than
  removing it on inspection alone — some overrides exist for materials/technical blocks the `MINEABLE_*`
  tags don't cover.
- `Material.getMaterial(String)` still appears in `Blacklist.java` and `CommandBlacklist.java` — that's
  parsing user/config input (arbitrary strings a player typed), not version-compat scaffolding, and
  should stay as-is.

## Runtime architecture

- **`Main`** (the `JavaPlugin`) owns the plugin lifecycle and wires everything together in `load()`,
  which runs on enable and on `/besttools reload`. It also owns per-player state (`playerSettings` map)
  and config defaults/migration triggers.
- **`BestToolsHandler`** holds the built-in-memory lookup tables — `toolMap` (Material → best `Tool`
  enum), plus lists of which materials count as pickaxes/axes/hoes/shovels/swords/weapons/leaves/insta-
  breakable-by-hand — and the core "what's the best item for this block/target" selection logic
  (`getBestToolFromInventory`, `getBestRoscoeFromInventory` for combat, `getBestItemStackFromArray`,
  `moveToolToSlot`, `freeSlot`).
- **`BestToolsUtils`** populates `BestToolsHandler`'s lookup tables at startup (`initMap()`), and *must*
  be constructed after `BestToolsHandler` (`Main.load()` enforces this ordering).
- **`BestToolsListener`** is the event-driven entry point: on `BlockBreakEvent` it re-fires as a delayed
  synthetic `PlayerInteractEvent`-like flow (via `BestToolsNotifyEvent`, 1 tick later) so tool-switching
  happens for the *next* interaction, and separately handles `EntityDamageByEntityEvent` for
  switching to the best sword/axe when attacking mobs.
- **`BestToolsCache`** / **`BestToolsCacheListener`** implement a cheap per-player last-block-type cache
  (`PlayerSetting.btcache`) so repeated interactions with the same block type skip the full lookup —
  invalidated whenever the player's inventory changes. This exists purely for performance (see
  `PerformanceMeter`, toggled via `/besttools performance` and `measure-performance` config option).
- **`PlayerSetting`** is per-player state (enabled flags, hotbar-only, blacklist, favorite slot). It
  persists to the player's `PersistentDataContainer` (via `MorePersistentDataTypes`'s
  `DataType.FILE_CONFIGURATION`) rather than flat files now; legacy `playerdata/<uuid>.yml` files are
  still read once for migration and then deleted (`Main.getPlayerSetting`).
- **`Blacklist`** is a per-player set of materials to never auto-switch for; managed via `/besttools bl ...`
  (`CommandBlacklist`).
- **`RefillListener`/`RefillUtils`** implement the separate `/refill` feature (auto-refilling hotbar stacks
  from the rest of the inventory) — largely independent of the tool-switching logic above.
- **`GUIHandler`/`GUIHolder`** implement an inventory-based settings GUI.
- **`ConfigUpdater`** performs config.yml migrations: on version mismatch (`config-version` in config.yml
  vs. `Main.configVersion`), it renames the old config, regenerates the default, and reinjects the user's
  previous values line-by-line to preserve comments/formatting. Bump `Main.configVersion` and update this
  migration logic together when changing `config.yml`'s shape.
- **`BestToolsPlaceholders`** registers PlaceholderAPI placeholders when PAPI is present (soft depend).
- Commands (`CommandBestTools`, `CommandBlacklist`, `CommandRefill`, `CommandReload`, `CommandDebug`) map
  directly to the subcommands documented in `plugin.yml`.

## Config/versioning conventions

- `config.yml`'s `config-version` must match `Main.configVersion` — bump both together and extend
  `ConfigUpdater` if you add/rename/remove config keys, since migration preserves unknown/old user values
  by keying off the raw YAML line prefixes (`node + ":"`).
- `CHANGELOG.md` is maintained manually per release — add an entry when making user-facing changes.
