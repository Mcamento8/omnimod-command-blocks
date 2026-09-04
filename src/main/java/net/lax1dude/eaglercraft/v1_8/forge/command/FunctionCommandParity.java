package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 {@code /function <name>}
 * where name is a function id ({@code ns:path}) or a tag ({@code #ns:tag}).
 *
 * 1.20.1 parity notes (Minecraft Wiki /function, fetched 2026-08-29):
 *   - NO macros and NO conditional if/unless args — macros landed in 1.20.2
 *     (23w31a) and if/unless were removed in 1.13; implementing them would be
 *     OUT-of-parity feature creep (§5 of the mandate: preserve target
 *     behavior, never add);
 *   - success count = number of executed commands (the 1.20.3 change is out
 *     of 1.20.1 parity);
 *   - a function that ends via {@code /return fail} fails the command.
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-FUNCCMD-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public class FunctionCommandParity implements ICommand {

	public FunctionCommandParity() {
	}

	@Override
	public String getCommandName() {
		return "function";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/function <name> (name = ns:path or #ns:tag)";
	}

	@Override
	public List<String> getCommandAliases() {
		return Collections.emptyList();
	}

	/** Vanilla 1.20.1 gate level for /function. */
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), getCommandName());
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (args.length != 1 || args[0] == null || args[0].isEmpty()) {
			throw new net.minecraft.command.WrongUsageException(getCommandUsage(sender));
		}
		String name = args[0];
		try {
			int result = FunctionRuntime.runFunction(name, sender);
			if (result == -1) {
				GapFixRuntimeLog.hit("function", "FunctionCommandParity", "run", "fail",
						"unknown function=" + name);
				throw new CommandException("Unknown function '" + name + "'");
			}
			if (result == -2) {
				GapFixRuntimeLog.hit("function", "FunctionCommandParity", "run", "fail",
						"empty tag=" + name);
				throw new CommandException("No functions found in tag '" + name + "'");
			}
			if (result == -3) {
				throw new CommandException("Function chain exceeded maxCommandChainLength");
			}
			if (FunctionRuntime.consumeReturnFailFlag()) {
				// vanilla: /return fail fails the whole /function command
				// (success count 0 — Minecraft Wiki /function + /return)
				GapFixRuntimeLog.hit("function", "FunctionCommandParity", "run", "fail",
						"return fail in " + name);
				throw new CommandException("Function '" + name + "' failed");
			}
			GapFixRuntimeLog.hit("function", "FunctionCommandParity", "run", "ok",
					"name=" + name + " executed=" + result);
		} catch (CommandException ce) {
			throw ce;
		} catch (Throwable t) {
			GapFixRuntimeLog.error("function", "FunctionCommandParity", "run", "fail",
					t.getClass().getSimpleName(),
					"name=" + name + " err=" + String.valueOf(t.getMessage()));
			throw new CommandException("/function command failed: " + String.valueOf(t.getMessage()));
		}
	}

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		return Collections.emptyList(); // function ids live in data packs — no static list
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
