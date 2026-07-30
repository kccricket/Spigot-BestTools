# Changelog

## Unreleased
- Replaced `text.MessageUtil` and the shared `CooldownMessenger` with a single KcMcLib
  `net.kccricket.kcmclib.text.Messenger`, reached via `plugin.messages()`: every send is now
  `messenger.to(target).<severity>().send("key", ...)`, with `.throttle(key, seconds)` folding
  rate-limiting into the same call instead of a separate messaging path. Fixes a real bug this
  surfaced: `blacklistAdded`/`blacklistRemoved`/`blacklistInvalid`/`blacklistTitle` were being sent
  via a path that bypassed the `[BestestTool]` prefix every other message carries — they're now
  prefixed like everything else
- Fixed the root cause behind the documented "blacklist edits aren't explicitly persisted" quirk:
  `Blacklist` now writes through to PDC immediately on every `add`/`remove`, via the new shared
  `net.kccricket.kcmclib.pdc.PdcStringSet` (promoted out of ClickSorted's `PlayerSortingPrefs`),
  instead of mutating an in-memory list that only got saved when some other, unrelated setting
  changed. The on-disk PDC key is unchanged, so existing player data is unaffected
- Lifted `check_for_updates`'s tri-state (`true`/`on-startup`/off) into a new
  `net.kccricket.kcmclib.update.UpdateCheckMode` enum, so `ModrinthUpdateChecker` itself now owns
  the once-vs-recurring decision instead of each plugin's wiring re-deriving it. No behavior change
  for BestestTool
- Added a new `action_cooldown_ms` config key (default 150ms) that throttles repeated
  `/bestesttool` command use and blacklist edits per player, mirroring ClickSorted's
  `ActionThrottle` — a rate-limited `actionTooFast` notice is shown on denial. Players holding
  `bestesttool.throttle.bypass` are exempt
- Routed the plugin's two remaining `getLogger()` call sites (the `dump.csv` failure warning and the
  per-batch `/bestesttool admin benchmark` progress line) through the shared `Log` facade instead,
  so debug-level gating and future logging changes apply uniformly
- Every chat message is now prefixed (`[BestestTool]`, new `prefix` lang key), and a message sent to
  the console now goes through the plugin's own logger instead of a raw component send, matching
  ClickSorted's `MessageUtil`
- Fixed `consider_swords_for_leaves`/`consider_swords_for_cobwebs`/`use_axe_as_sword`/
  `global_block_blacklist` not taking effect on `/bestesttool admin reload` — these were cached at
  construction time in `BestToolsHandler`/`BestToolsListener`, which are now (see below) only ever
  constructed once, in `onEnable`. They're now parsed once per load/reload in `MainConfig` and read
  live from there
- Renamed the plugin's main class from `Main` to `BestestToolPlugin` (matches ClickSorted's
  `ClickSortedPlugin`), moved its 34 previously flat classes into `commands/`, `listeners/`,
  `tool/`, `refill/`, `model/`, `selftest/`, `benchmark/`, and `util/` subpackages, and reworked
  `onEnable`/`onDisable`/`/bestesttool admin reload` so every service and listener is constructed
  and registered exactly once, on enable — `admin reload` now only re-reads config and re-derives
  config-dependent state instead of tearing down and rebuilding everything. Purely internal/
  structural, but touches `paper-plugin.yml`'s `main:` — a manual jar swap (not a hot reload) is
  needed to pick this up
- Added an `enable_metrics` config key (default `true`) to opt out of the bundled bStats reporting,
  and the metrics instance is now properly shut down on disable instead of leaking one
- On disable, blacklist edits and other per-player setting mutations that hadn't yet ridden along on
  a later save are now explicitly flushed to PDC, closing a gap where such an edit could be lost if
  the server stopped before another mutator happened to trigger a save
- Moved `reload`, `debug`, `selftest`, and `benchmark` under a new `/bestesttool admin` subcommand
  (e.g. `/bestesttool admin reload`). Their permission nodes are renamed to match —
  `bestesttool.admin.reload`/`.debug`/`.selftest`/`.benchmark` — as children of the existing
  `bestesttool.admin` umbrella node
- Replaced `/bestesttool performance` (and its `measure_performance` config key) with
  `/bestesttool benchmark` (`start [full|hotbar]`/`status`/`stop`) — the old command passively
  sampled real event-handler wall time and its numbers depended entirely on how fast a player
  happened to be clicking, so they weren't comparable between runs or versions. The new benchmark
  instead ramps a fixed, hardcoded synthetic workload (not the real world or a real inventory)
  against the tool-selection routine, doubling the batch size each tick until one exceeds the 50ms
  tick budget, then reports the peak sustainable rate and the measured cost per selection. Works
  from console as well as in-game, and is gated the same way as `/bestesttool admin selftest`: a
  new `enable_benchmark` config key (default `false`) plus the `bestesttool.admin.benchmark`
  permission (default `op`)
