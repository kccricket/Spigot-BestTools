# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

BestTools is a Spigot/Paper Minecraft server plugin ("Auto-Tool" plugin) that automatically switches
a player's held item to the best tool for the block they're about to break, or the best weapon when
attacking a mob. It also has an optional hotbar "Refill" feature. Package root: `de.jeff_media.BestTools`.

## Build

Maven project, no wrapper.

```bash
mvn clean package
```

- Produces a shaded jar at `target/BestTools-<version>.jar` (final name set via `maven-jar-plugin`, shading via
  `maven-shade-plugin`). Shaded/relocated dependencies: `com.jeff_media.updatechecker`, `org.bstats`,
  `com.jeff_media.morepersistentdatatypes` (relocated under `de.jeff_media.BestTools.*`).
- Compiles to Java 11 (`maven.compiler.release`). Target Spigot API is `1.21.1-R0.1-SNAPSHOT`, but
  `plugin.yml` declares `api-version: "1.16"` and the plugin is meant to stay compatible back to 1.13
  (see `COMPATABILITY.md`) — do not casually raise the minimum API version.
- There are no automated tests in this repo. Verify changes by building the jar and manually testing
  against a running Spigot/Paper server, or by reasoning carefully through the version-compatibility
  branches described below.
- `.vscode/launch.json` is configured for remote debug attach on port 5005 (i.e. run/attach a local
  test server yourself; there's no built-in run task).

## Cross-version compatibility model

This is the most important architectural constraint in the codebase: BestTools ships one jar that must
run on Spigot/Paper 1.13 through current, including forks like Leaves/Purpur where APIs can differ. This
shapes almost every file under `BestToolsUtils.java` and related classes:

- **Materials are looked up by string name**, not by compile-time `Material` enum references, via
  `BestToolsUtils.addToMap(String, Tool)` → `Material.getMaterial(name)`. This lets the jar load on
  older servers where newer Material constants don't exist — an unknown material just gets skipped
  (logged via `main.debug(...)`), rather than crashing class loading.
- **Tag-based bulk registration is wrapped in try/catch for `NoSuchFieldError` / `NoClassDefFoundError`**,
  grouped by the Minecraft version that introduced each `Tag` (see the `// Tags for 1.14+`, `// Tags for
  1.15+`, `// Tags for 1.16+`, `// 1.17` comment blocks in `BestToolsUtils.initMap()`). When adding
  support for a new version's blocks/tags, add a new guarded block rather than editing older ones.
  `tags/v1_17.java` is an example of isolating version-specific `Tag` collection logic in its own class.
  There's a similar deliberate `// mineable/ tag catchalls` pattern using `Tag.MINEABLE_AXE`,
  `Tag.MINEABLE_HOE`, `Tag.MINEABLE_PICKAXE`, `Tag.MINEABLE_SHOVEL` as a catch-all/future-proofing layer.
- Enchantments are looked up dynamically through the Bukkit `Registry.ENCHANTMENT` (`EnchantmentUtils.
  getEnchantment`), not deprecated static `Enchantment` fields.
- `Main.getMcVersion()` parses `Bukkit.getVersion()` to get a numeric minor version (e.g. 17 for 1.17)
  for any runtime version checks still needed outside the Tag/Material mechanisms above.
- When adding new block/tool mappings, prefer extending `BestToolsUtils.initMap()` following the existing
  pattern (Tag-based bulk registration first, guarded by try/catch, then individual `addToMap(...)` calls
  for exceptions/overrides). Order matters in several places — see the `WATCH OUT FOR ORDER` /
  `Order important` comments, since some Tag registrations intentionally get overwritten by more specific
  ones immediately after (e.g. stone buttons/doors/trapdoors get PICKAXE after AXE).

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
