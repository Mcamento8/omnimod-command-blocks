package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.command.WrongUsageException;
import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.commands.arguments.TimeArgument;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 {@code /schedule}:
 *   schedule function <function> <time> [append|replace]
 *   schedule clear <function>
 *
 * Mirrors vanilla semantics (Minecraft Wiki /schedule, fetched 2026-08-29):
 * default mode is "replace" (one pending per function); "append" allows
 * several; <time> uses the vanilla TimeArgument suffixes (t/s/d → ticks);
 * time must be >= 1 tick; clear removes every pending of that function.
 * on_area_loaded is Bedrock-only — N/A for Java parity.
 *
 * The tick engine lives in ScheduleRuntime (fired once per server tick via
 * ForgeHooks.onServerTick — wired into MinecraftServer.tick() this round).
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-SCHEDCMD-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public class ScheduleCommandParity implements ICommand {

	private static final List<String> MODES = Arrays.asList("append", "replace");

	public ScheduleCommandParity() {
	}

	@Override
	public String getCommandName() {
		return "schedule";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/schedule function <function> <time> [append|replace] | /schedule clear <function>";
	}

	@Override
	public List<String> getCommandAliases() {
		return Collections.emptyList();
	}

	/** Vanilla 1.20.1 gate level for /schedule. */
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), getCommandName());
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		String input = join(args);
		try {
			process(sender, new StringReader(input));
		} catch (CommandSyntaxException e) {
			GapFixRuntimeLog.hit("schedule", "ScheduleCommandParity", "parse", "syntax_fail",
					"reason=" + e.getMessage() + " input=" + input);
			throw new CommandException("Incorrect argument for command /schedule: "
					+ String.valueOf(e.getMessage()));
		} catch (CommandException ce) {
			throw ce;
		} catch (Throwable t) {
			GapFixRuntimeLog.error("schedule", "ScheduleCommandParity", "process", "fail",
					t.getClass().getSimpleName(), "input=" + input + " err=" + String.valueOf(t.getMessage()));
			throw new CommandException("/schedule command failed: " + String.valueOf(t.getMessage()));
		}
	}

	private void process(ICommandSender sender, StringReader reader) throws Exception {
		reader.skipWhitespace();
		String sub = readWord(reader);
		if ("clear".equals(sub)) {
			reader.skipWhitespace();
			String fn = readFunctionId(reader, false);
			int removed = ScheduleRuntime.clear(fn);
			if (removed == 0) {
				throw new CommandException("No scheduled function(s) for '" + fn + "'");
			}
			ScheduleRuntime.sendClearedFeedback(sender, fn, removed);
			GapFixRuntimeLog.hit("schedule", "ScheduleCommandParity", "clear", "ok",
					"function=" + fn + " removed=" + removed);
			return;
		}
		if (!"function".equals(sub)) {
			throw new CommandSyntaxException("Expected function or clear");
		}
		reader.skipWhitespace();
		String fn = readFunctionId(reader, false);
		reader.skipWhitespace();
		int delay = TimeArgument.time().parse(reader);
		if (delay <= 0) {
			throw new CommandException("Time must be at least 1 tick");
		}
		boolean append = false;
		String rest = reader.getRemaining().trim();
		if (!rest.isEmpty()) {
			String mode = rest.split(" ")[0];
			if (!MODES.contains(mode)) {
				throw new CommandSyntaxException("Expected append or replace, got: '" + mode + "'");
			}
			append = "append".equals(mode);
		}
		if (!FunctionRuntime.hasFunction(fn)) {
			GapFixRuntimeLog.hit("schedule", "ScheduleCommandParity", "schedule", "fail",
					"unknown function=" + fn);
			throw new CommandException("Unknown function '" + fn + "'");
		}
		long due = ScheduleRuntime.schedule(fn, delay, append);
		ScheduleRuntime.sendScheduledFeedback(sender, fn, delay);
		GapFixRuntimeLog.hit("schedule", "ScheduleCommandParity", "schedule", "ok",
				"function=" + fn + " delay=" + delay + " append=" + append + " due=" + due);
	}

	/** function ids may appear bare (ns:path) or as tags (#ns:tag). */
	private static String readFunctionId(StringReader reader, boolean unused) throws CommandSyntaxException {
		String token = readWord(reader);
		boolean tag = token.startsWith("#");
		String id = tag ? token.substring(1) : token;
		if (id.indexOf(':') <= 0 || id.indexOf(':', id.indexOf(':') + 1) >= 0) {
			throw new CommandSyntaxException("Expected a namespaced function id, got: '" + token + "'");
		}
		return tag ? token : id;
	}

	private static String readWord(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String word = reader.getString().substring(start, reader.getCursor());
		if (word.isEmpty()) {
			throw new CommandSyntaxException("Expected a word at position " + start);
		}
		return word;
	}

	private static String join(String[] args) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.length; ++i) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(args[i]);
		}
		return sb.toString();
	}

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 1) {
			List<String> out = new java.util.ArrayList<String>();
			if ("function".startsWith(args[0].toLowerCase())) {
				out.add("function");
			}
			if ("clear".startsWith(args[0].toLowerCase())) {
				out.add("clear");
			}
			return out;
		}
		if (args.length >= 4 && "function".equals(args[0])) {
			return MODES;
		}
		return Collections.emptyList();
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		return false;
	}

	@Override
	public int compareTo(ICommand other) {
		return getCommandName().compareToIgnoreCase(other.getCommandName());
	}
}