- Fixed the per-player best-tool cache going stale after a player rearranged their own inventory
  (e.g. dragging a tool out of the hotbar via the inventory screen) — only dropping, picking up,
  switching held slot, or breaking a tool invalidated it, so BestTools could keep reusing a
  decision made against inventory contents that no longer existed until one of those four
  unrelated events happened to fire. `BestToolsCacheListener` now also invalidates on
  `InventoryClickEvent`/`InventoryDragEvent`
- Added `/bestesttool admin selftest` (`start [<stage>]`/`next`/`status`/`stop`), a live in-game
  correctness test: it builds a labelled arena of blocks (and, for the combat/refill stages, mobs)
  next to the tester, hands them a known hotbar kit, and reports pass/fail as they interact with
  each one — comparing the plugin's actual choice against a declared expectation
  (`selftest/stages.yml`, overridable via `plugins/BestestTool/selftest.yml`). Forces the tester
  into survival for the run (the plugin does nothing in creative) and restores their inventory,
  game mode, and BestestTool settings afterward, including a crash-safety backup restored on next
  join if the server goes down mid-test. Gated behind both the `bestesttool.admin.selftest` permission
  (default `op`) and a new `enable_selftest` config key (default `false`) — an op has to opt in
  explicitly, since it forces survival mode and replaces the tester's inventory for the duration
- Removed the settings GUI (`/bestesttool gui`/`settings`) entirely — a shift-click or drag from
  the player's own inventory while it was open could silently destroy the shifted/dragged item,
  since only same-inventory clicks were cancelled. Its favorite-slot picker is replaced by
  `/bestesttool favoriteslot [<-1 to 8>]`; its other toggles (`hotbaronly`, `refill`) already had
  chat-command equivalents. The now-unused `puns` config key is also removed
- Fixed `/bestesttool refill` (and `/refill`, `/rf`) permanently destroying the empty bowl/bottle
  it was supposed to relocate out of the refill destination slot: the fallback relocation loop
  re-cleared the destination slot on every iteration, which could make it "find" its own
  just-cleared slot and place the item right back into it, immediately before the refill
  overwrote that slot
- Fixed BestTools switching to a diamond pickaxe on blocks no tool can break or drop (bedrock,
  reinforced deepslate, barrier, portals, ...) and on "any tool" blocks (glass, sea lantern, wool
  carpet) instead of leaving the hand alone — a regression from the live-mining-data switch above
  caused by `isDamageable` misclassifying every pristine (unenchanted, undamaged) tool as
  non-damageable. BestTools now never switches for unbreakable blocks or decorated pots (the held
  item there is your own choice between an intact pot and 4 sherds), and falls back to an empty
  hotbar slot for "any tool" blocks — unless you're holding a Silk Touch item that's the only way
  to get a drop at all (glass, sea lantern, coral, turtle eggs, ...), in which case it switches to
  that instead
- Renamed the command from `/besttools` to `/bestesttool` (alias `/bt`) and rebuilt it on Paper's
  Brigadier command API, adding real per-argument tab completion — including block-name
  suggestions for `/bestesttool blacklist add`/`remove` — and client-side argument validation.
  `/refill`/`/rf` now alias directly to `/bestesttool refill` instead of being registered as a
  separate command, and `/refill reload` is removed (it only ever forwarded to the same reload
  logic as `/bestesttool reload`; use that instead). Raises the minimum server version to Paper
  1.20.6 (up from 1.20.5), since Brigadier command registration requires it
- Removed the `bl` and `hotbar` subcommand aliases (`/bestesttool blacklist`/`hotbaronly` are now
  the only names); the `hotbaronly`, `refill`, `debug`, and `performance` toggles now also take an
  optional `[<state>]` argument (`yes`/`no`, `true`/`false`, `on`/`off`, `enable`/`disable`) to set
  the value explicitly instead of only flipping it. The root `/bestesttool` toggle does not take a
  state argument, since it's the entry point to the rest of the subcommand tree
- `bestesttool.use` and `bestesttool.refill` now default to `true` (previously `op`), so
  BestestTool works for every player out of the box without an admin granting anything;
  `bestesttool.reload` and `bestesttool.debug` remain `op`-only. Permission nodes are now
  declared in `paper-plugin.yml`'s `permissions:` block instead of registered in code
