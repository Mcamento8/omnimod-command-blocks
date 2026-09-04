package com.mojang.brigadier.context;

import java.util.Collections;
import java.util.List;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier command context node shim.
 *
 * Mirrors {@code com.mojang.brigadier.context.CommandContextNode<S>} from
 * Forge 1.20.1. Used by {@code getContextNodes()} / child parsing. OmniMod
 * does not perform real Brigadier parsing; this is a verification-compatible
 * stub.
 *
 * GENERAL — part of the standard Brigadier API surface. No mod hardcode.
 *
 * Doc-ID: BRIG-CTXNODE-001
 */
public class CommandContextNode<S> {
	private final String literal;
	private final Object range;

	public CommandContextNode(String literal, Object range) {
		this.literal = literal != null ? literal : "";
		this.range = range;
	}

	public String getLiteral() {
		return literal;
	}

	public Object getRange() {
		return range;
	}

	public static <S> List<CommandContextNode<S>> emptyList() {
		return Collections.emptyList();
	}
}
