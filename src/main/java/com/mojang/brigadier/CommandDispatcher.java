package com.mojang.brigadier;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import net.minecraft.commands.CommandSourceStack;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier command dispatcher shim.
 *
 * Mirrors {@code com.mojang.brigadier.CommandDispatcher<S>} from Forge 1.20.1.
 * A dispatcher owns a {@link RootCommandNode} and exposes {@code register(...)}
 * to attach a mod's command tree:
 *   dispatcher.register(Commands.literal("game").then(Commands.literal("start").executes(...)))
 *
 * WHY THIS EXISTS:
 * Forge 1.20.1 mods declare commands via this API in their
 * {@code RegisterCommandsEvent} handler. OmniMod does not execute mod Java,
 * so this shim serves two purposes:
 *   (1) It makes the mod's command-register class classload-able on the
 *       desktop ModClassLoader (bytecode verification passes).
 *   (2) When the engine CAN classload and invoke the mod's register method
 *       (desktop), the captured tree is mirrored into the 1.8 CommandHandler
 *       by {@code ModCommandSourceBridge}. When it CANNOT (web/source-only),
 *       the tree is reconstructed from SOURCE analysis of
 *       {@code Commands.literal(...)} calls in ModManager.
 *
 * [Agent Note 2026-08-28] PARITY UPGRADE:
 *   - {@link CommandPath} now carries the ROOT and LEAF {@link CommandNode}
 *     references so the bridge can (a) run the mod's REAL lambda on the
 *     classloaded path (previously the executor was dropped — recognition
 *     only), (b) parse arguments through real ArgumentTypes, and (c) provide
 *     tree-derived tab completion.
 *   - {@code walkPaths} INFERS the permission level from each node's real
 *     {@code requires(...)} predicate via {@link PermissionProbe} (previously
 *     only the manually-set permissionLevel was used, so predicates like
 *     {@code s -> s.hasPermission(2)} were silently treated as level 0 and
 *     non-ops could run op-gated mod commands).
 *
 * GENERAL — serves every Forge 1.20.1 command mod. No mod id/name hardcode.
 *
 * Doc-ID: BRIG-DISP-002
 */
public class CommandDispatcher<S> {
	private final RootCommandNode<S> root = new RootCommandNode<S>();

	public RootCommandNode<S> getRoot() {
		return root;
	}

	/**
	 * Register a literal command tree. Mirrors Forge's
	 * {@code CommandDispatcher.register(LiteralArgumentBuilder)}.
	 */
	public void register(LiteralArgumentBuilder<S> command) {
		if (command == null) {
			return;
		}
		CommandNode<S> node = command.build();
		root.addChild(node);
	}

	/**
	 * Register an already-built literal node (used by the source-analysis path).
	 */
	public void register(CommandNode<S> node) {
		if (node != null) {
			root.addChild(node);
		}
	}

	/**
	 * Register via a generic builder (covers {@code RequiredArgumentBuilder}
	 * top-level, though mods almost always register literals at top level).
	 */
	public void register(ArgumentBuilder<S, ?> command) {
		if (command == null) {
			return;
		}
		root.addChild(command.build());
	}

	/**
	 * Enumerate all reachable leaf command paths (used by the bridge to create
	 * 1.8 ICommand registrations). A leaf is a node whose executor is non-null
	 * OR has no children; intermediate literals are walked to their leaves.
	 *
	 * @return list of {full path tokens, root node, leaf node, permission
	 *         level, hasExecutor, stubExecutor}
	 */
	public java.util.List<CommandPath<S>> getAllCommandPaths() {
		java.util.List<CommandPath<S>> out = new java.util.ArrayList<CommandPath<S>>();
		for (CommandNode<S> node : root.getChildNodes()) {
			walkPaths(node, node, new java.util.ArrayList<String>(), out);
		}
		return out;
	}

	private void walkPaths(CommandNode<S> rootNode, CommandNode<S> node, java.util.List<String> path,
			java.util.List<CommandPath<S>> out) {
		path.add(node.getName());
		if (node.getCommand() != null || node.getChildNodes().isEmpty()) {
			int perm = inferPermissionLevel(node);
			out.add(new CommandPath<S>(new java.util.ArrayList<String>(path), rootNode, node,
					Math.max(node.getPermissionLevel(), perm), node.getCommand() != null,
					node.isStubExecutor()));
		}
		for (CommandNode<S> child : node.getChildNodes()) {
			walkPaths(rootNode, child, path, out);
		}
		if (!path.isEmpty()) {
			path.remove(path.size() - 1);
		}
	}

