package com.mojang.brigadier;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-02] GENERAL: functional command callback interface.
 *
 * Mirrors {@code com.mojang.brigadier.Command<S>} from Forge 1.20.1. A mod
 * registers an executes(...) lambda of this shape on a literal node. The lambda
 * receives a {@link CommandContext} and returns an int result (nonzero = success).
 *
 * OmniMod does not execute mod Java, so this interface exists primarily for
 * bytecode-verification compatibility on the desktop ModClassLoader. The actual
 * command behavior is bridged from source analysis where possible (see
 * ModCommandSourceBridge) — when the mod's lambda cannot be run, the bridge
 * registers a 1.8 ICommand that reports the command as registered-but-unexecuted
 * so the command is at least recognized instead of "unknown command".
 *
 * GENERAL — part of the standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-CMD-001
 */
public interface Command<S> {
	int run(CommandContext<S> context) throws CommandSyntaxException;
}
