package com.mojang.brigadier.builder;

import java.util.function.Predicate;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier literal argument builder shim.
 *
 * Mirrors {@code com.mojang.brigadier.builder.LiteralArgumentBuilder<S>} from
 * Forge 1.20.1. Created via {@code Commands.literal("name")}. This is the most
 * common builder — mods build literal command paths from it:
 *   Commands.literal("game").then(Commands.literal("start").executes(...))
 *
 * {@code build()} produces a {@link LiteralCommandNode} capturing the literal
 * name, permission requirement, executor, and child sub-tree.
 *
 * GENERAL — part of the standard Brigadier API surface. No mod hardcode.
 *
 * Doc-ID: BRIG-LITBLD-001
 */
public class LiteralArgumentBuilder<S> extends ArgumentBuilder<S, LiteralArgumentBuilder<S>> {
	private final String literal;
	private boolean dynamicName = false;

	protected LiteralArgumentBuilder(String literal) {
		this.literal = literal != null ? literal : "";
	}

	public static <S> LiteralArgumentBuilder<S> literal(String name) {
		return new LiteralArgumentBuilder<S>(name);
	}

	public String getLiteral() {
		return literal;
	}

	/**
	 * [Agent Note 2026-08-28] Mark this literal as DYNAMIC (source-analysis
	 * variable name — e.g. loop-generated {@code Commands.literal(arg)}). The
	 * bridge wildcard-matches one token for it and offers no static tab
	 * suggestions. Honors the CommandNode dynamicName contract.
	 */
	public void setDynamicName(boolean dynamic) {
		this.dynamicName = dynamic;
	}

	public boolean isDynamicName() {
		return dynamicName;
	}

	@Override
	public CommandNode<S> build() {
		LiteralCommandNode<S> node = new LiteralCommandNode<S>(literal, getCommand(),
				getRequirement(), hasRequirement());
		node.setPermissionLevel(getPermissionLevel());
		node.setDynamicName(this.dynamicName);
		node.setStubExecutor(isStubExecutor());
		for (ArgumentBuilder<S, ?> child : getArguments()) {
			node.addChild(child.build());
		}
		return node;
	}

	@Override
	public LiteralArgumentBuilder<S> requires(Predicate<S> requirement) {
		return super.requires(requirement);
	}

	@Override
	public LiteralArgumentBuilder<S> executes(Command<S> command) {
		return super.executes(command);
	}

	@Override
	public LiteralArgumentBuilder<S> then(ArgumentBuilder<S, ?> argument) {
		return super.then(argument);
	}
}
