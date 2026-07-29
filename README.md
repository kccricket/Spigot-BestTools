# BestestTool

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE.md)
[![Latest release](https://img.shields.io/github/v/release/kccricket/Spigot-BestTools)](https://github.com/kccricket/Spigot-BestTools/releases)
[![Build status](https://img.shields.io/github/actions/workflow/status/kccricket/Spigot-BestTools/ci.yml?branch=master)](https://github.com/kccricket/Spigot-BestTools/actions/workflows/ci.yml)

Automatically switch to the best tool — no more digging through your inventory mid-dig.

BestestTool switches your held item to the best tool for the block you're about to break, or the
best weapon when you attack a mob, and switches back afterward. Tool selection reads Paper's live
per-item mining data, so new blocks, new tools, and datapack-defined tool components are picked up
automatically — not a hand-maintained tool-tier table. An optional hotbar "Refill" feature tops up
depleted stacks from the rest of your inventory.

- Automatic best-tool switching for both mining and combat
- Per-player block blacklist — never auto-switch for specific blocks
- Optional hotbar-only mode, and a favorite slot for when the best tool isn't already in your hotbar
- Optional automatic hotbar refill
- PlaceholderAPI placeholders
- Full command autosuggestions and client-side argument validation
- Folia-supported

## Quick start

Enable it for yourself:

```
/bestesttool          — toggle automatic best-tool switching (alias: /bt)
/bestesttool refill   — toggle automatic hotbar refill (aliases: /refill, /rf)
```

Every command tab-completes, including block names for the blacklist — start typing
`/bestesttool blacklist add <TAB>` and pick from the list.

## Commands

All commands live under `/bestesttool` (alias `/bt`). `/bestesttool refill` also has its own root
aliases, `/refill` and `/rf`, that go straight to the same toggle — they don't expose the rest of
the `/bestesttool` tree.

The `hotbaronly`/`refill`/`debug` toggles below also take an optional `[<state>]`
argument — `yes`/`no`, `true`/`false`, `on`/`off`, or `enable`/`disable` — to set the value
explicitly instead of flipping it; the bare form (no argument) still toggles. The root
`/bestesttool` toggle does not, since it's the entry point to the rest of the subcommand tree.

| Command | Description | Permission |
| --- | --- | --- |
| `/bestesttool` | Toggle automatic best-tool switching for yourself | `bestesttool.use` |
| `/bestesttool hotbaronly [<state>]` | Toggle (or set) whether BestestTool only uses tools from your hotbar | `bestesttool.use` |
| `/bestesttool favoriteslot [<-1-8>]` | Report (or set) which hotbar slot to place a tool in when it has to make room; `-1` means "whatever slot I'm holding" | `bestesttool.use` |
| `/bestesttool refill [<state>]` (aliases `/refill`, `/rf`) | Toggle (or set) automatic hotbar refill | `bestesttool.refill` |
| `/bestesttool admin reload` | Reload `config.yml` and the language files | `bestesttool.admin.reload` |
| `/bestesttool admin debug [<state>]` | Toggle (or set) debug logging | `bestesttool.admin.debug` |
| `/bestesttool blacklist`, or `blacklist show` | Show your block blacklist | `bestesttool.use` |
| `/bestesttool blacklist add [<blocks...>]` | Blacklist the block you're holding, or the named blocks | `bestesttool.use` |
| `/bestesttool blacklist add inventory` | Blacklist every block type currently in your inventory | `bestesttool.use` |
| `/bestesttool blacklist add hotbar` | Blacklist every block type currently in your hotbar | `bestesttool.use` |
| `/bestesttool blacklist remove [<blocks...>]` / `remove inventory` / `remove hotbar` | Same shapes as `add`, but removes | `bestesttool.use` |
| `/bestesttool blacklist reset` | Clear your blacklist | `bestesttool.use` |
| `/bestesttool admin selftest start [<stage>]` | Start (or jump to a named stage of) the live in-game self-test | `bestesttool.admin.selftest` |
| `/bestesttool admin selftest next` | Skip to the next self-test stage | `bestesttool.admin.selftest` |
| `/bestesttool admin selftest status` | Show the current self-test stage and how many cases remain | `bestesttool.admin.selftest` |
| `/bestesttool admin selftest stop` | Abort the self-test and restore everything | `bestesttool.admin.selftest` |
| `/bestesttool admin benchmark start [full\|hotbar]` | Start the synthetic tool-selection speed benchmark | `bestesttool.admin.benchmark` |
| `/bestesttool admin benchmark status` | Show the currently-running benchmark's progress | `bestesttool.admin.benchmark` |
| `/bestesttool admin benchmark stop` | Abort a running benchmark | `bestesttool.admin.benchmark` |

`selftest` is additionally hidden entirely (not just permission-gated) unless `enable_selftest: true`
is set in `config.yml` — see [Self-test](#self-test) below. `benchmark` is gated the same way behind
`enable_benchmark: true` — see [Benchmark](#benchmark) below.

## Permissions

`bestesttool.use` and `bestesttool.refill` default to `true` — the plugin works for every player
out of the box. `bestesttool.admin.reload`, `bestesttool.admin.debug`, `bestesttool.admin.selftest`,
and `bestesttool.admin.benchmark` default to server operators only.

| Node | Default | Grants |
| --- | --- | --- |
| `bestesttool` | `true` | Umbrella node for `use` and `refill` (does not cascade to admin nodes) |
| `bestesttool.use` | `true` | Automatic best-tool switching itself, plus `/bestesttool` and its `hotbaronly`/`favoriteslot`/`blacklist` subcommands |
| `bestesttool.refill` | `true` | Automatic hotbar refilling itself, plus `/bestesttool refill` (`/refill`, `/rf`) |
| `bestesttool.admin` | `op` | Umbrella node for `admin.reload`, `admin.debug`, `admin.selftest`, and `admin.benchmark` |
| `bestesttool.admin.reload` | `op` | `/bestesttool admin reload` |
| `bestesttool.admin.debug` | `op` | `/bestesttool admin debug` |
| `bestesttool.admin.selftest` | `op` | `/bestesttool admin selftest` (also needs `enable_selftest: true` in `config.yml`) |
| `bestesttool.admin.benchmark` | `op` | `/bestesttool admin benchmark` (also needs `enable_benchmark: true` in `config.yml`) |

## Self-test

`/bestesttool admin selftest` runs a live, in-game correctness test against the real server — the plugin's
own automated tests can't drive this part, since MockBukkit doesn't implement the live per-item
mining data (`BlockData.getDestroySpeed`/`isPreferredTool`) tool selection actually reads. The
self-test builds a small labelled arena of blocks (and, for the combat/refill stages, docile mobs)
next to you, hands you a known hotbar kit, and reports pass/fail in chat as you interact with each
one in turn.

It's off by default and gated behind two things at once:

1. `enable_selftest: true` in `config.yml` (default `false`) — the command doesn't exist at all
   otherwise, not even for an op.
2. The `bestesttool.admin.selftest` permission (default `op`).

Running it forces you into survival mode for the duration (the plugin does nothing in creative —
see [Requirements](#requirements) below for why) and replaces your hotbar with each stage's kit;
your original inventory, game mode, and BestestTool settings (hotbar-only, favorite slot, sword-on-
mobs, refill, blacklist) are all restored the moment the test ends, whether it finishes normally,
is stopped early, or the server reloads/restarts mid-test. A crash-safety backup is also written to
disk for the duration and restored automatically the next time you join if the server goes down
before it gets to restore things itself.

The block/mob/expectation list lives in the bundled `selftest/stages.yml`; drop a
`plugins/BestestTool/selftest.yml` alongside your other config to replace it wholesale with your
own stages.

## Benchmark

`/bestesttool admin benchmark` answers a concrete performance question: how many tool selections can the
plugin run in a single tick before that tick blows the server's 50ms budget? It ramps a fixed,
hardcoded workload (a spread of block materials and a synthetic inventory kit — not your real
inventory or the world around you, so results are reproducible run to run and version to version)
against the selection routine, doubling the batch size each tick, until a batch takes 50ms or more.
It then reports the peak batch that stayed under budget, the measured cost per selection, and the
derived sustainable selections/tick.

It's off by default and gated behind two things at once, same as self-test:

1. `enable_benchmark: true` in `config.yml` (default `false`) — the command doesn't exist at all
   otherwise, not even for an op. Leave it off on production servers; a run deliberately blows the
   tick budget on purpose.
2. The `bestesttool.admin.benchmark` permission (default `op`).

`/bestesttool admin benchmark start [full|hotbar]` picks the inventory size to test against (`full`, 36
slots, is the default and the worst case; `hotbar` is 9 slots). It works from console as well as
in-game, and only one run is active server-wide at a time. `/bestesttool admin benchmark stop` aborts it
early; a `/bestesttool admin reload` or server shutdown aborts an in-progress run automatically.

## Configuration and localization

- `config.yml` holds per-player defaults (under `defaults:`), general behavior toggles (adventure
  mode, battle-switching, leaves/cobweb sword preference, ...), the global block blacklist, and
  update-checker settings. New keys show up documented on your existing install automatically.
- User-facing text lives in `plugins/BestestTool/lang/<locale>.yml` (MiniMessage format) as sparse
  overrides — only `en_us` is bundled, and anything you don't override keeps resolving to the
  plugin's built-in default, so a future wording improvement reaches you automatically.
- PlaceholderAPI (optional, soft-depend): `%bestesttool_btenabled%`, `%bestesttool_rfenabled%`,
  `%bestesttool_hotbaronly%`, `%bestesttool_favoriteslot%`.

## Requirements

See [`COMPATABILITY.md`](COMPATABILITY.md) — Paper (or a Paper fork such as Leaves/Purpur)
1.20.6 or newer, Folia-compatible. Spigot/CraftBukkit and Bukkit-only server jars are not
supported.
