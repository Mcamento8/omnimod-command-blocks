package com.mojang.brigadier.tree;

import java.util.function.Predicate;

import com.mojang.brigadier.Command;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier literal command node shim.
 *
 * Mirrors {@code com.mojang.brigadier.tree.LiteralCommandNode<S>} from Forge
 * 1.20.1. A literal node matches a fixed string token in the command path
 * (e.g. "start", "end", "area"). This is the node type produced by
 * {@code Commands.literal("...")}.
 *
 * GENERAL — part of the standard Brigadier API surface. See CommandNode for
 * how OmniMod bridges the captured tree into 1.8 ICommand registrations.
 *
 * Doc-ID: BRIG-LITNODE-001
 */
public class LiteralCommandNode<S> extends CommandNode<S> {
	private final String literal;

	public LiteralCommandNode(String literal, Command<S> command, Predicate<S> requirement,
			boolean hasRequirement) {
		super(command, requirement, hasRequirement);
		this.literal = literal != null ? literal : "";
	}

	@Override
	public String getName() {
		return literal;
	}

	public String getLiteral() {
		return literal;
	}
}
