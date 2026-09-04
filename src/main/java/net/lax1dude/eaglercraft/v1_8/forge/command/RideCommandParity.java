package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /ride} parity —
 * REAL 1.8 entity-mount engine, every map, every mod, zero hardcode.
 *
 * WHAT WAS BROKEN: /ride does not exist (1.19.4+ command). Map makers use
 * it for scripted vehicles, elevators, cutscene cameras and forced
 * mounts. The 1.8 engine HAS real riding (entity.mountEntity / riddenBy)
 * — only the command surface was missing.
 *
 * SYNTAX (1.20.1):
 * <pre>
 *  ride <targets> mount <vehicle>     → targets ride the vehicle
 *  ride <targets> dismount            → targets stop riding
 * </pre>
 *
 * HONEST BOUNDARY (§19.8): the 1.8 engine allows ONE rider per vehicle
 * (single riddenByEntity field); when several targets match, only the
 * first free mount succeeds and the rest fail with a visible message
 * (vanilla 1.20.1 supports multi-passenger). Teleport-to-vehicle before
 * mounting (vanilla behavior) is approximated by the 1.8 mount itself
 * which places the rider at the vehicle.
 *
 * Doc-ID: MCBP-RIDE-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public class RideCommandParity implements ICommand {

	@Override
	public String getCommandName() {
		return "ride";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/ride <targets> mount <vehicle> | dismount";
	}

	@Override
	public List<String> getCommandAliases() {
		return Collections.emptyList();
	}

	public int getRequiredPermissionLevel() {
		return 2; // vanilla 1.20.1 level
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), "ride");
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (args == null || args.length < 2) {
			throw new CommandException("Expected: ride <targets> mount <vehicle> | dismount");
		}
		String sub = args[1].toLowerCase();
		if ("mount".equals(sub)) {
			if (args.length < 3) {
				throw new CommandException("Expected: ride <targets> mount <vehicle>");
			}
			Entity vehicle = resolveSingle(sender, args[2]);
			if (vehicle == null) {
				throw new CommandException("No entity matched '" + args[2] + "'");
			}
			List<Entity> targets = resolveAll(sender, args[0]);
			int mounted = 0;
			List<Entity> failed = new ArrayList<Entity>();
			for (Entity target : targets) {
				if (target == vehicle || target.ridingEntity != null) {
					failed.add(target);
					continue;
				}
				if (vehicle.riddenByEntity != null) {
					failed.add(target); // 1.8: one rider per vehicle (§19.8)
					continue;
				}
				target.mountEntity(vehicle);
				if (target.ridingEntity == vehicle) {
					++mounted;
				} else {
					failed.add(target);
				}
			}
			sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, mounted);
			GapFixRuntimeLog.hit("ride", "RideCommandParity", "mount", "ok",
					"vehicle=" + vehicle + " mounted=" + mounted + " failed=" + failed.size());
			if (mounted > 0 && failed.isEmpty()) {
				feedback(sender, "Mounted " + mounted + " entit" + (mounted == 1 ? "y" : "ies")
						+ " on " + describe(vehicle));
			} else if (mounted == 0) {
				throw new CommandException("Unable to mount " + describe(vehicle)
						+ " (already ridden or target already riding)");
			} else {
				feedback(sender, "Mounted " + mounted + "; " + failed.size() + " could not mount (1 rider per vehicle on this engine)");
			}
			return;
		}
		if ("dismount".equals(sub)) {
			List<Entity> targets = resolveAll(sender, args[0]);
			int dismounted = 0;
			for (Entity target : targets) {
				if (target.ridingEntity != null) {
					Entity was = target.ridingEntity;
					target.mountEntity(null);
					if (target.ridingEntity == null) {
						++dismounted;
					}
				}
			}
			sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, dismounted);
			GapFixRuntimeLog.hit("ride", "RideCommandParity", "dismount", "ok", "count=" + dismounted);
			if (dismounted == 0) {
				throw new CommandException("Nothing to dismount");
			}
			feedback(sender, "Dismounted " + dismounted + " entit" + (dismounted == 1 ? "y" : "ies"));
			return;
		}
		throw new CommandException("Expected mount or dismount, got: '" + sub + "'");
	}

	private static List<Entity> resolveAll(ICommandSender sender, String token) throws CommandException {
		try {
			List<Entity> out = new EntitySelector(token).getEntities(sender);
			if (out.isEmpty()) {
				throw new CommandException("No entity matched '" + token + "'");
			}
			return out;
		} catch (CommandException e) {
			throw e;
		} catch (Throwable t) {
			GapFixRuntimeLog.hit("ride", "RideCommandParity", "resolve", "fail",
					"token=" + token + " err=" + String.valueOf(t.getMessage()));
			throw new CommandException("No entity matched '" + token + "'");
		}
	}

	private static Entity resolveSingle(ICommandSender sender, String token) {
		try {
			return new EntitySelector(token).findEntity(sender);
		} catch (Throwable t) {
			GapFixRuntimeLog.hit("ride", "RideCommandParity", "resolve_single", "fail",
					"token=" + token + " err=" + String.valueOf(t.getMessage()));
			return null;
		}
	}

	private static String describe(Entity e) {
		return e == null ? "nothing" : (e.getName() + " (" + e.getEntityId() + ")");
	}

	private static void feedback(ICommandSender sender, String message) {
		try {
			sender.addChatMessage(new ChatComponentText(message));
		} catch (Throwable t) {
			GapFixRuntimeLog.hit("ride", "RideCommandParity", "feedback", "fail",
					"err=" + String.valueOf(t.getMessage()));
		}
	}

	// ------------------------------------------------------------------
	// ICommand plumbing
	// ------------------------------------------------------------------

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		List<String> out = new ArrayList<String>();
		if (args.length == 2) {
			match(out, args[1], Arrays.asList("mount", "dismount"));
		}
		return out;
	}

	private static void match(List<String> out, String token, List<String> options) {
		String t = token == null ? "" : token.toLowerCase();
		for (String o : options) {
			if (o.startsWith(t)) {
				out.add(o);
			}
		}
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