- Tool selection for mining now reads Paper's live per-item mining data
  (`BlockData.getDestroySpeed`/`isPreferredTool`) instead of the plugin's own hand-maintained
  tool-tier table, so new blocks, new tools, and datapack-defined tool components are picked up
  automatically. Tools that would fail to drop the block correctly (e.g. an enchanted iron
  pickaxe on obsidian) are now ranked below any tool that mines it correctly, even if slower.
  Leaves and cobwebs are ranked by the same live logic rather than a separate hardcoded
  shears/hoe/sword preference list
- Added Folia support (`folia-supported: true`), switched the plugin descriptor from the legacy
  `plugin.yml` to `paper-plugin.yml`, and moved all scheduling off `Bukkit.getScheduler()` onto
  the per-entity scheduler so tool-switching, refills, and the settings GUI work correctly on
  Folia
- Replaced the last Spigot-only API usage (`p.spigot().sendMessage(...)` with a BungeeCord
  `TextComponent`) in the `/bestesttool blacklist` listing with Paper's bundled Adventure API
- Renamed permission nodes to the `bestesttool.*` prefix (`bestesttool.use`, `bestesttool.refill`,
  `bestesttool.reload`, `bestesttool.debug`) — no `besttools.*` legacy alias, since BestestTool is
  a fresh re-release with no backward compatibility to preserve
- Renamed the PlaceholderAPI expansion identifier from `besttools` to `bestesttool` to match the
  command and permission nodes above — `%besttools_btenabled%` etc. are now `%bestesttool_btenabled%`,
  `%bestesttool_rfenabled%`, `%bestesttool_hotbaronly%`, `%bestesttool_favoriteslot%`
- Fixed bStats leaking a `Metrics` instance (and its scheduled submission task) on every
  `/bestesttool reload` — it's now registered once, in `onEnable`, instead of every `load()`
