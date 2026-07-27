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
/bestesttool          — toggle automatic best-tool switching
/bestesttool refill   — toggle automatic hotbar refill (aliases: /refill, /rf)
```

Every command tab-completes, including block names for the blacklist — start typing
`/bestesttool blacklist add <TAB>` and pick from the list.

## Commands

All commands live under `/bestesttool` (alias `/bt`). `/bestesttool refill` also has its own root
aliases, `/refill` and `/rf`, that go straight to the same toggle — they don't expose the rest of
the `/bestesttool` tree.

The `hotbaronly`/`refill`/`debug`/`performance` toggles below also take an optional `[<state>]`
argument — `yes`/`no`, `true`/`false`, `on`/`off`, or `enable`/`disable` — to set the value
explicitly instead of flipping it; the bare form (no argument) still toggles. The root
`/bestesttool` toggle does not, since it's the entry point to the rest of the subcommand tree.

| Command | Description | Permission |
| --- | --- | --- |
| `/bestesttool` | Toggle automatic best-tool switching for yourself | `bestesttool.use` |
| `/bestesttool hotbaronly [<state>]` | Toggle (or set) whether BestestTool only uses tools from your hotbar | `bestesttool.use` |
| `/bestesttool favoriteslot [<-1-8>]` | Report (or set) which hotbar slot to place a tool in when it has to make room; `-1` means "whatever slot I'm holding" | `bestesttool.use` |
| `/bestesttool refill [<state>]` (aliases `/refill`, `/rf`) | Toggle (or set) automatic hotbar refill | `bestesttool.refill` |
| `/bestesttool reload` | Reload `config.yml` and the language files | `bestesttool.reload` |
| `/bestesttool debug [<state>]` | Toggle (or set) debug logging | `bestesttool.debug` |
| `/bestesttool performance [<state>]` | Toggle (or set) the performance meter | `bestesttool.debug` |
| `/bestesttool blacklist`, or `blacklist show` | Show your block blacklist | `bestesttool.use` |
| `/bestesttool blacklist add [<blocks...>]` | Blacklist the block you're holding, or the named blocks | `bestesttool.use` |
| `/bestesttool blacklist add inventory` | Blacklist every block type currently in your inventory | `bestesttool.use` |
| `/bestesttool blacklist add hotbar` | Blacklist every block type currently in your hotbar | `bestesttool.use` |
| `/bestesttool blacklist remove [<blocks...>]` / `remove inventory` / `remove hotbar` | Same shapes as `add`, but removes | `bestesttool.use` |
| `/bestesttool blacklist reset` | Clear your blacklist | `bestesttool.use` |

## Permissions

`bestesttool.use` and `bestesttool.refill` default to `true` — the plugin works for every player
out of the box. `bestesttool.reload` and `bestesttool.debug` default to server operators only.

| Node | Default | Grants |
| --- | --- | --- |
| `bestesttool` | `true` | Umbrella node for `use` and `refill` (does not cascade to admin nodes) |
| `bestesttool.use` | `true` | Automatic best-tool switching itself, plus `/bestesttool` and its `hotbaronly`/`favoriteslot`/`blacklist` subcommands |
| `bestesttool.refill` | `true` | Automatic hotbar refilling itself, plus `/bestesttool refill` (`/refill`, `/rf`) |
| `bestesttool.admin` | `op` | Umbrella node for `reload` and `debug` |
| `bestesttool.reload` | `op` | `/bestesttool reload` |
| `bestesttool.debug` | `op` | `/bestesttool debug` and `/bestesttool performance` |

## Configuration and localization

- `config.yml` holds per-player defaults (under `defaults:`), general behavior toggles (adventure
  mode, battle-switching, leaves/cobweb sword preference, ...), the global block blacklist, and
  update-checker settings. New keys show up documented on your existing install automatically.
- User-facing text lives in `plugins/BestestTool/lang/<locale>.yml` (MiniMessage format) as sparse
  overrides — only `en_us` is bundled, and anything you don't override keeps resolving to the
  plugin's built-in default, so a future wording improvement reaches you automatically.
- PlaceholderAPI (optional, soft-depend): `%besttools_btenabled%`, `%besttools_rfenabled%`,
  `%besttools_hotbaronly%`, `%besttools_favoriteslot%`.

## Requirements

See [`COMPATABILITY.md`](COMPATABILITY.md) — Paper (or a Paper fork such as Leaves/Purpur)
1.20.6 or newer, Folia-compatible. Spigot/CraftBukkit and Bukkit-only server jars are not
supported.
