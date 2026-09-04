package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /tag} parity.
 *
 *   tag <targets> add <name>
 *   tag <targets> list
 *   tag <targets> remove <name>
 *
 * IMPLEMENTATION (Generalize-First): a pure SYNTAX TRANSLATOR onto the REAL
 * 1.8 scoreboard players-tag subsystem, delegated through the real server
 * CommandHandler (the RunDelegate seam pattern proven by ExecuteCommandParity
 * / FunctionRuntime / TeamCommandParity). The 1.8 {@code scoreboard players
 * tag} command already resolves entity selectors (so {@code /tag @e[type=Zombie]
 * add wave3} tags real entities) and persists tags with the entity NBT — the
 * exact data {@code /execute if entity ...} and {@code testfor} check.
 *
 * MAPPING (1.20.1 → 1.8.8):
 * <pre>
 *   tag <targets> add <name>     → scoreboard players tag <targets> add <name>
 *   tag <targets> remove <name>  → scoreboard players tag <targets> remove <name>
 *   tag <targets> list           → scoreboard players tag <targets> list
 * </pre>
 *
 * WHY MAP MAKERS NEED THIS: entity tags are the backbone of every wave /
 * defense / hunt map — spawn a wave, tag it {@code wave5}, then gate the
 * next wave on {@code execute unless entity @e[tag=!wave5alive] ...} style
 * checks (through the real 1.20.1 /execute parity shim already registered).
 *
 * HONEST BOUNDARY (§19.8): none — the whole 1.20.1 /tag surface maps onto
 * real 1.8.8 behavior; anything the real command rejects surfaces with its
 * real vanilla feedback (never silent).
 *
 * GENERAL — no mod id, no map name, no tag name hardcode.
 *
 * Doc-ID: GMF-TAG-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public class TagCommandParity implements ICommand {

	/** Real delegation point (same seam pattern as TeamCommandParity). */
	public interface RunDelegate {
		int executeCommand(ICommandSender sender, String command);
	}

	private static volatile RunDelegate runDelegate;

	public static void setRunDelegate(RunDelegate delegate) {
		runDelegate = delegate;
	}

	private static final RunDelegate DEFAULT_DELEGATE = new RunDelegate() {
		@Override
		public int executeCommand(ICommandSender sender, String command) {
			try {
				net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
				if (server != null) {
					return server.getCommandManager().executeCommand(sender, command);
				}
				GapFixRuntimeLog.warn("tag", "TagCommandParity", "run", "fail", "server_unavailable",
						"no live server — tag delegation dropped: " + command);
			} catch (Throwable t) {
				GapFixRuntimeLog.error("tag", "TagCommandParity", "run", "fail",
						t.getClass().getSimpleName(),
						"cmd=" + command + " err=" + String.valueOf(t.getMessage()));
			}
			return 0;
		}
	};

	private static int runDelegated(ICommandSender sender, String command) {
		RunDelegate delegate = runDelegate;
		if (delegate == null) {
			delegate = DEFAULT_DELEGATE;
		}
		return delegate.executeCommand(sender, command);
	}

	public TagCommandParity() {
	}

	@Override
	public String getCommandName() {
		return "tag";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/tag <targets> add|remove|list ...";
	}

	@Override
	public List<String> getCommandAliases() {
		return Collections.emptyList();
	}

	/** Vanilla 1.20.1 gate level for /tag. */
	// ICommand (not CommandBase) has no getRequiredPermissionLevel — plain method.
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), "tag");
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		String translated;
		try {
			translated = translate(args);
		} catch (IllegalArgumentException e) {
			GapFixRuntimeLog.hit("tag", "TagCommandParity", "parse", "syntax_fail",
					"reason=" + e.getMessage() + " args=" + Arrays.toString(args));
			throw new CommandException(String.valueOf(e.getMessage()));
		}
		int result = runDelegated(sender, translated);
		GapFixRuntimeLog.hit("tag", "TagCommandParity", "delegate", result > 0 ? "ok" : "fail",
				"translated='" + translated + "' result=" + result);
		if (result <= 0) {
			// The real command failed (no entities matched, bad tag name…).
			// Its own feedback already reached the sender; fail honestly.
			throw new CommandException("commands.tag.delegation_failed", new Object[] { translated });
		}
	}

	// ------------------------------------------------------------------
	// Pure translator (harness-testable without a live server)
	// ------------------------------------------------------------------

	/**
	 * Translate a 1.20.1 {@code /tag} argument vector into the equivalent
	 * real 1.8 {@code scoreboard players tag ...} command line (no leading
	 * slash). Throws {@link IllegalArgumentException} with an agent-readable
	 * reason for every unsupported or malformed form.
	 */
	public static String translate(String[] args) {
		if (args == null || args.length == 0) {
			throw new IllegalArgumentException("Expected tag <targets> add|remove|list");
		}
		String targets = args[0];
		if (targets == null || targets.isEmpty()) {
			throw new IllegalArgumentException("Expected entity targets for /tag");
		}
		if (args.length < 2) {
			throw new IllegalArgumentException("Expected add|remove|list after targets '" + targets + "'");
		}
		String sub = args[1];
		if ("add".equals(sub) || "remove".equals(sub)) {
			if (args.length < 3 || args[2] == null || args[2].isEmpty()) {
				throw new IllegalArgumentException("Expected a tag name for /tag " + targets + " " + sub);
			}
			return "scoreboard players tag " + targets + " " + sub + " " + joinFrom(args, 2);
		}
		if ("list".equals(sub)) {
			return "scoreboard players tag " + targets + " list";
		}
		throw new IllegalArgumentException(
				"Expected add, remove or list for /tag, got: '" + sub + "'");
	}

	private static String joinFrom(String[] args, int from) {
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < args.length; ++i) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(args[i]);
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------
	// ICommand plumbing (1.8 interface)
	// ------------------------------------------------------------------

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		// Manual prefix match (implements ICommand directly — no CommandBase helpers).
		List<String> out = new ArrayList<String>();
		if (args.length == 2) {
			String t = args[1] == null ? "" : args[1].toLowerCase();
			for (String option : Arrays.asList("add", "list", "remove")) {
				if (option.startsWith(t)) {
					out.add(option);
				}
			}
		}
		return out;
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		return false;
	}

	@Override
	public int compareTo(ICommand other) {
		return this.getCommandName().compareTo(other.getCommandName());
	}
}
