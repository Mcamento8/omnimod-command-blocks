# 37 — MAP MODE PIPELINE (Dual-Mode: DEV ⇄ PREVIEW)

**Doc-ID:** MAP-MODE-PIPELINE-37
**Created:** 2026-09-04
**Status:** active
**Round:** MAP-MODE v1 (dual-mode map-development system)
**Audience:** any agent improving or verifying the map development system (Layer C engine bridge), plus map-building agents that need the mode contract.

---

## 1. What this is (the user-facing contract)

Every map in OmniMod has **two working shapes**, switchable at any moment with
**zero negative side effects**:

| Mode | Arabic name | Command blocks | Map mechanics | Purpose |
|------|-------------|----------------|---------------|---------|
| **DEV** (default) | وضع التطوير | Visible, openable (GUI), breakable, programmable — full creative control + the external-agent pipeline (`_dev/`, Agent Link) | Everything live | Building and editing the map |
| **PLAY** (preview) | وضع المعاينة | **Invisible, unopenable, unbreakable, untargetable** | **Still fully live** — impulse/repeating/chain command blocks, redstone, functions, schedulers all keep executing | Testing the map **exactly as a player will experience it when published** |

The user-facing promise: entering PLAY mode is indistinguishable from the map
being live on a public server — command blocks never show, never open, never
break — while the developer (or an external AI agent) can flip back to DEV at
any moment and everything returns instantly. The mode is **persisted per map**
and survives reloads and restarts.

## 2. The three front-ends (one unified kernel — UNIFIED_COMMAND_SYSTEM)

All three switch paths go through the SAME server-thread runtime call
(`MapModeRuntime.setMode`) so behavior is identical everywhere:

1. **Chat / command blocks:** `/omni_dev mode` (query) · `/omni_dev mode dev` ·
   `/omni_dev mode play` (alias: `preview`, Arabic aliases: `تطوير`, `معاينة`).
2. **In-game GUI:** pause menu → "Map Dev Folder" screen → the mode toggle
   button (label flips with the live client state) + a live mode status line
   (green = DEV, gold = PREVIEW).
3. **External AI agents (HTTP):** `POST /omni/mapdev/mode` `{"mode":"play"}`
   (empty body = query). Server-thread scheduled (same latch pattern as
   `/omni/command`), 10s timeout, blue-chat feedback in game.

## 3. Architecture (files, wired from where)

### 3.1 New files

| File | Role |
|------|------|
| `sources/main/java/net/lax1dude/eaglercraft/v1_8/sp/MapModeRuntime.java` | The authoritative runtime: mode state (volatile server/client flags), per-map persistence (`worlds/<map>/mapmode.json`), broadcast, join push, tick safety-net, setMode kernel, normalization (aliases), labels, status line. Doc-ID MAP-MODE-RT-001. |
| `sources/main/java/net/lax1dude/eaglercraft/v1_8/sp/ClientMapModeRuntime.java` | The client half: payload apply (idempotent by version), chunk-mesh re-render (every loaded command-block tile + its 6 neighbors → no X-ray holes), reset on world leave. Doc-ID MAP-MODE-CLI-001. |
| `tmp_mapmode_audit/` | Compile args + the JVM harness (38 assertions) + pack export scratch. |

### 3.2 Engine wiring (exact choke points, with citation IDs)

| Choke point | File | What it does | Doc-ID |
|---|---|---|---|
| Rendering gate | `BlockCommandBlock.getRenderType` | `-1` (invisible) in PLAY | MAP-MODE-BLK-001 |
| Opacity gate | `BlockCommandBlock.isOpaqueCube` | `false` in PLAY so neighbors stop face-culling (no X-ray holes; redstone `isNormalCube` is a separate formula and stays true) | MAP-MODE-BLK-001 |
| Targeting gate | `BlockCommandBlock.canCollideCheck` | `false` in PLAY — ray traces (mouse-over, pick-block, arrows) skip hidden blocks: no outline, no pick, no interaction reach | MAP-MODE-BLK-001 |
| Editor guard | `CommandBlockLogic.tryOpenEditCommandBlock` | returns `false` in PLAY — covers tile AND minecart command blocks, both sides | MAP-MODE-EDT-001 |
| Break guard | `ItemInWorldManager.removeBlock` | refuses command blocks in PLAY — creative instamine, survival digging and area mining ALL sink into this one method; the S23 re-sync in callers restores the block client-side | MAP-MODE-BRK-001 |
| GUI-apply guard | `NetHandlerPlayServer` `MC\|AdvCdm` branch | rejects edits from a stale client-side GUI after a switch to PLAY (+ Arabic notice) | MAP-MODE-ADV-001 |
| Client payload routing | `NetHandlerPlayClient` `OMNIMOD\|MapMode` branch | mirrors the mode + triggers the re-render (same transport as `OMNIMOD\|BossBar`) | MAP-MODE-CLI-002 |
| Join push | `ServerConfigurationManager.initializeConnectionToPlayer` | pushes the mode payload BEFORE chunks mesh → preview maps load already hidden, no one-frame flash | MAP-MODE-JOIN-001 |
| World-load hook | `EaglerMinecraftServer.startServer` → `MapModeRuntime.onWorldLoaded` | reloads the persisted mode after `loadAllWorlds` | — |
| Tick safety net | `EaglerMinecraftServer.updateTimeLightAndEntities` → `MapModeRuntime.onServerTick` | re-broadcasts every ~5s while undelivered (join-race healing), free when idle | — |
| Command surface | `CommandOmniDev` `mode` subcommand + tab completion + usage | MAP-MODE-CMD-001 | |
| GUI | `GuiMapDevFolder` BTN_MODE toggle + live status line | MAP-MODE-GUI-001 | |
| Agent Link | `AgentLinkBackend.mapDevMode` + router case 30 + `/omni/mapdev/mode` | POST / GET semantics, server-thread scheduling | — |
| Lang | `mapdev.mode.*` keys (4 new) | GUI labels | — |

