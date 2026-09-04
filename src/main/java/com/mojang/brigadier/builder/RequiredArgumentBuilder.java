package com.mojang.brigadier.builder;

import java.util.function.Predicate;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier required-argument builder shim.
 *
 * Mirrors {@code com.mojang.brigadier.builder.RequiredArgumentBuilder<S, T>}
 * from Forge 1.20.1. Created via {@code Commands.argument("name", type)}.
 * Argument nodes parse dynamic input (player names, positions, ints) rather
 * than matching a fixed literal.
 *
 * [Agent Note 2026-08-28] PARITY UPGRADE: when the declared type IS a real
 * {@link ArgumentType} (IntegerArgumentType, StringArgumentType,
 * EntityArgument, BlockPosArgument...), build() now produces an
 * {@link ArgumentCommandNode} so the bridge parses the mod's arguments and
 * places typed values on the CommandContext ({@code ctx.getArgument(name, ...)})
 * instead of treating them as nameless wildcards. A non-ArgumentType payload
 * (unresolvable variable in source analysis) falls back to a literal node
 * marked DYNAMIC (wildcard match, no static suggestions) — honest boundary.
 * Also adds the real {@code suggests(...)} API; the provider feeds tab
 * completion on the bridge.
 *
 * GENERAL — part of the standard Brigadier API surface. No mod hardcode.
 *
 * Doc-ID: BRIG-ARGBLD-002
 */
public class RequiredArgumentBuilder<S, T> extends ArgumentBuilder<S, RequiredArgumentBuilder<S, T>> {
	private final String name;
	private final Object type;
	private SuggestionProvider<S> suggestionProvider;

	protected RequiredArgumentBuilder(String name, Object type) {
		this.name = name != null ? name : "arg";
		this.type = type;
	}

	public static <S, T> RequiredArgumentBuilder<S, T> argument(String name, Object type) {
		return new RequiredArgumentBuilder<S, T>(name, type);
	}

	public String getName() {
		return name;
	}

	public Object getType() {
		return type;
	}

	/** Real Brigadier suggests(...) — custom tab-completion provider. */
	public RequiredArgumentBuilder<S, T> suggests(SuggestionProvider<S> provider) {
		this.suggestionProvider = provider;
		return getThis();
	}

	public SuggestionProvider<S> getSuggestionProvider() {
		return suggestionProvider;
	}

	@Override
	public CommandNode<S> build() {
		if (type instanceof ArgumentType) {
			// Real argument node: the bridge parses tokens through it and the
			// parsed value lands on the CommandContext under this name.
			ArgumentCommandNode<S, ?> node = new ArgumentCommandNode<S, Object>(name,
					(ArgumentType<Object>) type, getCommand(), getRequirement(), hasRequirement(),
					suggestionProvider);
			node.setPermissionLevel(getPermissionLevel());
			node.setStubExecutor(isStubExecutor());
			for (ArgumentBuilder<S, ?> child : getArguments()) {
				node.addChild(child.build());
			}
			return node;
		}
		// Non-ArgumentType payload (e.g. a source-analysis variable): keep a
		// literal node so the tree still links, marked dynamic (wildcard).
		LiteralCommandNode<S> node = new LiteralCommandNode<S>(name, getCommand(), getRequirement(),
				hasRequirement());
		node.setPermissionLevel(getPermissionLevel());
		node.setDynamicName(true);
		node.setStubExecutor(isStubExecutor());
		for (ArgumentBuilder<S, ?> child : getArguments()) {
			node.addChild(child.build());
		}
		return node;
	}

	@Override
	public RequiredArgumentBuilder<S, T> requires(Predicate<S> requirement) {
		return super.requires(requirement);
	}

	@Override
	public RequiredArgumentBuilder<S, T> executes(Command<S> command) {
		return super.executes(command);
	}

	@Override
	public RequiredArgumentBuilder<S, T> then(ArgumentBuilder<S, ?> argument) {
		return super.then(argument);
	}
}
