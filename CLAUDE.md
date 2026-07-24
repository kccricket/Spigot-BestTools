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
  Shaded/relocated dependency: `org.bstats` (relocated under `net.kccricket.bestesttool.*`).
- `KcMcLib` (`net.kccricket.kcmclib`) is a shared library, wired in as a git submodule + Gradle
  composite build (see `settings.gradle.kts`'s `includeBuild("KcMcLib")` and `build.gradle.kts`'s
  `implementation("net.kccricket:kcmclib")`), the same way ClickSorted consumes it. Its classes are
  bundled into the fat jar like any other `implementation` dependency — no relocation needed, since
  `net.kccricket.kcmclib` is a distinct namespace. It supplies logging (`Log`/`DebugLevel`),
  permission checks (`security.Permissions`), the update checker (`update.ModrinthUpdateChecker`),
  the config-file lifecycle contract (`config.ManagedConfig`/`ResourceUpdater`), and the
  localization stack (`text.lang.*`, `text.Components`, `text.CooldownMessenger`). To pull in a
  library change: commit + push on the `KcMcLib` repo's `develop` branch, then `cd KcMcLib && git
  pull origin develop` here and commit the updated submodule pointer.
- Compiles to Java 21 (`options.release` in `build.gradle.kts`), built against
  `io.papermc.paper:paper-api:26.2.build.+`. `paper-plugin.yml` declares `api-version: "1.20.5"`, the
  minimum supported server version (see `COMPATABILITY.md`) — do not casually lower it back below
  1.20.5, since the codebase now assumes direct `Material`/`Tag` references that only exist from that
  floor onward.
- The plugin uses the modern `paper-plugin.yml` descriptor (not the legacy `plugin.yml`), and declares
  `folia-supported: true`. `paper-plugin.yml` cannot declare `commands:` or `permissions:` blocks, so
  both are registered in code in `Main.registerCommands()`/`Main.registerPermissions()` instead —
  commands via `getServer().getCommandMap().register(...)` wrapping each `CommandExecutor` in a
  `DelegatingCommand`, permissions via `getServer().getPluginManager().addPermission(...)`. Because
  Folia has no single main thread, **never use `Bukkit.getScheduler()`** — dispatch player/entity-tied
  work through `entity.getScheduler().run(...)`/`runDelayed(...)`, and anything not tied to a specific
  entity through `getServer().getAsyncScheduler()` (as KcMcLib's `ModrinthUpdateChecker` already does).
- `./gradlew test` runs the MockBukkit-based test suite (`src/test/java`) — bootstraps the real plugin
  via `MockBukkit.load(Main.class)` and dispatches real Bukkit events/commands; see `BestToolsTestBase`
  for the shared harness. Also verify by building the jar and manually testing against a running Paper
  server — `./gradlew runServer` spins up a local dev server with the plugin already loaded (see the
  `org.bxteam.runserver` config in `build.gradle.kts`; override the version with `-PmcVersion=<version>`).
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
  and the `config.ConfigManager` (constructed fresh on enable, `reloadAll()`'d on reload).
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
  `PerformanceMeter`, toggled via `/besttools performance` and `measure_performance` config option).
- **`PlayerSetting`** is per-player state (enabled flags, hotbar-only, blacklist, favorite slot). It
  persists to the player's `PersistentDataContainer` as one native `NamespacedKey` leaf per field
  (`BYTE` for booleans, `INTEGER` for `favorite_slot`, a comma-delimited `STRING` for the material
  blacklist) — no third-party PDC library. Leaf names match the `defaults.*` config keys. New plugin,
  no legacy data — no migration path.
- **`Blacklist`** is a per-player set of materials to never auto-switch for; managed via `/besttools bl ...`
  (`CommandBlacklist`). Mutations (`add`/`remove`) aren't explicitly persisted — they ride along on the
  next `PlayerSetting.save()` call some other mutator triggers. Known pre-existing quirk, not a bug
  introduced by any recent refactor.
- **`RefillListener`/`RefillUtils`** implement the separate `/refill` feature (auto-refilling hotbar stacks
  from the rest of the inventory) — largely independent of the tool-switching logic above.
- **`GUIHandler`/`GUIHolder`** implement an inventory-based settings GUI. Item names/lore route through
  `MessageUtil.legacy(player, key)` (renders a lang key down to a single legacy string, embedded `\n`
  preserved) since the GUI predates Adventure Components and already splits lore on `\n` itself.
- **`config.ConfigManager`** owns the plugin's config files and provides a single load/reload lifecycle,
  mirroring ClickSorted's `ConfigManager`:
  - **`config.MainConfig`** wraps `config.yml` via vanilla `JavaPlugin.getConfig()` +
    `copyDefaults(true)` + `saveConfig()` (not KcMcLib's `ResourceUpdater` — that's the separate
    file-loading path `ManagedConfig` implementations can use, but `config.yml`/`plugin.getConfig()`
    is Bukkit's own default-merging mechanism). Also calls KcMcLib's
    `ResourceUpdater.copyMissingKeyComments(...)` so a newly-added config key arrives on disk with its
    comment, not bare. Exposes typed getters — always add through `MainConfig`, never read
    `getConfig()` directly from a new call site.
  - **`config.LangConfig`** (ported from ClickSorted's `LangConfig`) loads bundled `lang/en_us.yml`
    plus sparse on-disk overrides under `plugins/BestestTool/lang/<locale>.yml` into a KcMcLib
    `LocaleMessages` snapshot. `text.MessageUtil` is the call-site-facing wrapper — use
    `MessageUtil.send(sender, "key", Placeholder.unparsed("name", value)...)` for chat messages, never
    a hardcoded string or a `main.getConfig()` message read.
- **`security.Permissions`** holds the `bestesttool.*` node constants and delegates the actual check to
  KcMcLib's `security.Permissions.isAllowedTo`, mirroring ClickSorted's facade pattern. No
  `besttools.*` legacy alias (dropped — new plugin, no backward compat).
- **`BestToolsPlaceholders`** registers PlaceholderAPI placeholders when PAPI is present (soft depend).
- Commands (`CommandBestTools`, `CommandBlacklist`, `CommandRefill`, `CommandReload`, `CommandDebug`) map
  directly to the subcommands documented in the `besttools`/`refill` usage strings registered by
  `Main.registerCommands()` (see above — not declared in the descriptor).

## Config/lang conventions

- `config.yml` has no version/migration scheme (new plugin, no backward compat to preserve) — add,
  rename, or remove keys freely; `ResourceUpdater.copyMissingKeyComments` (via `MainConfig`) means a
  newly-added key just shows up documented on the next load for existing installs.
- Per-player preference defaults live under `config.yml`'s `defaults.*` section, with leaf names
  matching the `PlayerSetting` PDC leaf names (see above) — keep that parity when adding a new
  per-player preference.
- User-facing text lives in `lang/en_us.yml` (MiniMessage format), not `config.yml` — add a key there
  and read it via `MessageUtil`, never a hardcoded string. Only `en_us` is bundled internally; admins
  can add other locales as sparse override files under `plugins/BestestTool/lang/`.
- `CHANGELOG.md` is maintained manually per release — add an entry when making user-facing changes.
