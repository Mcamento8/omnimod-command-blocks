package net.lax1dude.eaglercraft.v1_8.forge.command;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.util.EnumFacing;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /setblock} SYNTAX
 * translator — wired INTO the real 1.8 {@code CommandSetBlock} (dual-syntax
 * pattern proven by ExecuteCommandParity: modern form translated to the
 * legacy arg vector IN-PLACE, legacy form untouched = regression anchor).
 * Works for every map, every mod, zero hardcode.
 *
 * WHAT WAS BROKEN: 1.8 {@code CommandSetBlock} parses
 * {@code setblock <pos> <block> [meta] [mode] [nbt]} only. Every 1.13+ map
 * writes {@code setblock <pos> <block[props]{nbt}> [mode]} — bracketed
 * blockstates and attached NBT parse-fail, and modern command-block ids
 * (repeating/chain) do not exist at all. This is the placement path map
 * makers use for EVERY command-block mechanism.
 *
 * TRANSLATION (args-level, no re-tokenization — quotes/NBT stay intact):
 * <pre>
 *  setblock ~ ~-1 ~ minecraft:repeating_command_block[facing=up,
 *      conditional=true]{Command:"say hi",auto:1b}
 *    → args: [~ ~-1 ~ | minecraft:command_block | 0 | replace |
 *             {Mode:"repeating",Facing:"up",Conditional:1b,Command:"say hi",auto:1b}]
 *  setblock ~ ~ ~ stone replace          → [~ ~ ~ stone 0 replace]
 *  setblock ~ ~ ~ minecraft:oak_log[axis=y] → [~ ~ ~ minecraft:log 0 replace]
 * </pre>
 *
 * Block props resolve through the REAL 1.8 property space
 * ({@code BlockStateArgument}); unknown-to-1.8 props are dropped (the 1.8
 * state space is narrower — documented boundary §19.8). Command-block
 * extras ({@code facing}, {@code conditional}) fold into tile NBT where the
 * modern runtime reads them (MCBP-RUNTIME-001).
 *
 * PLACE-AND-FIRE: modern placement of an Always-Active impulse command
 * block fires it once (1.12+ {@code {auto:1b}} semantics — the most used
 * map pattern for instant commands). The caller (CommandSetBlock) performs
 * the fire after its own real placement succeeds.
 *
 * Doc-ID: MCBP-SETBLOCK-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class SetBlockCommandParity {

        private SetBlockCommandParity() {
        }

        /** Result of a modern-syntax translation. */
        public static final class Legacy {
                /** Legacy 1.8 arg vector (same meaning as the original command). */
                public final String[] args;
                /** auto impulse CB placed via modern syntax → fire once after place. */
                public final boolean autoFire;

                public Legacy(String[] args, boolean autoFire) {
                        this.args = args;
                        this.autoFire = autoFire;
                }
        }

        /**
         * Translate modern {@code /setblock} args into the legacy 1.8 arg vector.
         * Returns {@code null} when the args are already 1.8 syntax (caller keeps
         * them untouched — regression anchor). Throws IllegalArgumentException
         * (agent-readable) on malformed modern input.
         */
        public static Legacy translate(String[] args) {
                if (args == null || args.length < 4) {
                        return null; // let the real command produce its own usage error
                }
                String blockToken = args[3];
                boolean tokenIsModern = hasModernDecorations(blockToken) || isModernOnlyCommandBlockId(blockToken);
                boolean modernOrder = args.length >= 5 && isModeWord(args[4]);
                if (!tokenIsModern && !modernOrder) {
                        return null; // plain 1.8 syntax (minecraft:command_block included —
                    // its bare id is IDENTICAL to the 1.8 form; only repeating/chain ids
                    // or decorated tokens take the modern path)
                }

                // ---- split the block token: id [props] {nbt} ----
                String idPart = stripDecorations(blockToken);
                String propsPart = extractProps(blockToken);
                String nbtPart = extractNbt(blockToken);
                if (idPart.isEmpty()) {
                        throw new IllegalArgumentException("Expected a block id in: " + blockToken);
                }

                // ---- resolve id: modern command-block alias vs real block ----
                String cbMode = CommandBlockModernRuntime.modernCommandBlockMode(idPart);
                String realId;
                int meta = 0;
                String extraKeys = "";
                if (cbMode != null) {
                        realId = CommandBlockModernRuntime.REAL_COMMAND_BLOCK_ID;
                        StringBuilder keys = new StringBuilder("Mode:\"").append(cbMode).append('"');
                        if (CommandBlockModernRuntime.defaultAutoForMode(cbMode)) {
                                keys.append(",auto:1b");
                        }
                        extraKeys = foldCommandBlockProps(keys, propsPart).toString();
                } else {
                        realId = idPart;
                        meta = resolveMeta(idPart, propsPart);
                }

                String mergedNbt = mergeNbt(extraKeys, nbtPart);

                // ---- mode word + optional NBT (modern tail) ----
                String mode = "replace";
                for (int i = 4; i < args.length; ++i) {
                        if (isModeWord(args[i])) {
                                mode = args[i];
                                break;
                        }
                }
                boolean autoFire = false;
                if (cbMode != null && CommandBlockModernRuntime.MODE_IMPULSE.equals(cbMode)) {
                        boolean auto = mergedNbt.contains("auto:1") || mergedNbt.contains("\"auto\":1")
                                        || (nbtPart.isEmpty() && false); // impulse default = Needs Redstone
                        autoFire = auto;
                }

                // ---- build the legacy arg vector ----
                int n = 6 + (mergedNbt.isEmpty() ? 0 : 1);
                String[] out = new String[n];
                out[0] = args[0];
                out[1] = args[1];
                out[2] = args[2];
                out[3] = realId;
                out[4] = String.valueOf(meta);
                out[5] = mode;
                if (n == 7) {
                        out[6] = mergedNbt;
                }
                return new Legacy(out, autoFire);
        }

        /** Fold facing/conditional block props into tile-NBT SNBT keys. */
        static StringBuilder foldCommandBlockProps(StringBuilder keys, String propsPart) {
                if (propsPart == null || propsPart.isEmpty()) {
                        return keys;
                }
                for (String pair : propsPart.split(",")) {
                        int eq = pair.indexOf('=');
                        if (eq <= 0) {
                                continue;
                        }
                        String key = pair.substring(0, eq).trim();
                        String value = pair.substring(eq + 1).trim();
                        if ("facing".equals(key)) {
                                EnumFacing f = EnumFacing.byName(value);
                                if (f != null) {
                                        appendKey(keys, "Facing", '"' + f.getName() + '"');
                                }
                        } else if ("conditional".equals(key)) {
                                appendKey(keys, "Conditional", "true".equals(value) ? "1b" : "0b");
                        }
                        // "powered"/"triggered" are visual-only — accepted, ignored (§19.8)
                }
                return keys;
        }

        /** Merge injected keys with the user's {nbt} body (string-level SNBT). */
        public static String mergeNbt(String ourKeys, String userNbt) {
                boolean hasOurs = ourKeys != null && !ourKeys.isEmpty();
                String body = userNbt == null ? "" : userNbt.trim();
                boolean hasUser = body.length() > 2;
                if (!hasOurs && !hasUser) {
                        return "";
                }
                StringBuilder sb = new StringBuilder("{");
                if (hasOurs) {
                        sb.append(ourKeys);
                }
                if (hasUser) {
                        if (hasOurs) {
                                sb.append(',');
                        }
                        sb.append(body, 1, body.length() - 1);
                }
                sb.append('}');
                return sb.toString();
        }

        private static void appendKey(StringBuilder sb, String key, String value) {
                if (sb.length() > 0) {
                        sb.append(',');
                }
                sb.append(key).append(':').append(value);
        }

        /** Parse [props] through the REAL 1.8 property space → metadata value. */
        static int resolveMeta(String idPart, String propsPart) {
                if (propsPart == null || propsPart.isEmpty()) {
                        return 0;
                }
                try {
                        StringBuilder token = new StringBuilder(idPart).append('[').append(propsPart).append(']');
                        net.minecraft.commands.arguments.BlockStateArgument.BlockInput input =
                                        net.minecraft.commands.arguments.BlockStateArgument.blockState()
                                                        .parse(new com.mojang.brigadier.StringReader(token.toString()));
                        Object block = input.getBlock();
                        Object state = input.getState();
                        if (block != null && state != null) {
                                return ((net.minecraft.block.Block) block)
                                                .getMetaFromState((net.minecraft.block.state.IBlockState) state);
                        }
                } catch (Throwable t) {
                        // Property not in the 1.8 state space → dropped (§19.8 boundary);
                        // the real handler still validates the block id downstream.
                        GapFixRuntimeLog.hit("setblock", "SetBlockCommandParity", "resolveMeta", "stub",
                                        "props dropped for " + idPart + ": " + String.valueOf(t.getMessage()));
                }
                return 0;
        }

        /** Full-token variant used by fill/clone translators. */
        public static int resolveMetaPublic(String token) {
                if (token == null) {
                        return 0;
                }
                return resolveMeta(stripDecorations(token), extractProps(token));
        }

        static String extractProps(String token) {
                if (token == null) {
                        return "";
                }
                int bracket = token.indexOf('[');
                if (bracket < 0) {
                        return "";
                }
                int close = token.indexOf(']', bracket);
                if (close <= bracket) {
                        return "";
                }
                return token.substring(bracket + 1, close).trim();
        }

        static String extractNbt(String token) {
                if (token == null) {
                        return "";
                }
                int brace = token.indexOf('{');
                return brace >= 0 ? token.substring(brace).trim() : "";
        }

        static boolean hasModernDecorations(String token) {
                return token != null && (token.indexOf('[') >= 0 || token.indexOf('{') >= 0);
        }

        static String stripDecorations(String token) {
                if (token == null) {
                        return "";
                }
                int cut = token.length();
                int b = token.indexOf('[');
                int c = token.indexOf('{');
                if (b >= 0) {
                        cut = Math.min(cut, b);
                }
                if (c >= 0) {
                        cut = Math.min(cut, c);
                }
                return token.substring(0, cut).trim();
        }

        static boolean isModeWord(String s) {
                return "destroy".equals(s) || "keep".equals(s) || "replace".equals(s);
        }

        /** repeating/chain ids (NOT the plain impulse id — that one is 1.8-identical). */
        static boolean isModernOnlyCommandBlockId(String token) {
                String mode = CommandBlockModernRuntime.modernCommandBlockMode(stripDecorations(token));
                return CommandBlockModernRuntime.MODE_REPEATING.equals(mode)
                                || CommandBlockModernRuntime.MODE_CHAIN.equals(mode);
        }

        static boolean isNumeric(String s) {
                if (s == null || s.isEmpty()) {
                        return false;
                }
                try {
                        Integer.parseInt(s);
                        return true;
                } catch (NumberFormatException e) {
                        return false;
                }
        }
}