	/**
	 * [Agent Note 2026-08-28] Infer the op level a node's REAL {@code requires}
	 * predicate demands, by running it once against a probing source whose
	 * hasPermission() records the highest requested level (and always answers
	 * true so the whole predicate body executes). Falls back to 0 when the
	 * predicate throws or requests nothing. This is what makes
	 * {@code .requires(s -> s.hasPermission(2))} (and constants like
	 * {@code PERMISSION_LEVEL_OP}) enforce on the 1.8 command gate.
	 */
	private int inferPermissionLevel(CommandNode<S> node) {
		if (!node.hasRequirement() || node.getRequirement() == null) {
			return 0;
		}
		try {
			PermissionProbe probe = new PermissionProbe();
			@SuppressWarnings("unchecked")
			CommandSourceStack probeSource = new CommandSourceStack(probe);
			// Execute the predicate against the probe; any hasPermission(N)
			// inside records N. Wrapping a CommandSourceStack keeps the call
			// site identical to the mod's real source.
			node.getRequirement().test((S) probeSource);
			return probe.highestRequested;
		} catch (Throwable t) {
			// Predicate touched APIs the probe cannot satisfy (getServer()
			// on a real server, world access...): honest fallback to 0.
			return 0;
		}
	}

	/**
	 * [Agent Note 2026-08-28] Probing ICommandSender that records the highest
	 * permission level requested through CommandSourceStack.hasPermission(N).
	 * All other sender methods are inert (the probe is never used for world
	 * access — predicates that touch them throw and fall back to level 0).
	 */
	public static final class PermissionProbe implements net.minecraft.command.ICommandSender {
		int highestRequested = 0;

		@Override
		public boolean canCommandSenderUseCommand(int level, String cmd) {
			if (level > highestRequested) {
				highestRequested = level;
			}
			return true; // optimistic probe: let the whole predicate body run
		}

		@Override
		public String getName() {
			return "PermissionProbe";
		}

		@Override
		public net.minecraft.util.IChatComponent getDisplayName() {
			return null;
		}

		@Override
		public void addChatMessage(net.minecraft.util.IChatComponent c) {
		}

		@Override
		public net.minecraft.util.BlockPos getPosition() {
			return net.minecraft.util.BlockPos.ORIGIN;
		}

		@Override
		public net.minecraft.util.Vec3 getPositionVector() {
			return new net.minecraft.util.Vec3(0.0D, 0.0D, 0.0D);
		}

		@Override
		public net.minecraft.world.World getEntityWorld() {
			return null;
		}

		@Override
		public net.minecraft.entity.Entity getCommandSenderEntity() {
			return null;
		}

		@Override
		public boolean sendCommandFeedback() {
			return false;
		}

		@Override
		public void setCommandStat(net.minecraft.command.CommandResultStats.Type t, int v) {
		}
	}

	/**
	 * A reachable command leaf path: tokens (e.g. ["game","start"]), the ROOT
	 * literal node (parse/dispatch entry), the LEAF node (executor + children
	 * for tab completion), permission level (0 = everyone, 2+ = op), whether
	 * an executor exists, and whether that executor is an honest stub
	 * (source-analysis marker, not runnable mod behavior).
	 */
	public static final class CommandPath<S> {
		public final java.util.List<String> tokens;
		public final CommandNode<S> rootNode;
		public final CommandNode<S> node;
		public final int permissionLevel;
		public final boolean hasExecutor;
		public final boolean stubExecutor;

		public CommandPath(java.util.List<String> tokens, int permissionLevel, boolean hasExecutor) {
			this(tokens, null, null, permissionLevel, hasExecutor, false);
		}

		public CommandPath(java.util.List<String> tokens, CommandNode<S> rootNode, CommandNode<S> node,
				int permissionLevel, boolean hasExecutor, boolean stubExecutor) {
			this.tokens = tokens;
			this.rootNode = rootNode;
			this.node = node;
			this.permissionLevel = permissionLevel;
			this.hasExecutor = hasExecutor;
			this.stubExecutor = stubExecutor;
		}

		public String getRootLiteral() {
			return tokens != null && !tokens.isEmpty() ? tokens.get(0) : "";
		}

		public String getJoinedPath() {
			if (tokens == null || tokens.isEmpty()) {
				return "";
			}
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < tokens.size(); ++i) {
				if (i > 0) {
					sb.append(' ');
				}
				sb.append(tokens.get(i));
			}
			return sb.toString();
		}
	}
}
