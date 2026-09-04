package com.mojang.brigadier.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.mojang.brigadier.Command;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier command-tree node shim.
 *
 * Mirrors {@code com.mojang.brigadier.tree.CommandNode<S>} from Forge 1.20.1.
 * A node represents one segment of a command path (e.g. the "start" in
 * "/game start"). It carries: the literal/argument name, a permission
 * requirement {@code requires} predicate, an optional {@code Command} executor
 * for leaf nodes, and child nodes reachable via {@code .then(...)}.
 *
 * This shim captures the tree STRUCTURE that a mod builds via
 * {@code Commands.literal("x").requires(...).then(Commands.literal("y").executes(...))}
 * so that OmniMod can mirror each reachable leaf path into a 1.8 ICommand
 * registered on the ServerCommandManager. OmniMod does not execute the mod's
 * {@code Command<S>} lambda (mod Java); when the lambda is unavailable, the
 * bridge registers a no-op recognition command so the command is no longer
 * "unknown".
 *
 * GENERAL — serves every Forge 1.20.1 command mod. The tree is built either by
 * classloading the mod's register method (desktop) or by source analysis
 * (ModManager inspects Commands.literal(...) calls). No mod id/name hardcode.
 *
 * Doc-ID: BRIG-NODE-001
 */
public abstract class CommandNode<S> {
	private final Map<String, CommandNode<S>> children = new LinkedHashMap<String, CommandNode<S>>();
	private Command<S> command;
	private Predicate<S> requirement;
	private boolean hasRequirement = false;
	private int permissionLevel = 0;
	/**
	 * [Agent Note 2026-08-28] Honest-stub marker: set by the SOURCE-ANALYSIS
	 * path (ModCommandSourceAnalyzer) for executor MARKERS it synthesizes
	 * (it cannot reconstruct the mod's lambda body from text). The bridge
	 * treats stub executors as recognition-only — it never reports a stub run
	 * as real execution (anti-fake, PROJECT_CONTEXT §18.2b). Real lambdas
	 * (desktop classloaded register()) leave this false and RUN for real.
	 */
	private boolean stubExecutor = false;
	/**
	 * [Agent Note 2026-08-28] Dynamic-literal marker: a literal whose name came
	 * from a mod VARIABLE (e.g. for-loop-generated {@code Commands.literal(arg)}).
	 * The bridge wildcard-matches one token for it and offers no static tab
	 * suggestions — documented honest boundary.
	 */
	private boolean dynamicName = false;

	public CommandNode(Command<S> command, Predicate<S> requirement, boolean hasRequirement) {
		this.command = command;
		this.requirement = requirement;
		this.hasRequirement = hasRequirement;
	}

	public Command<S> getCommand() {
		return command;
	}

	public void setCommand(Command<S> command) {
		this.command = command;
	}

	public Predicate<S> getRequirement() {
		return requirement;
	}

	public boolean hasRequirement() {
		return hasRequirement;
	}

	/**
	 * Permission level recorded from {@code Commands.literal(...).requires(s -> s.hasPermission(n))}.
	 * Defaults to 0 (everyone). The bridge uses this to set the 1.8 command's
	 * op-level gate.
	 */
	public int getPermissionLevel() {
		return permissionLevel;
	}

	public void setPermissionLevel(int permissionLevel) {
		this.permissionLevel = permissionLevel;
	}

	/** Honest-stub marker accessors (see field doc). */
	public boolean isStubExecutor() {
		return stubExecutor;
	}

	public void setStubExecutor(boolean stubExecutor) {
		this.stubExecutor = stubExecutor;
	}

	/** Dynamic-literal marker accessors (see field doc). */
	public boolean isDynamicName() {
		return dynamicName;
	}

	public void setDynamicName(boolean dynamicName) {
		this.dynamicName = dynamicName;
	}

	/**
	 * Add a child node under this node. Mirrors Brigadier's
	 * {@code addChild(CommandNode)}. Duplicate literal names replace the prior
	 * registration (idempotent for re-registration).
	 */
	public void addChild(CommandNode<S> node) {
		if (node == null) {
			return;
		}
		String name = node.getName();
		if (name != null) {
			children.put(name, node);
		}
	}

	public CommandNode<S> getChild(String name) {
		if (name == null) {
			return null;
		}
		return children.get(name);
	}

	public Map<String, CommandNode<S>> getChildren() {
		return Collections.unmodifiableMap(children);
	}

	public List<CommandNode<S>> getChildNodes() {
		return new ArrayList<CommandNode<S>>(children.values());
	}

	public abstract String getName();

	public boolean canUse(S source) {
		if (requirement == null) {
			return true;
		}
		try {
			return requirement.test(source);
		} catch (Throwable t) {
			return true;
		}
	}
}