- Fixed a crash on plugin load when `global-block-blacklist` contained an invalid material name
- Fixed `swordOnMobs` and the favorite-slot setting not surviving a server restart
- Fixed `/bestesttool performance` (mixed case) not toggling the performance test
- Removed a redundant double permission check on `/refill reload`
- Removed the one-time flat-file playerdata migration (dead weight now that settings persist to
  the player's PersistentDataContainer) and assorted dead/commented-out code left over from the
  BestTools → BestestTool rebrand
- Added a unit test suite (JUnit 5 + MockBukkit) covering tool selection, commands, and permission
  checks

## 2.2.1
- Rebranded to BestestTool (package `net.kccricket.bestesttool`); moved to a Gradle/Paper-API
  toolchain (dropping Spigot API and Maven), raised the minimum server version to Paper 1.20.5,
  and switched update checking from SpigotMC to Modrinth
- Got rid of ChestSortAPI as shaded dependency
- Fixed leaves not using the proper tool
- Fixed BestTools sometimes not recognizing interaction when mining cobblestone generators for hours
- Added config option "consider-swords-for-cobwebs" (default: false)
- Removed pre-1.20.5 compatibility code now that the minimum server version is Paper 1.20.5:
  the runtime MC-version probe, string-based `Material` lookups (replaced with compile-time
  `Material` references), the version-guarded `try`/`catch` blocks around `Tag`/`Material`
  registration, and the `tags/v1_17` helper (its logic is now covered directly by the
  `Tag.MINEABLE_*` tags)

## 2.2.0
- Added config option "consider-swords-for-cobwebs"

## 2.1.0
- Added support for BentoBox/OneBlock's magic block generator

## 2.0.2
- Fixed RAW_IRON_BLOCK not changing to a proper tool

## 2.0.1
- Fixed playerdata not being saved correctly
- Fixed crimson and warped stairs using pickaxe instead of axe
- Fixed seagrass and tall seagrass not using shears

## 2.0.0
- PlayerData will now be stored without any files. Old playerdata will be converted when a player joins.
- Fixed console error when setting "favorite-slot" to -1

## 1.17.0
- BestTools will now only use a golden pickaxe for diamond ore or deepslate diamond ore when you do not have an iron, diamond or netherite pickaxe

## 1.16.1
- Fixed dirth paths not being detected

## 1.16.0
- BestTools will now also consider hoes and swords (if you want) for leaves. It will still prefer shears if you got any (because only they give drops), then try to use a hoe if you have any. There's now a config to also consider swords, however it's disabled by default because swords take twice the usual damage when using them on leaves.

## 1.15.1
- Playerdata is saved async now

## 1.14.9
- Fixed some copper blocks not being detected

## 1.14.8
- Fixed bone block not being detected

## 1.14.7
- Fixed some deepslate blocks not being detected

## 1.14.6
- Fixed calcite not being detected

## 1.14.5
- Fixed smooth basalt and other blocks not being detected
- Added Polish translation

## 1.14.4
- Fixed some of the new ores not being detected

## 1.14.3
- Fixed UpdateChecker showing the wrong version

## 1.14.2
- Fixed all the amethyst blocks not being broken by pickaxes

## 1.14.0
- Added support for many new 1.17 materials. If some are still missing, please let me know on my Discord or in the discussion.
- Updated ChestSortAPI

## 1.13.0
- Updated ChestSortAPI to version 3.0.0. If you have ChestSort installed, you must use at least ChestSort 10.0.0!

## 1.12.1
- Updated config version (to show the new hint from 1.12.0)

## 1.12.0
- You can set messages to an empty String ("") to avoid them from being shown to the player.

## 1.11.0
- Added global block blacklist

## 1.10.2
- Added Russian translation

## 1.10.1
- Fixed BestTools trying to choose a tool for AIR in rare cases

## 1.10.0
- Added option to always use the current slot as favorite slot (set favorite-slot to -1)

## 1.9.1
- Refill works with the offhand as well

## 1.9.0
- Removed console message when attempting to refill bonemeal after right-clicking a block that can not be fertilized by bone meal
- Added possibility to disable using axes as weapons

## 1.8.1
- Refill now works for many new items like bone meal, ender pearls or ender eyes. 

## 1.8.0
- Empty soup bowls and potion bottles will ne be moved to another free slot when refill is enabled

## 1.7.0
- Added possibility to disable hint messages (just set them to an empty string (""))
- Added option to automatically switch to best sword/axe when attacking monsters
- Added silk touch and pickaxe as best tool to  kinds of glass and sea lanterns

## 1.6.2
- Nether gold ore, quartz and monster spawners will now be preferably mined with silk touch
- Already prepared ability to use best swords/axes on mobs, according to the enchantments Sharpness, Smite and Ban of Anthropods

## 1.6.1
- Fixed broken config updater leading to corrupt config files and exceptions. If you get errors on start, please delete your config.yml once. In future versions, it gets updated automatically.
- Added support for PlaceholderAPI (see new config.yml)

## 1.6.0
- Added GUI (beta!) using /besttools gui
- Added new blacklist command, so that BestTools will not change tools when trying to break those blocks.

Usage:
- /besttools bl -- Show your blacklist
- /besttools bl add -- Adds your currently held item to your blacklist
- /besttools bl add inventory -- Adds all items from your inventory to your blacklist
- /besttools bl add hotbar -- Adds all items from your hotbar to your blacklist
- /besttools bl add <items...> -- Add specified items to your blacklist
- /besttools bl remove -- Removes your currently held item from your blacklist
- /besttools bl remove inventory -- Removes all items from your inventory from your blacklist
- /besttools bl remove hotbar -- Removes all items from your hotbar from your blacklist
- /besttools bl remove <items...> -- Remove items from your blacklist
- /besttools bl reset -- Removes all items from your blacklist

## 1.5.0
- Instant breakable blocks like torches, grass, flowers etc. are no broken with the current item if it's not a hoe (because the hoe would take damage)
- Does not empty a slot into the inventory to use the empty hand to break a block when another undamagable item or block from the hotbar can be used instead
- Further performance optimization
  - replaced LinkedLists with ArrayLists
  - pregenerates tools material list instead of comparing strings (which was done to avoid problems with versions before netherite tools)1
  - checks player cache before checking everything else like permissions to further speed things up
- Added performance test mode (/besttools performance, needs permission besttools.debug)


## 1.4.1
- Added measure-performance option to demonstrate how fast BestTools is

## 1.4.0
##### Huuuuuge performance boost by caching two simple values

BestTools will now cache the last material a player has interacted with, and a boolean whether something important in their inventory has changed.

If the player interacts with the same material again, and they neither picked up a tool, broke a tool, changed their currently held item, nor dropped a tool, the BestTools listener will immediately exit because the player still has the best tool in his hands. This should improve performance by a greeeeeaaaaaaat amount. Even hundreds of players stripmining at once with golden efficiency 5 pickaxes should not be a problem for BestTools! :)

Also cleaned up the code.

## 1.3.0
- Added config option "dont-switch-during-battle" (default: true) that prevents BestTools from switching to a tool if the player currently holds a weapon (Sword, Crossbow, Bow or Trident)
- Added Chinese and Chinese (Traditional) translations

## 1.2.1
- Fixed misspelled permission (again)

## 1.2.0
- BestTools now prefers items with silk touch when breaking glass, enderchests and glowstone

## 1.1.0
- Refill works on food, snacks, and anything else digestible
- /besttools and /refill will only work in survival mode or, if enabled in the config, in adventure mode
- Added German translation 

## 1.0.1
- Fixed typo in permission. /refill should work now
- Added Spanish translation
- Added download link to Update checker

# Todo
- Auto refill edibles