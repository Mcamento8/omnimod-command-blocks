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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.BlockPos;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /stopsound} parity
 * — real sound stopping on the real client sound engine, every map, every
 * mod, zero hardcode.
 *
 * WHAT WAS BROKEN: /stopsound does not exist (1.9.3+ command) — 1.8 has no
 * stop-sound packet at all. Map sound design (stop music, stop ambience,
 * stop looped playsounds — the bread and butter of adventure maps) was
 * impossible: a started sound played to the end with no command to stop it.
 *
 * PIPELINE (real engine paths):
 * <pre>
 *  /stopsound <targets> [*|master|music|record|weather|block|hostile|
 *                        neutral|player|ambient|voice] [*|sound-id]
 *     server → OMNIMOD|StopSound custom payload (the proven channel used
 *              by OMNIMOD|BossBar / OMNIMOD|OpenScreen)
 *     → NetHandlerPlayClient branch → SoundHandler → real
 *       EaglercraftSoundManager filtered stop (category + sound name).
 * </pre>
 * {@code *} wildcards stop everything matching. Sounds that already ended
 * are simply absent from the active list (same as vanilla). Feedback
 * messages follow the vanilla "Stopped all sounds" / per-target style.
 *
 * Doc-ID: MCBP-STOPSOUND-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public class StopsoundCommandParity implements ICommand {

	/** Vanilla 1.20.1 source categories (sound_category names). */
	private static final List<String> SOURCES = Collections.unmodifiableList(Arrays.asList(
			"master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice"));

	@Override
	public String getCommandName() {
		return "stopsound";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/stopsound <targets> [*|source] [*|sound]";
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
		return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), "stopsound");
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (args == null || args.length == 0) {
			throw new CommandException("Expected: stopsound <targets> [*|source] [*|sound]");
		}
		String source = args.length >= 2 ? args[1] : "*";
		String sound = args.length >= 3 ? joinFrom(args, 2) : "*";
		if (!"*".equals(source) && !SOURCES.contains(source.toLowerCase())) {
			throw new CommandException("Unknown sound source '" + source + "'");
		}
		if ("*".equals(source)) {
			source = "*";
		} else {
			source = source.toLowerCase();
		}

		List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
		try {
			players.addAll(new EntitySelector(args[0]).findPlayers(sender));
		} catch (Throwable t) {
			GapFixRuntimeLog.hit("stopsound", "StopsoundCommandParity", "resolve", "fail",
					"token=" + args[0] + " err=" + String.valueOf(t.getMessage()));
		}
		if (players.isEmpty()) {
			throw new CommandException("commands.generic.player.notFound");
		}
		int stopped = 0;
		for (EntityPlayerMP p : players) {
			if (SoundStopBridge.stopSounds(p, source, sound)) {
				++stopped;
			}
		}
		sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, stopped);
		GapFixRuntimeLog.hit("stopsound", "StopsoundCommandParity", "stop", "ok",
				"targets=" + stopped + " source=" + source + " sound=" + sound);
		if ("*".equals(sound) && "*".equals(source)) {
			feedback(sender, "Stopped all sounds for " + stopped + " player(s)");
		} else {
			feedback(sender, "Stopped sound '" + ("*".equals(sound) ? "" : sound) + "'"
					+ ("*".equals(source) ? "" : " in source '" + source + "'")
					+ " for " + stopped + " player(s)");
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

	private static void feedback(ICommandSender sender, String message) {
		try {
			sender.addChatMessage(new net.minecraft.util.ChatComponentText(message));
		} catch (Throwable t) {
			GapFixRuntimeLog.hit("stopsound", "StopsoundCommandParity", "feedback", "fail",
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
			match(out, args[1], SOURCES);
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
		return index == 0;
	}

	@Override
	public int compareTo(ICommand other) {
		return this.getCommandName().compareTo(other.getCommandName());
	}
}
