package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla {@code /return}:
 *   return <value>       (int — sets the function's return value and stops it)
 *   return fail          (fails the function)
 *   return run <command> (delegates; the command's result is the return value)
 *
 * Vanilla semantics (Minecraft Wiki /return): /return is only meaningful
 * inside a function — outside one it fails ("Only functions can execute the
 * /return command"). When executed inside a function it stops the CURRENT
 * function immediately; FunctionRuntime consumes the flag between lines.
 *
 * WHY THIS EXISTS: pairs with FunctionRuntime (UCBPP-P3-FUNCTIONRT-001);
 * without it, functions cannot terminate early or carry a value.
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-RETURNCMD-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public class ReturnCommandParity implements ICommand {

	public ReturnCommandParity() {
	}

	@Override
	public String getCommandName() {
		return "return";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/return <value> | /return fail | /return run <command>";
	}

	@Override
	public List<String> getCommandAliases() {
		return Collections.emptyList();
	}

	/** Vanilla 1.20.1 gate level for /return. */
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), getCommandName());
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (!FunctionRuntime.isInsideFunction()) {
			GapFixRuntimeLog.hit("return", "ReturnCommandParity", "run", "fail", "outside_function");
			throw new CommandException("Only functions can execute the /return command");
		}
		if (args.length == 0) {
			throw new net.minecraft.command.WrongUsageException(getCommandUsage(sender));
		}
		try {
			if ("fail".equals(args[0]) && args.length == 1) {
				FunctionRuntime.returnFailFromCommand();
				GapFixRuntimeLog.hit("return", "ReturnCommandParity", "run", "ok", "mode=fail");
				return;
			}
			if ("run".equals(args[0]) && args.length >= 2) {
				String command = join(args, 1);
				int result = FunctionRuntime.getRunDelegateForReturn().executeCommand(sender, command);
				FunctionRuntime.returnFromCommand(result);
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, result);
				GapFixRuntimeLog.hit("return", "ReturnCommandParity", "run", "ok",
						"mode=run cmd=" + command + " result=" + result);
				if (result == 0) {
					// vanilla: /return run with a failing command fails the function
					FunctionRuntime.returnFailFromCommand();
				}
				return;
			}
			int value = Integer.parseInt(args[0]);
			FunctionRuntime.returnFromCommand(value);
			sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, value);
			GapFixRuntimeLog.hit("return", "ReturnCommandParity", "run", "ok", "value=" + value);
		} catch (NumberFormatException nfe) {
			throw new CommandException("Expected an integer value, got: '" + args[0] + "'");
		} catch (Throwable t) {
			GapFixRuntimeLog.error("return", "ReturnCommandParity", "run", "fail",
					t.getClass().getSimpleName(), "err=" + String.valueOf(t.getMessage()));
			throw new CommandException("/return command failed: " + String.valueOf(t.getMessage()));
		}
	}

	private static String join(String[] args, int from) {
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < args.length; ++i) {
			if (i > from) {
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
			if ("fail".startsWith(args[0].toLowerCase())) {
				out.add("fail");
			}
			if ("run".startsWith(args[0].toLowerCase())) {
				out.add("run");
			}
			return out;
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
