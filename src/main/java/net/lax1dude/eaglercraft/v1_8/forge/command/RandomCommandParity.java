package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 {@code /random}:
 *   random value <range> [<sequence>]
 *   random roll  <range> [<sequence>]
 *
 * Vanilla semantics (Minecraft Wiki /random, fetched 2026-08-29):
 *   - range = inclusive int bounds (vanilla RangeArgument.Ints; may not be
 *     reversed);
 *   - "value" does NOT broadcast; its result stat = the drawn value;
 *   - "roll" broadcasts who rolled what and from which range;
 *   - a named sequence is a persistent Random seeded deterministically from
 *     the world seed + sequence id — the same world seed reproduces the same
 *     series (vanilla uses per-sequence random sources with the same
 *     property).
 *
 * HONEST BOUNDARY (§19.8): vanilla persists sequence state inside the world
 * save; OmniMod keeps sequences for the session (re-seeded deterministically
 * from the world seed at first use — same world seed → same rolls) and clears
 * them on world unload. Persistence across sessions is a documented future
 * step, never silent state loss.
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-RANDOMCMD-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public class RandomCommandParity implements ICommand {

	/** Production seed provider = the loaded world's seed (harness may pin one). */
	public interface SeedProvider {
		long worldSeed();
	}

	private static volatile SeedProvider seedProvider;

	public static void setSequenceSeedForTest(long seed) {
		seedProvider = new SeedProvider() {
			@Override
			public long worldSeed() {
				return seed;
			}
		};
		sequences.clear();
	}

	private static final SeedProvider DEFAULT_SEED = new SeedProvider() {
		@Override
		public long worldSeed() {
			try {
				net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
				if (server != null && server.worldServers != null && server.worldServers.length > 0
						&& server.worldServers[0] != null) {
					return server.worldServers[0].getSeed();
				}
			} catch (Throwable ignored) {
				// no live world — sequence seeding falls back to a constant salt
			}
			return 0L;
		}
	};

	private static final Map<String, Random> sequences = new ConcurrentHashMap<String, Random>();

	private Random sequenceRandom(String name) {
		Random random = sequences.get(name);
		if (random == null) {
			SeedProvider provider = seedProvider != null ? seedProvider : DEFAULT_SEED;
			long seed = provider.worldSeed() * 31L + name.hashCode() * 1000003L;
			random = new Random(seed);
			sequences.put(name, random);
		}
		return random;
	}

	public RandomCommandParity() {
	}

	@Override
	public String getCommandName() {
		return "random";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/random (value|roll) <range> [<sequence>]";
	}

	@Override
	public List<String> getCommandAliases() {
		return Collections.emptyList();
	}

	/** Vanilla 1.20.1 gate level for /random. */
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
			GapFixRuntimeLog.hit("random", "RandomCommandParity", "parse", "syntax_fail",
					"reason=" + e.getMessage() + " input=" + input);
			throw new CommandException("Incorrect argument for command /random: "
					+ String.valueOf(e.getMessage()));
		} catch (CommandException ce) {
			throw ce;
		} catch (Throwable t) {
			GapFixRuntimeLog.error("random", "RandomCommandParity", "process", "fail",
					t.getClass().getSimpleName(), "input=" + input + " err=" + String.valueOf(t.getMessage()));
			throw new CommandException("/random command failed: " + String.valueOf(t.getMessage()));
		}
	}

	private void process(ICommandSender sender, StringReader reader) throws Exception {
		reader.skipWhitespace();
		String mode = readWord(reader);
		if (!"value".equals(mode) && !"roll".equals(mode)) {
			throw new CommandSyntaxException("Expected value or roll");
		}
		reader.skipWhitespace();
		// vanilla /random uses the DASH range syntax ("1-6" or a single "5"),
		// NOT the shared score-match "1..6" form (Minecraft Wiki /random,
		// fetched 2026-08-29) — parsed here, not via MinMaxArgument.
		int min = reader.readInt();
		int max = min;
		if (reader.canRead() && reader.peek() == '-') {
			reader.skip();
			max = reader.readInt();
		}
		if (min > max) {
			throw new CommandException("Min must not be greater than max");
		}
		reader.skipWhitespace();
		String rest = reader.getRemaining().trim();
		if (rest.indexOf(' ') >= 0) {
			throw new CommandSyntaxException("Too many arguments for /random");
		}
		Random random;
		if (rest.isEmpty()) {
			random = new Random(); // vanilla: the world's random source
		} else {
			random = sequenceRandom(rest);
		}
		int drawn = min + random.nextInt(max - min + 1);
		sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, drawn);
		if ("roll".equals(mode)) {
			if (sender.sendCommandFeedback()) {
				sender.addChatMessage(new ChatComponentText(
						sender.getName() + " rolled " + drawn + " from range " + min + "-" + max));
			}
		}
		GapFixRuntimeLog.hit("random", "RandomCommandParity", mode, "ok",
				"drawn=" + drawn + " range=" + min + "-" + max + " sequence="
						+ (rest.isEmpty() ? "<world>" : rest));
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
			if ("value".startsWith(args[0].toLowerCase())) {
				out.add("value");
			}
			if ("roll".startsWith(args[0].toLowerCase())) {
				out.add("roll");
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
