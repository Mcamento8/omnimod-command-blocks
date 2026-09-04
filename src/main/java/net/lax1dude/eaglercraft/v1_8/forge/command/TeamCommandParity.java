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
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /team} parity.
 *
 *   team add <team> [displayName]
 *   team empty <team>
 *   team join <team> <targets>
 *   team leave <targets>
 *   team list [team]
 *   team modify <team> <option> <value>
 *   team remove <team>
 *
 * IMPLEMENTATION (Generalize-First): a pure SYNTAX TRANSLATOR onto the REAL
 * 1.8 scoreboard teams subsystem — every call is delegated through the real
 * server CommandHandler (the RunDelegate seam pattern proven by
 * ExecuteCommandParity / FunctionRuntime), so team creation, membership,
 * colors, prefixes, suffixes and visibility all execute the REAL engine code
 * path with REAL validation and REAL feedback messages. No team data is
 * duplicated here, nothing is faked.
 *
 * MAPPING (1.20.1 → 1.8.8):
 * <pre>
 *   team add X [disp]           → scoreboard teams add X [disp]
 *   team remove X               → scoreboard teams remove X
 *   team join X <targets>       → scoreboard teams join X <targets>
 *   team leave <targets>        → scoreboard teams leave <targets>
 *   team empty X                → scoreboard teams empty X
 *   team list [X]               → scoreboard teams list [X]
 *   team modify X opt val       → scoreboard teams option X opt val
 * </pre>
 *
 * HONEST BOUNDARY (§19.8): the 1.13+ {@code collision} team option has no
 * 1.8.8 equivalent — it is rejected with a clear message (never silent).
 * Everything else in the 1.20.1 surface maps 1:1.
 *
 * WHY MAP MAKERS NEED THIS: team color + prefix is the vanilla way to draw
 * text over every team member's head (wave numbers, medals, ranks) without
 * any client mod — and {@code team join} + {@code team modify color} is the
 * only fully general per-player overhead display this engine supports.
 *
 * GENERAL — no mod id, no map name, no team name hardcode.
 *
 * Doc-ID: GMF-TEAM-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public class TeamCommandParity implements ICommand {

	/** Real delegation point (ExecuteCommandParity RunDelegate seam pattern). */
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
				GapFixRuntimeLog.warn("team", "TeamCommandParity", "run", "fail", "server_unavailable",
						"no live server — team delegation dropped: " + command);
			} catch (Throwable t) {
				GapFixRuntimeLog.error("team", "TeamCommandParity", "run", "fail",
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

	public TeamCommandParity() {
	}

	@Override
	public String getCommandName() {
		return "team";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/team add|empty|join|leave|list|modify|remove ...";
	}

	@Override
	public List<String> getCommandAliases() {
		return Collections.emptyList();
	}

	/** Vanilla 1.20.1 gate level for /team. */
	// ICommand (not CommandBase) has no getRequiredPermissionLevel — plain method.
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), "team");
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		String translated;
		try {
			translated = translate(args);
		} catch (IllegalArgumentException e) {
			GapFixRuntimeLog.hit("team", "TeamCommandParity", "parse", "syntax_fail",
					"reason=" + e.getMessage() + " args=" + Arrays.toString(args));
			throw new CommandException(String.valueOf(e.getMessage()));
		}
		int result = runDelegated(sender, translated);
		GapFixRuntimeLog.hit("team", "TeamCommandParity", "delegate", result > 0 ? "ok" : "fail",
				"translated='" + translated + "' result=" + result);
		if (result <= 0) {
			// The real scoreboard command failed (bad team name, no targets…).
			// Its own feedback already reached the sender; surface a failure exit
			// honestly instead of pretending success.
			throw new CommandException("commands.team.delegation_failed", new Object[] { translated });
		}
	}

	// ------------------------------------------------------------------
	// Pure translator (harness-testable without a live server)
	// ------------------------------------------------------------------

	/**
	 * Translate a 1.20.1 {@code /team} argument vector into the equivalent
	 * real 1.8 {@code scoreboard teams ...} command line (no leading slash).
	 * Throws {@link IllegalArgumentException} with an agent-readable reason
	 * for every unsupported or malformed form.
	 */
	public static String translate(String[] args) {
		if (args == null || args.length == 0) {
			throw new IllegalArgumentException("Expected team add|empty|join|leave|list|modify|remove");
		}
		String sub = args[0];
		if ("add".equals(sub)) {
			// team add <team> [displayName...] → scoreboard teams add <team> [displayName...]
			requireArg(args, 1, "team name");
			return "scoreboard teams add " + joinFrom(args, 1);
		}
		if ("remove".equals(sub)) {
			requireArg(args, 1, "team name");
			return "scoreboard teams remove " + args[1];
		}
		if ("join".equals(sub)) {
			// team join <team> <targets...>
			requireArg(args, 1, "team name");
			requireArg(args, 2, "targets");
			return "scoreboard teams join " + joinFrom(args, 1);
		}
		if ("leave".equals(sub)) {
			// team leave <targets...>
			requireArg(args, 1, "targets");
			return "scoreboard teams leave " + joinFrom(args, 1);
		}
		if ("empty".equals(sub)) {
			requireArg(args, 1, "team name");
			return "scoreboard teams empty " + args[1];
		}
		if ("list".equals(sub)) {
			// team list [team]
			if (args.length >= 2) {
				return "scoreboard teams list " + args[1];
			}
			return "scoreboard teams list";
		}
		if ("modify".equals(sub)) {
			// team modify <team> <option> <value...> → scoreboard teams option ...
			requireArg(args, 1, "team name");
			requireArg(args, 2, "option");
			String option = args[2];
			if ("collision".equals(option)) {
				// Honest boundary: 1.13+ collision has no 1.8.8 equivalent.
				throw new IllegalArgumentException(
						"team option 'collision' is not supported by this engine (no 1.8.8 equivalent) — §19.8 honest boundary");
			}
			requireArg(args, 3, "value");
			return "scoreboard teams option " + joinFrom(args, 1);
		}
		throw new IllegalArgumentException(
				"Expected team add|empty|join|leave|list|modify|remove, got: '" + sub + "'");
	}

	private static void requireArg(String[] args, int index, String what) {
		if (args == null || args.length <= index || args[index] == null || args[index].isEmpty()) {
			throw new IllegalArgumentException("Expected a " + what + " for /team " + args[0]);
		}
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
		// Manual prefix match (this command implements ICommand directly and does
		// not inherit CommandBase helpers — same approach as BossBarCommandParity).
		List<String> out = new ArrayList<String>();
		if (args.length == 1) {
			matchLastWord(out, args[0], Arrays.asList("add", "empty", "join", "leave", "list", "modify", "remove"));
		} else if (args.length == 3 && "modify".equals(args[0])) {
			matchLastWord(out, args[2], TEAM_OPTIONS);
		}
		return out;
	}

	private static void matchLastWord(List<String> out, String token, List<String> options) {
		String t = token == null ? "" : token.toLowerCase();
		for (String option : options) {
			if (option.startsWith(t)) {
				out.add(option);
			}
		}
	}

	private static final List<String> TEAM_OPTIONS = Collections.unmodifiableList(Arrays.asList(
			"color", "friendlyFire", "seeFriendlyInvisibles", "nametagVisibility", "deathMessageVisibility",
			"prefix", "suffix"));

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		return false;
	}

	@Override
	public int compareTo(ICommand other) {
		return this.getCommandName().compareTo(other.getCommandName());
	}
}
