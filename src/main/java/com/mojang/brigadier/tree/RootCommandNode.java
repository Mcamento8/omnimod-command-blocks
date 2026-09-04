package com.mojang.brigadier.tree;

import java.util.function.Predicate;

import com.mojang.brigadier.Command;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier root command node shim.
 *
 * Mirrors {@code com.mojang.brigadier.tree.RootCommandNode<S>} from Forge
 * 1.20.1. The root node holds all top-level registered commands (e.g. "game",
 * "sniper"). A {@link com.mojang.brigadier.CommandDispatcher} owns one root
 * node; each {@code dispatcher.register(builder)} call attaches the builder's
 * literal node as a child of the root.
 *
 * GENERAL — part of the standard Brigadier API surface. The root node is what
 * OmniMod walks to enumerate reachable command leaf paths for 1.8 bridging.
 *
 * Doc-ID: BRIG-ROOT-001
 */
public class RootCommandNode<S> extends CommandNode<S> {
	public RootCommandNode() {
		super(null, null, false);
	}

	@Override
	public String getName() {
		return "";
	}
}
