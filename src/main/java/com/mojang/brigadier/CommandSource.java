package com.mojang.brigadier;

/**
 * [Agent Note 2026-08-02] GENERAL: Forge 1.20.1 Brigadier command source marker.
 *
 * WHY THIS EXISTS:
 * Forge 1.20.1 mods declare commands via Brigadier:
 *   dispatcher.register(Commands.literal("game").then(Commands.literal("start").executes(ctx -> ...)))
 * The full com.mojang.brigadier library is ~25 classes. OmniMod is a translation
 * bridge that does NOT execute mod Java, so we only need enough of the API surface
 * to (a) satisfy bytecode verification for any classloaded mod command class on
 * the desktop ModClassLoader, and (b) allow a SOURCE-analyzed command tree
 * (see ModCommandSourceBridge) to be mirrored into the 1.8 CommandHandler.
 *
 * This is the generic {@code S} (source) type parameter context. In real Forge
 * it is {@code CommandSourceStack}; here it is a bare marker so generic type
 * erasure produces a classfile-compatible signature.
 *
 * GENERAL — serves every Forge 1.20.1 mod that registers commands, not a
 * single mod. The command literal tree is discovered from source/bytecode
 * analysis in ModManager and bridged into 1.8 ICommand registrations.
 *
 * Doc-ID: BRIG-CMSRC-001
 */
public interface CommandSource {
}