### 3.3 Persistence contract

```
worlds/<map>/mapmode.json          (game-owned — never hand-edit)
{"format":"omnimod-mapmode-1","mode":"dev"|"play",
  "setAt":<epochMillis>,"setBy":"<who>","map":"<mapName>"}
```

Missing or corrupt file ⇒ **DEV** (the safe default — a developer is never
surprised by hidden blocks). The file lives OUTSIDE `_dev/` on purpose: the
mode is a core map concept that must exist for every map, including maps that
never enabled the agent workspace (per the 2026-08-30 decision that normal
worlds do not provision `_dev/`).

### 3.4 Transport contract

Channel `OMNIMOD|MapMode`, payload = UTF-8 JSON, idempotent by `version`:

```json
{"format":"omnimod-mapmode-1","mode":"play","version":7,
 "map":"MyMap","setBy":"command","setAt":1757000000000}
```

The CLIENT derives the re-render positions from its own loaded
`TileEntityCommandBlock` set (no position lists on the wire — the payload
stays tiny regardless of map size). Worker-thread mode: server isolate and
client isolate do not share statics; the union check
(`isPlayModeAnywhere`) makes the shared `BlockCommandBlock` class correct on
either side.

## 4. Honest boundaries (§19.8 style — documented, not hidden)

1. **Command-block minecarts** keep RENDERING in PLAY mode (entity rendering
   is a separate path). Their editor IS locked (same `tryOpenEditCommandBlock`
   choke point) and breaking them as entities is not blocked. Future work.
2. **Light recompute:** switching modes does not itself trigger light updates
   (no block changes). A block update NEXT TO a hidden command block during
   PLAY recomputes light treating the CB as transparent (its
   `getLightOpacity()` derives from `isOpaqueCube()`). Subtle, preview-only.
3. **Arrows / ray traces pass through hidden command blocks** in PLAY —
   consistent with what the player sees (thin air). Buried command blocks
   behind solid walls are still stopped by the wall itself.
4. **`/setblock`, `/fill`, `/give` can still place command blocks during
   PLAY** — they will be invisible immediately. Switching back to DEV reveals
   them. Preview is a testing shape, not a security boundary.
5. The **command-block GUI already open** when a switch to PLAY happens closes
   on its next interaction; a stale "Done" click is rejected server-side
   (MAP-MODE-ADV-001) with an honest Arabic notice.

## 5. Verification evidence (2026-09-04)

- **Compile:** `javac @tmp_mapmode_audit/mapmode_compile.args` — exit 0 over
  all 15 modified + 2 new files (log in `tmp_mapmode_audit/`).
- **JVM harness:** `MapModeHarness` — **38/38 PASS** (normalization aliases
  incl. Arabic, no-world guard, state machine, payload contract, constants).
  Run: `local-tools\jdk-17.0.18+8\bin\java.exe -cp <classes> MapModeHarness`.
- **Existing checker:** `mod_compat_lab/run_map_dev_check.py` — 89 PASS /
  4 FAIL where all 4 failures are pre-existing (3× stale GuiCreateWorld
  expectations from the intentional 2026-08-30 workspace-provisioning change +
  1× `.sh` harness that cannot run under Windows cmd). Zero new failures.
- **MCP:** `tsc` clean, `selfcheck` PASS (new dual-mode pack assertions
  included), `e2e.mjs` PASS (49 tools).
- **Docs:** `MapDevWorkspaceDocs` bumped to `omnimod-agent-docs-4`, re-exported
  (18 files) into `mcp/assets/map-context-pack/` via the export harness — NOT
  by hand (no drift path).

## 6. Agent loop with the mode (the new standard cycle)

```
build/modify in DEV (batches, command blocks, mods)
  → /omni_dev mode play            (or POST /omni/mapdev/mode)
  → verify as a PLAYER: mechanics fire, chat/screens/timers appear,
    command blocks invisible + untouchable
  → /omni_dev mode dev
  → iterate
```

**Verification duty (04_TESTING_MANDATE extension):** after ANY switch to
PLAY, an agent must verify the map still WORKS — e.g. `POST /omni/command`
triggering a known command-block effect, or `/omni/poll` watching for the
expected chat/event. Hiding the blocks never disables them; a preview that
looks right but fires nothing is a broken map, not a successful preview.

## 7. Related documents (update-together contract)

| If you change... | You must also update... |
|---|---|
| mode values / aliases | `MapDevWorkspaceDocs` §8/§8b + re-export + bump DOCS_VERSION; `MapModeHarness` alias assertions; this doc §3.3 |
| payload format marker | `MapModeRuntime.PAYLOAD_FORMAT` + `ClientMapModeRuntime.applyJson` + this doc §3.4 + harness T5 |
| persistence path/format | this doc §3.3 + the file map seed in `MapDevWorkspaceDocs` + `FILE_MAP.md` guidance |
| the render/opacity/targeting gates | this doc §3.2 + `BlockCommandBlock` citations |
| the endpoint shape | `05_AGENT_LINK_API.md` §2 row + `AgentLinkBackend` javadoc + selfcheck endpoint assertion |
