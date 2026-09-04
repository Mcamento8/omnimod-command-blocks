package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.commands.arguments.selector.EntitySelector;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 {@code /bossbar}:
 *   bossbar add <id> <name>
 *   bossbar get <id> (max|players|value|visible)
 *   bossbar list [<id>]
 *   bossbar remove <id>
 *   bossbar set <id> (color|style|name|max|players|value|visible) …
 *
 * Vanilla semantics (Minecraft Wiki /bossbar, fetched 2026-08-29):
 *   - defaults: color=white, max=100, name="Boss Bar", style=progress,
 *     value=0, visible=true;
 *   - set value/max results = the OLD value; set players result = the player
 *     count; set visible result = 1 when toggled else 0;
 *   - get supports max|players|value|visible (color/style/name are set-only);
 *   - get on a non-listed id fails ("No boss bar with the ID '<id>' exists");
 *   - add on an existing id fails ("A boss bar with the ID '<id>' already
 *     exists").
 *
 * [Agent Note 2026-09-04] GMF round: every mutation now calls
 * {@link BossBarRuntime#notifyChanged()} so the client HUD
 * ({@link ClientBossBarRuntime}) updates live. The old honest boundary
 * ("players is tracked but draws nothing") is CLOSED — custom bars render
 * on every platform where the vanilla packet pipeline works.
 *
 * HONEST BOUNDARY (§19.8, updated): singleplayer/integrated-server targets
 * only; bars are runtime state cleared on world unload — maps re-create
 * them from init batches or {@code _dev/functions/load.mcfunction}.
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-BOSSBARCMD-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public class BossBarCommandParity implements ICommand {

	public BossBarCommandParity() {
	}

	@Override
	public String getCommandName() {
		return "bossbar";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/bossbar add|get|list|remove|set ...";
	}

	@Override
	public List<String> getCommandAliases() {
		return Collections.emptyList();
	}

	/** Vanilla 1.20.1 gate level for /bossbar. */
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
			GapFixRuntimeLog.hit("bossbar", "BossBarCommandParity", "parse", "syntax_fail",
					"reason=" + e.getMessage() + " input=" + input);
			throw new CommandException("Incorrect argument for command /bossbar: "
					+ String.valueOf(e.getMessage()));
		} catch (CommandException ce) {
			throw ce;
		} catch (Throwable t) {
			GapFixRuntimeLog.error("bossbar", "BossBarCommandParity", "process", "fail",
					t.getClass().getSimpleName(), "input=" + input + " err=" + String.valueOf(t.getMessage()));
			throw new CommandException("/bossbar command failed: " + String.valueOf(t.getMessage()));
		}
	}

	private void process(ICommandSender sender, StringReader reader) throws Exception {
		reader.skipWhitespace();
		String sub = readWord(reader);
		if ("add".equals(sub)) {
			reader.skipWhitespace();
			String id = readId(reader);
			reader.skipWhitespace();
			String name = rest(reader).trim();
			if (name.isEmpty()) {
				throw new CommandSyntaxException("Expected a boss bar name");
			}
			if (BossBarRuntime.exists(id)) {
				throw new CommandException("A boss bar with the ID '" + id + "' already exists");
			}
			BossBarRuntime.add(id, unquote(name));
			BossBarRuntime.notifyChanged();
			feedback(sender, "Created custom bossbar " + id);
			log("add", "ok", "id=" + id);
			return;
		}
		if ("list".equals(sub)) {
			List<BossBarRuntime.BossBar> bars = BossBarRuntime.list();
			if (bars.isEmpty()) {
				feedback(sender, "There are no custom bossbars active");
				return;
			}
			StringBuilder sb = new StringBuilder("There are " + bars.size() + " custom bossbar(s) active: ");
			for (int i = 0; i < bars.size(); ++i) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(bars.get(i).id);
			}
			feedback(sender, sb.toString());
			log("list", "ok", "count=" + bars.size());
			return;
		}
		if ("remove".equals(sub)) {
			reader.skipWhitespace();
			String id = readId(reader);
			BossBarRuntime.BossBar removed = BossBarRuntime.remove(id);
			if (removed == null) {
				throw new CommandException("No boss bar with the ID '" + id + "' exists");
			}
			BossBarRuntime.notifyChanged();
			feedback(sender, "Removed custom bossbar " + id);
			log("remove", "ok", "id=" + id);
			return;
		}
		if ("get".equals(sub)) {
			reader.skipWhitespace();
			String id = readId(reader);
			reader.skipWhitespace();
			String property = readWord(reader);
			BossBarRuntime.BossBar bar = requireBar(id);
			if ("max".equals(property)) {
				feedback(sender, "Boss bar '" + id + "' has a maximum of " + fmt(bar.max));
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, (int) bar.max);
			} else if ("value".equals(property)) {
				feedback(sender, "Boss bar '" + id + "' has value " + fmt(bar.value));
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, (int) bar.value);
			} else if ("visible".equals(property)) {
				feedback(sender, "Boss bar '" + id + "' is " + (bar.visible ? "shown" : "hidden"));
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, bar.visible ? 1 : 0);
			} else if ("players".equals(property)) {
				if (bar.players.isEmpty()) {
					feedback(sender, "Boss bar '" + id + "' has no players online");
				} else {
					feedback(sender, "Boss bar '" + id + "' has " + bar.players.size()
							+ " player(s) online: " + String.join(", ", bar.players));
				}
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, bar.players.size());
			} else {
				throw new CommandSyntaxException("Expected max, players, value or visible");
			}
			log("get", "ok", "id=" + id + " property=" + property);
			return;
		}
		if ("set".equals(sub)) {
			reader.skipWhitespace();
			String id = readId(reader);
			reader.skipWhitespace();
			String property = readWord(reader);
			reader.skipWhitespace();
			BossBarRuntime.BossBar bar = requireBar(id);
			if ("color".equals(property)) {
				String color = readWord(reader);
				validateEnum(color, BossBarRuntime.COLORS);
				bar.color = color;
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, 1);
			} else if ("style".equals(property)) {
				String style = readWord(reader);
				validateEnum(style, BossBarRuntime.STYLES);
				bar.style = style;
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, 1);
			} else if ("name".equals(property)) {
				bar.name = unquote(rest(reader).trim());
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, 1);
			} else if ("max".equals(property)) {
				int newMax = reader.readInt();
				if (newMax < 1) {
					throw new CommandException("Max must be at least 1");
				}
				int old = (int) bar.max;
				bar.max = (float) newMax;
				bar.value = Math.min(bar.value, bar.max);
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, old);
			} else if ("value".equals(property)) {
				int newValue = reader.readInt();
				if (newValue < 0) {
					throw new CommandException("Value must be non-negative");
				}
				int old = (int) bar.value;
				bar.value = (float) newValue;
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, old);
			} else if ("visible".equals(property)) {
				String flag = readWord(reader);
				boolean visible = parseBool(flag);
				int result = visible != bar.visible ? 1 : 0; // vanilla: 1 when toggled
				bar.visible = visible;
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, result);
			} else if ("players".equals(property)) {
				bar.players.clear();
				String rest = rest(reader).trim();
				if (!rest.isEmpty()) {
					List<net.minecraft.entity.player.EntityPlayerMP> players =
							new EntitySelector(rest).findPlayers(sender);
					for (net.minecraft.entity.player.EntityPlayerMP player : players) {
						bar.players.add(player.getName());
					}
				}
				sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, bar.players.size());
			} else {
				throw new CommandSyntaxException(
						"Expected color, style, name, max, players, value or visible");
			}
			BossBarRuntime.notifyChanged();
			feedback(sender, "Modified boss bar '" + id + "' (" + property + ")");
			log("set", "ok", "id=" + id + " property=" + property);
			return;
		}
		throw new CommandSyntaxException("Expected add, get, list, remove or set");
	}

	private BossBarRuntime.BossBar requireBar(String id) throws CommandException {
		BossBarRuntime.BossBar bar = BossBarRuntime.get(id);
		if (bar == null) {
			throw new CommandException("No boss bar with the ID '" + id + "' exists");
		}
		return bar;
	}

	private static String fmt(float value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	private static void validateEnum(String value, String[] options) throws CommandSyntaxException {
		for (String option : options) {
			if (option.equals(value)) {
				return;
			}
		}
		throw new CommandSyntaxException("Expected one of " + String.join(", ", options) + ", got: '" + value + "'");
	}

	private static boolean parseBool(String text) throws CommandSyntaxException {
		if ("true".equals(text)) {
			return true;
		}
		if ("false".equals(text)) {
			return false;
		}
		throw new CommandSyntaxException("Expected true or false, got: '" + text + "'");
	}

	private static String unquote(String text) {
		if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
			return text.substring(1, text.length() - 1);
		}
		return text;
	}

	private static String readId(StringReader reader) throws CommandSyntaxException {
		String id = readWord(reader);
		if (!BossBarRuntime.isValidId(id)) {
			throw new CommandSyntaxException("Expected a namespaced bossbar id, got: '" + id + "'");
		}
		return id;
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

	private static String rest(StringReader reader) {
		return reader.getRemaining();
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

	private void feedback(ICommandSender sender, String message) {
		if (sender.sendCommandFeedback()) {
			sender.addChatMessage(new ChatComponentText(message));
		}
	}

	private void log(String action, String result, String detail) {
		GapFixRuntimeLog.hit("bossbar", "BossBarCommandParity", action, result, detail);
	}

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		List<String> out = new ArrayList<String>();
		if (args.length == 1) {
			for (String option : new String[] { "add", "get", "list", "remove", "set" }) {
				if (option.startsWith(args[0].toLowerCase())) {
					out.add(option);
				}
			}
			return out;
		}
		if (args.length == 3 && "get".equals(args[0])) {
			for (String option : new String[] { "max", "players", "value", "visible" }) {
				if (option.startsWith(args[2].toLowerCase())) {
					out.add(option);
				}
			}
			return out;
		}
		if (args.length == 3 && "set".equals(args[0])) {
			for (String option : new String[] { "color", "style", "name", "max", "players", "value", "visible" }) {
				if (option.startsWith(args[2].toLowerCase())) {
					out.add(option);
				}
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
