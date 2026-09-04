package com.mojang.brigadier.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier argument builder base shim.
 *
 * Mirrors {@code com.mojang.brigadier.builder.ArgumentBuilder<S, T>} from Forge
 * 1.20.1. Builders are the fluent DSL used to construct command trees:
 *   Commands.literal("game").requires(...).then(other).executes(lambda)
 *
 * This shim records the {@code requires} predicate, the {@code executes}
 * callback, and child builders so that {@code CommandDispatcher.register(...)}
 * can assemble the tree. OmniMod does not execute the mod's lambda; the tree
 * shape (literal names + permission levels) is what the 1.8 bridge needs.
 *
 * GENERAL — part of the standard Brigadier API surface. No mod hardcode.
 *
 * Doc-ID: BRIG-BLD-001
 */
public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
	private Command<S> command;
	private Predicate<S> requirement;
	private boolean hasRequirement = false;
	private int permissionLevel = 0;
	/**
	 * [Agent Note 2026-08-28] Honest-stub marker transferred to the built
	 * node: set by the SOURCE-ANALYSIS path for executes() markers it cannot
	 * turn into real behavior. The bridge reports stubs as recognition-only —
	 * never a fake run (§18.2b).
	 */
	private boolean stubExecutor = false;
	private final List<ArgumentBuilder<S, ?>> args = new ArrayList<ArgumentBuilder<S, ?>>();

	@SuppressWarnings("unchecked")
	protected T getThis() {
		return (T) this;
	}

	public T requires(Predicate<S> requirement) {
		this.requirement = requirement;
		this.hasRequirement = true;
		return getThis();
	}

	public T executes(Command<S> command) {
		this.command = command;
		return getThis();
	}

	public T then(ArgumentBuilder<S, ?> argument) {
		if (argument != null) {
			args.add(argument);
		}
		return getThis();
	}

	public T then(CommandNode<S> node) {
		// wrap an already-built node back into a builder-less registration.
		if (node != null) {
			ArgumentBuilder<S, ?> wrap = wrapNode(node);
			if (wrap != null) {
				args.add(wrap);
			}
		}
		return getThis();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private ArgumentBuilder<S, ?> wrapNode(CommandNode<S> node) {
		if (node == null) {
			return null;
		}
		LiteralArgumentBuilder<S> b = LiteralArgumentBuilder.literal(node.getName());
		b.requires(node.getRequirement());
		b.executes(node.getCommand());
		b.setPermissionLevel(node.getPermissionLevel());
		for (CommandNode<S> child : node.getChildNodes()) {
			b.then(wrapNode(child));
		}
		return b;
	}

	public Predicate<S> getRequirement() {
		return requirement;
	}

	public boolean hasRequirement() {
		return hasRequirement;
	}

	public Command<S> getCommand() {
		return command;
	}

	public List<ArgumentBuilder<S, ?>> getArguments() {
		return args;
	}

	public int getPermissionLevel() {
		return permissionLevel;
	}

	/** Honest-stub marker accessors (see field doc). */
	public boolean isStubExecutor() {
		return stubExecutor;
	}

	public void setStubExecutor(boolean stubExecutor) {
		this.stubExecutor = stubExecutor;
	}

	public void setPermissionLevel(int permissionLevel) {
		this.permissionLevel = permissionLevel;
	}

	public abstract CommandNode<S> build();
}
