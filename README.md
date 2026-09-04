# OmniMod Command-Block System — Complete Engine Mirror (DEV/PLAY Dual-Mode)

![Engine](https://img.shields.io/badge/engine-EaglercraftX_1.8.8-blue)
![Commands](https://img.shields.io/badge/commands-1.8_kernel_+_1.20.1_parity-green)
![Sources](https://img.shields.io/badge/sources-181_files-orange)
![Docs](https://img.shields.io/badge/pipeline_docs-dual--mode-purple)
![Agents](https://img.shields.io/badge/AI_agents-omnimod--mcp_ready-red)

> **الغرض (بالعربية):** هذا المستودع هو المرجع العام الكامل لنظام الكوماند بلوك
> وسطح الأوامر في لعبة OmniMod: جسر Brigadier 1.20.1، أوامر التكافؤ الحديثة،
> أنماط الكوماند بلوك (impulse/repeating/chain/conditional/auto)، ونظام
> الوضعين (تطوير/معاينة). اقرأه لتتقن تصميم المابات الاحترافية بالأوامر، وليفهم
> وكيل الذكاء الاصطناعي كيف يبني مابات تعمل تماما كما ستظهر بعد النشر. يقترن
> بخادم `omnimod-mcp` (أداة `omni_mapdev_mode` والدليل `omni_map_guide`).

This repository mirrors **the engine's complete command system**: the 1.8.8
command implementations, the 1.20.1 Brigadier bridge, the modern parity
commands, the command-block block/tile/logic, and the **dual-mode DEV/PLAY
map system** that hides command blocks exactly like a published map.

---

## Contents

- [Why this mirror exists](#why-this-mirror-exists)
- [The command surface (what actually works)](#the-command-surface-what-actually-works)
- [Command-block modes](#command-block-modes)
- [The dual-mode contract (DEV/PLAY)](#the-dual-mode-contract-devplay)
- [Repository map](#repository-map)
- [For AI agents](#for-ai-agents)
- [For human map authors](#for-human-map-authors)
- [Snapshot policy](#snapshot-policy)
- [Status](#status)
- [License](#license)

---

## Why this mirror exists

A map whose logic lives in command blocks can only be trusted if its author
knows *exactly* which commands exist, how each one parses its arguments, how
command blocks tick, and what players will see after publishing. This mirror
gives you the real code for all four — citable as `file:line`, identical to
what the game runs. No wiki approximations, no version confusion.

Companion mirrors:

| Repository | What it covers |
|---|---|
| [omnimod-forge-compat](https://github.com/Mcamento8/omnimod-forge-compat) | The Forge 1.20.1 mod-compatibility layer (translation/asset bridge, 28 pipeline docs) |
| [omnimod-mcp](https://github.com/Mcamento8/omnimod-mcp) | The MCP server that turns any AI agent into a professional OmniMod map builder and mod author |

---

## The command surface (what actually works)

The engine runs the 1.8.8 kernel **plus** the registered 1.20.1 parity
commands. Verified surface: `bossbar` (renders on the client HUD),
`team`, `tag`, `title` (incl. `actionbar`), `function` + `schedule` (map
functions in `_dev/functions/*.mcfunction`), the full 1.20.1 `execute` chain
(`if data`, `store bossbar|storage|entity|score`), `data`
(get/merge/remove/modify on block/entity/storage), `random`, `return`,
`attribute`, `damage`, `ride`, `stopsound`, `teammsg`, `experience`, modern
`setblock`/`fill`/`clone` (id[props]{nbt} tokens), modern `effect`/`xp`/`give`,
plus the full 1.8 vanilla set.

Everything else that 1.20.1 has and this list does not mention is
**unsupported** — check the game feedback, never assume.

---

## Command-block modes

- `minecraft:repeating_command_block` — fires every tick.
- `minecraft:chain_command_block` — fires when the block it faces fires.
- `conditional=true` — fires only if the block behind it succeeded.
- `{auto:1b}` (Always Active) — impulse + auto is the place-and-fire pattern.
- Chain length is bounded by `/gamerule maxCommandChainLength` (default 65536).
- The in-game GUI keeps the three vanilla buttons (Block Type / Condition /
  Redstone), and map functions live in `_dev/functions/<name>.mcfunction`
  (with the `load.mcfunction` + `tick.mcfunction` conventions).

---

## The dual-mode contract (DEV/PLAY)

| Mode | Command blocks | Map mechanics |
|---|---|---|
| **DEV** (default) | Visible, openable, breakable, programmable | Everything live |
| **PLAY** (preview) | **Invisible, unopenable, unbreakable, untargetable** | **Still fully live** — impulse/repeating/chain, redstone, functions, schedulers all keep executing |

One kernel, three front-ends: `/omni_dev mode` (chat; aliases `preview`,
Arabic `تطوير`/`معاينة`), the pause-menu Map Dev screen, and the agent HTTP
endpoint `POST /omni/mapdev/mode` (empty body = query). The mode persists per
map at `worlds/<map>/mapmode.json` and survives reloads/restarts.

Honest boundaries (documented in `docs/37`): command-block minecarts keep
rendering in PLAY (editor locked); light recompute treats hidden blocks as
transparent; arrows pass through hidden blocks; `/setblock` can still place
command blocks in PLAY (invisible immediately).

---

## Repository map

| Path | What it is | Why you read it |
|---|---|---|
| `src/main/java/com/mojang/brigadier/` | The Brigadier 1.20.1 shim: `CommandDispatcher`, `CommandNode`/`Literal`/`Argument` trees, builders, 7 argument types with range enforcement, `StringReader`, suggestions | How mod commands register and parse. |
| `src/main/java/net/minecraft/commands/` | The 1.20.1 command API shims: `Commands`, `CommandSourceStack`, `arguments/` (Entity, BlockPos, Vec3, ResourceLocation, ...), `selector/EntitySelector`, `arguments/coordinates/` | The 1.20-side types command code compiles against. |
| `src/main/java/net/lax1dude/eaglercraft/v1_8/forge/command/` | **The parity layer** (27 files): `BossBarCommandParity` + `BossBarRuntime` + `ClientBossBarRuntime`, `Team`/`Tag`/`Teammsg`, `Function`/`Schedule` + `FunctionRuntime`/`ScheduleRuntime`, `Data`/`NbtPathCore`/`CommandStorageRuntime`, `SetBlock`/`Fill`/`Clone`/`Effect`/`Experience`/`Attribute`/`Damage`/`Ride`/`Stopsound` parity, `Random`, `Return`, `CommandBlockModernRuntime` | The exact semantics of every modern command — read the parity file of a command before you rely on an edge case. |
| `src/minecraft-client/java/net/minecraft/command/` | Every 1.8 command implementation (52 + `server/` 14): `CommandHandler.executeCommand` (the unified entry), `ServerCommandManager` (the 46-command registration + `ForgeHooks.onRegisterCommands`), `PlayerSelector`, `CommandOmniDev` | The vanilla base the parity layer extends. |
| `src/minecraft-client/java/net/minecraft/block/BlockCommandBlock.java` | The command block: rendering gate (`getRenderType`), opacity gate, collision/targeting gate (`canCollideCheck`) — all mode-aware | How PLAY mode hides the block. |
| `src/minecraft-client/java/net/minecraft/tileentity/TileEntityCommandBlock.java` | The tile entity + tick execution | How command blocks actually run. |
| `src/minecraft-client/java/net/minecraft/command/server/CommandBlockLogic.java` | The shared editor logic: `tryOpenEditCommandBlock` (locked in PLAY), command storage, NBT persistence | How the GUI and minecart variants share logic. |
| `src/main/java/net/lax1dude/eaglercraft/v1_8/sp/MapModeRuntime.java` | **The dual-mode kernel**: mode state, per-map persistence (`worlds/<map>/mapmode.json`), broadcast, join push, tick safety-net, `setMode` (server-thread), alias normalization (incl. Arabic) | The authoritative mode contract. |
| `src/main/java/net/lax1dude/eaglercraft/v1_8/sp/ClientMapModeRuntime.java` | The client half: payload apply (idempotent by version), chunk-mesh re-render (hidden block + 6 neighbors — no X-ray holes) | How switching rebuilds visuals instantly. |
| `src/minecraft-client/java/net/minecraft/network/NetHandlerPlayServer.java` | `MC\|AdvCdm` editor-apply guard (rejects stale GUI edits after a switch to PLAY) | Server-side anti-race gate. |
| `src/minecraft-client/java/net/minecraft/client/network/NetHandlerPlayClient.java` | `OMNIMOD\|MapMode` payload routing -> client mode apply + re-render | The mode transport. |
| `src/minecraft-client/java/net/minecraft/server/management/ItemInWorldManager.java` | Break guard: `removeBlock` refuses command blocks in PLAY (creative instamine + survival digging) | Why the block is unbreakable in preview. |
| `src/minecraft-client/java/net/minecraft/server/management/ServerConfigurationManager.java` | Join push: mode payload ships BEFORE chunks mesh — preview maps load already hidden (no one-frame flash) | Zero-flash guarantee. |
| `src/main/java/net/lax1dude/eaglercraft/v1_8/sp/server/EaglerMinecraftServer.java` | World-load hook (`MapModeRuntime.onWorldLoaded` — mode persistence reload) + tick safety-net (re-broadcast ~5s while undelivered) | Crash-healing for the mode state. |
| `docs/29_COMMAND_BLOCK_PIPELINE.md` | The full command pipeline map (1.8 entry -> Brigadier bridge -> verification harness) | Start here for the command system. |
| `docs/37_MAP_MODE_PIPELINE.md` | The dual-mode design contract: every gate, every choke point, honest boundaries, verification evidence | Start here for DEV/PLAY. |

`FILE_MANIFEST.txt` lists every file with its byte size — verify your checkout
is complete.

---

## For AI agents

Pair this repository with the **OmniMod MCP server**
([omnimod-mcp](https://github.com/Mcamento8/omnimod-mcp)):

```text
1. omni_mapdev_mode {}            -> query the current mode
2. omni_mapdev_mode {mode:"play"} -> preview exactly as published (hidden but live)
3. omni_command {command:"..."}   -> fire any command, read the feedback
4. omni_map_guide                 -> the professional map-dev master guide
5. omni_knowledge {topic:"commands"} -> the verified command table
```

The companion repository
([omnimod-forge-compat](https://github.com/Mcamento8/omnimod-forge-compat))
covers the mod compatibility layer. When you cite behavior, cite `file:line`
from THIS repository — it is the same code the engine runs.

---

## For human map authors

1. Build in **DEV** (command blocks visible and editable), wire your mechanics
   (repeating/chain/conditional/auto + `_dev/functions/*.mcfunction`).
2. Switch to **PLAY** (`/omni_dev mode play`) and verify the map still WORKS —
   hiding never disables logic. Walk the map as a player would.
3. Check the logs for `command_failed` / selector misses, fix in DEV, re-test
   in PLAY, then publish.

---

## Snapshot policy

This is a **snapshot mirror** of the OmniMod engine tree, re-exported on every
command-system change. `FILE_MANIFEST.txt` carries the file inventory of this
snapshot, so reviewers can see exactly what moved between updates. The
maintainer refreshes it with the project's sync tool
(`omnimod-mcp` repository, `tools/omnimod_sync.py`), which re-exports the
engine packages, regenerates the manifest, and pushes the diff here.

---

## Status

- Engine target: 1.8.8 kernel + 1.20.1 command parity.
- Snapshot policy: re-exported from the engine tree on every command-system
  change; `FILE_MANIFEST.txt` carries the file inventory of this snapshot.
- License: see [LICENSE](LICENSE).

---

## License

Engine sources (c) the OmniMod project — mirrored here as a read-only
reference for map authors and AI agents (see [LICENSE](LICENSE) for the full
terms). The MCP server that consumes this mirror is MIT-licensed at
[Mcamento8/omnimod-mcp](https://github.com/Mcamento8/omnimod-mcp).
