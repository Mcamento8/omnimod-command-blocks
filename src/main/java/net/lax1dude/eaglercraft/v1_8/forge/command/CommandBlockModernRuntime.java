package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.HashMap;
import java.util.Map;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.command.server.CommandBlockLogic;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 command-block BEHAVIOR
 * parity on the 1.8.8 engine — for every map, every mod, zero hardcode.
 *
 * WHAT WAS BROKEN (research evidence, UCBPP audit round):
 * the engine's command-block system is pure 1.8: ONE impulse block type
 * ({@code BlockCommandBlock.java:41} registers only TRIGGERED), redstone
 * edge + 1-tick schedule activation ({@code BlockCommandBlock.java:53-77}),
 * no Repeating / Chain / Conditional / Always-Active semantics anywhere
 * (grep 'repeating_command_block' over all sources = 0 hits). Every 1.9+
 * map mechanism (tick loops, timers, conditional chains, wave counters,
 * boss timers, doors) is built on those four modes — the single biggest
 * general gap in the map-development system.
 *
 * WHAT THIS RUNTIME ADDS (vanilla semantics, wiki-verified 2026-09-04):
 * <pre>
 *   Impulse   — executes once when activated (1.8 path preserved);
 *               with auto (Always Active) also fires when its command is
 *               updated (GUI apply / setblock place), like 1.12+.
 *   Repeating — executes EVERY game tick while activated
 *               (activated = auto OR world power at the block).
 *   Chain     — executes when triggered by the block BEHIND it
 *               (i.e. the block it FACES executed), in the SAME tick;
 *               default activation mode is Always Active (vanilla).
 *   Conditional — only executes if the command block BEHIND it
 *               (opposite of its facing) last executed successfully;
 *               when the condition fails the block STILL forwards the
 *               trigger down the chain (vanilla 1.12+ semantics).
 *   maxCommandChainLength gamerule — bounds chain length (default 65536).
 * </pre>
 *
 * VANILLA NBT KEYS (kept identical where they exist in 1.20.1):
 * {@code auto} (byte), {@code conditionMet} (byte), {@code LastExecution}
 * (long), {@code UpdateLastExecution} (byte). ADDED keys for values vanilla
 * keeps in BLOCK STATE (this engine's 1.8 command block has no facing /
 * conditional state properties, so they live in tile NBT — documented
 * honest boundary): {@code Mode} ("impulse"|"chain"|"repeating"),
 * {@code Facing} ("down"|"east"|"north"|"south"|"up"|"west"),
 * {@code Conditional} (byte). Modern block IDs
 * {@code minecraft:repeating_command_block} /
 * {@code minecraft:chain_command_block} are ALIASED onto the real 1.8
 * command block + Mode NBT by {@link #modernCommandBlockMode} so
 * {@code /setblock}, {@code /fill}, {@code /give} parity all place a
 * fully functional block (visual skin stays the 1.8 command block —
 * documented §19.8 boundary).
 *
 * WIRED FROM: TileEntityCommandBlock implements ITickable (world-driven
 * per-tick, chunk-load aware), CommandBlockLogic.trigger() propagates
 * chains after every execution, GuiCommandBlock/NetHandlerPlayServer
 * MC|AdvCdm applies GUI mode changes, SetBlockCommandParity translates
 * modern /setblock syntax. GENERAL — no map/mod id anywhere.
 *
 * Doc-ID: MCBP-RUNTIME-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class CommandBlockModernRuntime {

        /** Command block modes (vanilla 1.20.1 GUI order: Impulse, Chain, Repeat). */
        public static final String MODE_IMPULSE = "impulse";
        public static final String MODE_CHAIN = "chain";
        public static final String MODE_REPEATING = "repeating";

        /** Vanilla default chain length (gamerule maxCommandChainLength, 1.12+). */
        public static final int DEFAULT_MAX_CHAIN_LENGTH = 65536;

        /** Modern block id → real 1.8 command block + mode (vanilla alias surface). */
        private static final Map<String, String> MODERN_COMMAND_BLOCK_IDS = buildModernIds();

        private static Map<String, String> buildModernIds() {
                Map<String, String> m = new HashMap<String, String>();
                m.put("minecraft:command_block", MODE_IMPULSE);
                m.put("minecraft:repeating_command_block", MODE_REPEATING);
                m.put("minecraft:chain_command_block", MODE_CHAIN);
                return m;
        }

        private CommandBlockModernRuntime() {
        }

        // ------------------------------------------------------------------
        // Modern id aliases (used by /setblock /fill /give parity translators)
        // ------------------------------------------------------------------

        /** @return the mode for a modern command-block id, or null if not one. */
        public static String modernCommandBlockMode(String blockId) {
                if (blockId == null) {
                        return null;
                }
                String id = blockId.toLowerCase();
                String mode = MODERN_COMMAND_BLOCK_IDS.get(id);
                if (mode == null) {
                        mode = MODERN_COMMAND_BLOCK_IDS.get("minecraft:" + id);
                }
                return mode;
        }

        /** The real 1.8 block every modern command-block id aliases onto. */
        public static final String REAL_COMMAND_BLOCK_ID = "minecraft:command_block";

        /**
         * The real 1.8 ITEM a modern command-block item id aliases onto
         * (used by the /give parity translation). Mode survives through the
         * placed block's NBT, never through the item form — the 1.8 engine has
         * a single command-block item (documented §19.8 boundary).
         */
        public static String giveAliasFor(String itemId) {
                String mode = itemId == null ? null
                                : MODERN_COMMAND_BLOCK_IDS.get(itemId.toLowerCase());
                if (mode == null && itemId != null) {
                        mode = MODERN_COMMAND_BLOCK_IDS.get("minecraft:" + itemId.toLowerCase());
                }
                return mode != null ? "minecraft:command_block" : null;
        }

        // ------------------------------------------------------------------
        // Per-tick driver (called from TileEntityCommandBlock.update / ITickable)
        // ------------------------------------------------------------------

        /**
         * World-driven tick: executes REPEATING command blocks that are activated
         * (auto or world power). Chain blocks never self-tick (they are triggered
         * by their source block through {@link #onExecuted}); impulse blocks keep
         * the 1.8 redstone path. Client worlds are ignored (server authority only).
         */
        public static void onTileTick(TileEntityCommandBlock tile) {
                if (tile == null || tile.getWorld() == null || tile.getWorld().isRemote) {
                        return;
                }
                CommandBlockLogic logic = tile.getCommandBlockLogic();
                if (logic == null || !isRepeating(logic)) {
                        return;
                }
                World world = tile.getWorld();
                BlockPos pos = tile.getPos();
                if (pos == null || !world.isBlockLoaded(pos)) {
                        return;
                }
                if (!isActivated(world, pos, logic)) {
                        return;
                }
                // UpdateLastExecution=true guards double execution per game tick
                // (vanilla applies this to chain blocks; applying it to repeating too
                // is harmless and protects against double tile ticks).
                long tick = currentServerTick();
                if (logic.isUpdateLastExecution() && logic.getLastExecution() == tick) {
                        return;
                }
                logic.setLastExecution(tick);
                try {
                        logic.trigger(world);
                } catch (Throwable t) {
                        // CommandBlockLogic.trigger already wraps into ReportedException;
                        // never let one bad repeating block kill the world tick loop.
                        GapFixRuntimeLog.hit("commandblock", "CommandBlockModernRuntime", "repeat", "fail",
                                        "pos=" + pos + " err=" + String.valueOf(t.getMessage()));
                }
                // Chain propagation happens inside trigger() → onExecuted().
        }

        // ------------------------------------------------------------------
        // Post-execution chain propagation (called from CommandBlockLogic.trigger)
        // ------------------------------------------------------------------

        /**
         * After ANY command block executes (any mode), the block it FACES gets
         * triggered (vanilla "Trigger and chaining": the facing block must be a
         * CHAIN block to react; other modes ignore triggers).
         */
        public static void onExecuted(World world, BlockPos pos, CommandBlockLogic source) {
                if (world == null || world.isRemote || pos == null || source == null) {
                        return;
                }
                EnumFacing facing = source.getFacing();
                if (facing == null) {
                        return;
                }
                propagateChain(world, pos.offset(facing), 1);
        }

        /**
         * Trigger a chain command block at {@code target}. Vanilla order:
         * (1) if not a chain block (or not a command block at all) — ignore;
         * (2) if not activated (Needs Redstone and unpowered) — ignore (no forward);
         * (3) if conditional AND the block BEHIND it did not last succeed — skip
         *     its own command but STILL forward the trigger to the block it faces;
         * (4) if already executed this game tick (LastExecution) — do nothing;
         * (5) execute, then forward the trigger to the block it faces.
         * Chain depth is bounded by the maxCommandChainLength gamerule.
         */
        public static void propagateChain(World world, BlockPos target, int depth) {
                if (world == null || world.isRemote || target == null || depth < 0) {
                        return;
                }
                int max = getMaxChainLength(world);
                if (depth > max) {
                        GapFixRuntimeLog.hit("commandblock", "CommandBlockModernRuntime", "chain", "abort",
                                        "depth=" + depth + " max=" + max + " (maxCommandChainLength)");
                        return;
                }
                TileEntityCommandBlock tile = getCommandTile(world, target);
                if (tile == null || tile.getCommandBlockLogic() == null) {
                        return;
                }
                CommandBlockLogic logic = tile.getCommandBlockLogic();
                BlockPos pos = tile.getPos();
                // (3) the conditional gate needs the BEHIND block's live success
                // state — resolved here (world access), then handed to the pure
                // vanilla decision table below (single source of truth).
                boolean conditionMet = true;
                if (logic.isConditional()) {
                        EnumFacing f = logic.getFacing();
                        if (f != null) {
                                TileEntityCommandBlock sourceTile = getCommandTile(world, pos.offset(f.getOpposite()));
                                conditionMet = sourceTile != null
                                                && sourceTile.getCommandBlockLogic() != null
                                                && sourceTile.getCommandBlockLogic().getSuccessCount() > 0;
                        } else {
                                conditionMet = false;
                        }
                }
                logic.setConditionMet(conditionMet);
                long tick = currentServerTick();
                boolean lastThisTick = logic.isUpdateLastExecution() && logic.getLastExecution() == tick;
                ChainDecision decision = decideChainExecution(isChain(logic),
                                isActivated(world, pos, logic), logic.isConditional(), conditionMet, lastThisTick);
                if (decision == ChainDecision.IGNORE || decision == ChainDecision.SKIP_THIS_TICK) {
                        return;
                }
                EnumFacing facing = logic.getFacing();
                if (decision == ChainDecision.FORWARD_ONLY) {
                        // condition failed → do NOT run, but forward the trigger
                        if (facing != null) {
                                propagateChain(world, pos.offset(facing), depth + 1);
                        }
                        return;
                }
                // EXECUTE
                logic.setLastExecution(tick);
                try {
                        logic.trigger(world);
                } catch (Throwable t) {
                        GapFixRuntimeLog.hit("commandblock", "CommandBlockModernRuntime", "chain", "fail",
                                        "pos=" + pos + " depth=" + depth + " err=" + String.valueOf(t.getMessage()));
                }
                // trigger() → onExecuted() forwards to the next chain block; the
                // direct forward below keeps chains alive even if a seam skips the hook.
                EnumFacing after = logic.getFacing();
                if (after != null) {
                        propagateChain(world, pos.offset(after), depth + 1);
                }
        }

        // ------------------------------------------------------------------
        // Activation / mode helpers
        // ------------------------------------------------------------------

        /** Activated = Always Active (auto) OR (Needs Redstone and world-powered). */
        public static boolean isActivated(World world, BlockPos pos, CommandBlockLogic logic) {
                if (logic.isAuto()) {
                        return true;
                }
                try {
                        return world != null && world.isBlockPowered(pos);
                } catch (Throwable t) {
                        return false;
                }
        }

        public static boolean isRepeating(CommandBlockLogic logic) {
                return logic != null && MODE_REPEATING.equals(logic.getMode());
        }

        public static boolean isChain(CommandBlockLogic logic) {
                return logic != null && MODE_CHAIN.equals(logic.getMode());
        }

        /**
         * Vanilla default for the {@code auto} NBT key: chain blocks default to
         * Always Active; impulse and repeating default to Needs Redstone.
         */
        public static boolean defaultAutoForMode(String mode) {
                return MODE_CHAIN.equals(mode);
        }

        // ------------------------------------------------------------------
        // Pure chain-decision core (harness-testable without a world)
        // ------------------------------------------------------------------

        /** Outcome of the vanilla chain trigger evaluation. */
        public enum ChainDecision {
                /** Not a chain block (or unpowered) — trigger ignored entirely. */
                IGNORE,
                /** Conditional failed — do NOT run, but FORWARD to the next block. */
                FORWARD_ONLY,
                /** Already executed this game tick (LastExecution) — do nothing. */
                SKIP_THIS_TICK,
                /** Run the command, then forward to the next block. */
                EXECUTE
        }

        /**
         * The exact vanilla "When a chain command block is triggered" decision
         * table (wiki-verified 1.20.1): activated gate → conditional gate →
         * one-execution-per-tick gate. Pure function — the harness proves the
         * full matrix; propagateChain only wires it to the real world.
         */
        public static ChainDecision decideChainExecution(boolean isChainMode, boolean activated,
                        boolean conditional, boolean conditionMet, boolean lastExecutionThisTick) {
                if (!isChainMode) {
                        return ChainDecision.IGNORE;
                }
                if (!activated) {
                        return ChainDecision.IGNORE;
                }
                if (conditional && !conditionMet) {
                        return ChainDecision.FORWARD_ONLY;
                }
                if (lastExecutionThisTick) {
                        return ChainDecision.SKIP_THIS_TICK;
                }
                return ChainDecision.EXECUTE;
        }

        // ------------------------------------------------------------------
        // Gamerule + clock
        // ------------------------------------------------------------------

        /** Current integrated-server tick (0 when no server is live). */
        public static long currentServerTick() {
                MinecraftServer server = MinecraftServer.getServer();
                return server == null ? 0L : server.getTickCounter();
        }

        /**
         * Read the vanilla maxCommandChainLength gamerule (registered by the
         * GameRules patch); falls back to the vanilla default when absent.
         */
        public static int getMaxChainLength(World world) {
                try {
                        if (world != null && world.getGameRules() != null) {
                                String raw = world.getGameRules().getString("maxCommandChainLength");
                                if (raw != null && !raw.isEmpty()) {
                                        return Math.max(1, Integer.parseInt(raw.trim()));
                                }
                        }
                } catch (Throwable t) {
                        // fall through to the vanilla default
                }
                return DEFAULT_MAX_CHAIN_LENGTH;
        }

        // ------------------------------------------------------------------
        // Internal
        // ------------------------------------------------------------------

        private static TileEntityCommandBlock getCommandTile(World world, BlockPos pos) {
                try {
                        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
                                return null;
                        }
                        Object te = world.getTileEntity(pos);
                        if (te instanceof TileEntityCommandBlock) {
                                return (TileEntityCommandBlock) te;
                        }
                } catch (Throwable t) {
                        // chunk not loaded / tile gone — treat as no chain block
                }
                return null;
        }
}
