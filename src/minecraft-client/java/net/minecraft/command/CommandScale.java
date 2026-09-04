package net.minecraft.command;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.forge.scale.EntityScaleRuntime;
import net.lax1dude.eaglercraft.v1_8.forge.scale.EntityScaleType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

public class CommandScale extends CommandBase {

	@Override
	public String getCommandName() {
		return "scale";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/scale <set|add|subtract|multiply|divide|get|compute|delay|persist|reset|nbt> ...";
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getCommandUsage(sender), new Object[0]);
		}

		String sub = args[0].toLowerCase();
		if ("set".equals(sub) || "add".equals(sub) || "subtract".equals(sub) || "multiply".equals(sub)
				|| "divide".equals(sub)) {
			handleNumericOperation(sender, args, sub);
			return;
		}
		if ("get".equals(sub) || "compute".equals(sub)) {
			handleGet(sender, args, "compute".equals(sub));
			return;
		}
		if ("delay".equals(sub)) {
			handleDelay(sender, args);
			return;
		}
		if ("persist".equals(sub)) {
			handlePersist(sender, args);
			return;
		}
		if ("reset".equals(sub)) {
			handleReset(sender, args);
			return;
		}
		if ("nbt".equals(sub)) {
			handleNbt(sender, args);
			return;
		}

		throw new WrongUsageException(getCommandUsage(sender), new Object[0]);
	}

	private static void handleNumericOperation(ICommandSender sender, String[] args, String op) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException("/scale " + op + " <value>|<scale_type> <value> [entity]", new Object[0]);
		}

		EntityScaleType type = EntityScaleType.BASE;
		int valueIdx = 1;
		EntityScaleType parsed = EntityScaleType.byId(args[1]);
		if (parsed != null) {
			type = parsed;
			valueIdx = 2;
		}
		if (valueIdx >= args.length) {
			throw new WrongUsageException("Missing numeric value", new Object[0]);
		}

		float value = (float) parseDouble(args[valueIdx]);
		Entity target = resolveTarget(sender, args, valueIdx + 1);

		if ("set".equals(op)) {
			EntityScaleRuntime.setScale(target, type, value);
		} else {
			EntityScaleRuntime.applyOperation(target, type, op, value);
		}

		sender.addChatMessage(new ChatComponentText("[Scale] " + target.getName() + " " + type.getId() + " = "
				+ EntityScaleRuntime.getRawScale(target, type)));
	}

	private static void handleGet(ICommandSender sender, String[] args, boolean computed) throws CommandException {
		EntityScaleType type = EntityScaleType.BASE;
		int entityIdx = 1;
		if (args.length >= 2) {
			EntityScaleType parsed = EntityScaleType.byId(args[1]);
			if (parsed != null) {
				type = parsed;
				entityIdx = 2;
			}
		}

		Entity target = resolveTarget(sender, args, entityIdx);
		float value = computed ? EntityScaleRuntime.getComputedScale(target, type)
				: EntityScaleRuntime.getRawScale(target, type);
		String mode = computed ? "computed" : "raw";
		sender.addChatMessage(
				new ChatComponentText("[Scale] " + target.getName() + " " + type.getId() + " (" + mode + ") = " + value));
	}

	private static void handleDelay(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException("/scale delay <set|get> ...", new Object[0]);
		}
		String op = args[1].toLowerCase();
		if ("set".equals(op)) {
			if (args.length < 3) {
				throw new WrongUsageException("/scale delay set <ticks>|<scale_type> <ticks> [entity]", new Object[0]);
			}
			EntityScaleType type = EntityScaleType.BASE;
			int ticksIdx = 2;
			EntityScaleType parsed = EntityScaleType.byId(args[2]);
			if (parsed != null) {
				type = parsed;
				ticksIdx = 3;
			}
			if (ticksIdx >= args.length) {
				throw new WrongUsageException("Missing delay ticks", new Object[0]);
			}
			int ticks = parseInt(args[ticksIdx], 0, 72000);
			Entity target = resolveTarget(sender, args, ticksIdx + 1);
			EntityScaleRuntime.setDelay(target, type, ticks);
			sender.addChatMessage(new ChatComponentText(
					"[Scale] delay " + type.getId() + " for " + target.getName() + " = " + ticks + " ticks"));
			return;
		}

		if ("get".equals(op)) {
			EntityScaleType type = EntityScaleType.BASE;
			int entityIdx = 2;
			if (args.length >= 3) {
				EntityScaleType parsed = EntityScaleType.byId(args[2]);
				if (parsed != null) {
					type = parsed;
					entityIdx = 3;
				}
			}
			Entity target = resolveTarget(sender, args, entityIdx);
			int ticks = EntityScaleRuntime.getDelay(target, type);
			sender.addChatMessage(new ChatComponentText(
					"[Scale] delay " + type.getId() + " for " + target.getName() + " = " + ticks + " ticks"));
			return;
		}

		throw new WrongUsageException("/scale delay <set|get> ...", new Object[0]);
	}

	private static void handlePersist(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2) {
			throw new WrongUsageException("/scale persist <set|get|reset> ...", new Object[0]);
		}
		String op = args[1].toLowerCase();
		if ("set".equals(op)) {
			if (args.length < 3) {
				throw new WrongUsageException("/scale persist set (true|false)|<scale_type> (true|false) [entity]",
						new Object[0]);
			}
			EntityScaleType type = EntityScaleType.BASE;
			int valueIdx = 2;
			EntityScaleType parsed = EntityScaleType.byId(args[2]);
			if (parsed != null) {
				type = parsed;
				valueIdx = 3;
			}
			if (valueIdx >= args.length) {
				throw new WrongUsageException("Missing persist value", new Object[0]);
			}
			boolean value = parseBoolean(args[valueIdx]);
			Entity target = resolveTarget(sender, args, valueIdx + 1);
			EntityScaleRuntime.setPersistent(target, type, value);
			sender.addChatMessage(new ChatComponentText(
					"[Scale] persist " + type.getId() + " for " + target.getName() + " = " + value));
			return;
		}

		if ("get".equals(op)) {
			EntityScaleType type = EntityScaleType.BASE;
			int entityIdx = 2;
			if (args.length >= 3) {
				EntityScaleType parsed = EntityScaleType.byId(args[2]);
				if (parsed != null) {
					type = parsed;
					entityIdx = 3;
				}
			}
			Entity target = resolveTarget(sender, args, entityIdx);
			boolean value = EntityScaleRuntime.isPersistent(target, type);
			sender.addChatMessage(new ChatComponentText(
					"[Scale] persist " + type.getId() + " for " + target.getName() + " = " + value));
			return;
		}

		if ("reset".equals(op)) {
			EntityScaleType type = null;
			int entityIdx = 2;
			if (args.length >= 3) {
				type = EntityScaleType.byId(args[2]);
				if (type != null) {
					entityIdx = 3;
				}
			}
			Entity target = resolveTarget(sender, args, entityIdx);
			if (type == null) {
				for (EntityScaleType t : EntityScaleType.values()) {
					EntityScaleRuntime.setPersistent(target, t, false);
				}
				sender.addChatMessage(
						new ChatComponentText("[Scale] persist reset for all scales on " + target.getName()));
			} else {
				EntityScaleRuntime.setPersistent(target, type, false);
				sender.addChatMessage(new ChatComponentText(
						"[Scale] persist reset for " + type.getId() + " on " + target.getName()));
			}
			return;
		}

		throw new WrongUsageException("/scale persist <set|get|reset> ...", new Object[0]);
	}

	private static void handleReset(ICommandSender sender, String[] args) throws CommandException {
		EntityScaleType type = null;
		int entityIdx = 1;
		if (args.length >= 2) {
			type = EntityScaleType.byId(args[1]);
			if (type != null) {
				entityIdx = 2;
			}
		}
		Entity target = resolveTarget(sender, args, entityIdx);
		if (type == null) {
			EntityScaleRuntime.resetScale(target);
			sender.addChatMessage(new ChatComponentText("[Scale] reset all scales for " + target.getName()));
		} else {
			EntityScaleRuntime.resetScale(target, type);
			sender.addChatMessage(new ChatComponentText("[Scale] reset " + type.getId() + " for " + target.getName()));
		}
	}

	private static void handleNbt(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 2 || !"get".equalsIgnoreCase(args[1])) {
			throw new WrongUsageException("/scale nbt get [entity]", new Object[0]);
		}
		Entity target = resolveTarget(sender, args, 2);
		NBTTagCompound tag = EntityScaleRuntime.buildScaleNbt(target);
		sender.addChatMessage(new ChatComponentText("[ScaleNBT] " + target.getName() + " " + tag.toString()));
	}

	private static Entity resolveTarget(ICommandSender sender, String[] args, int index) throws CommandException {
		if (index < args.length) {
			return getEntity(sender, args[index], Entity.class);
		}
		Entity senderEntity = sender.getCommandSenderEntity();
		if (senderEntity != null) {
			return senderEntity;
		}
		EntityPlayerMP player = getCommandSenderAsPlayer(sender);
		if (player != null) {
			return player;
		}
		throw new WrongUsageException("A target entity is required for non-player command senders", new Object[0]);
	}

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		ArrayList<String> out = new ArrayList<>();
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args,
					new String[] { "set", "add", "subtract", "multiply", "divide", "get", "compute", "delay",
							"persist", "reset", "nbt" });
		}
		if (args.length == 2 && ("delay".equalsIgnoreCase(args[0]) || "persist".equalsIgnoreCase(args[0])
				|| "nbt".equalsIgnoreCase(args[0]))) {
			if ("delay".equalsIgnoreCase(args[0])) {
				return getListOfStringsMatchingLastWord(args, new String[] { "set", "get" });
			}
			if ("persist".equalsIgnoreCase(args[0])) {
				return getListOfStringsMatchingLastWord(args, new String[] { "set", "get", "reset" });
			}
			return getListOfStringsMatchingLastWord(args, new String[] { "get" });
		}

		EntityScaleType[] vals = EntityScaleType.values();
		for (int i = 0, l = vals.length; i < l; ++i) {
			out.add(vals[i].getId());
			out.add(vals[i].getShortId());
		}
		return getListOfStringsMatchingLastWord(args, out.toArray(new String[out.size()]));
	}
}
