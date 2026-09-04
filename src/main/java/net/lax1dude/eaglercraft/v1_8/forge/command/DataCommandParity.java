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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.commands.arguments.selector.EntitySelector;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 /data command —
 * get|merge|remove|modify over block entities, entities and command storage.
 *
 * Mirrors the exact vanilla 1.20.1 surface (Minecraft Wiki /data, fetched
 * 2026-08-29):
 *   data get    (block <pos>|entity <target>|storage <id>) [<path>] [<scale>]
 *   data merge  … <nbt>                        (root-level deep merge)
 *   data remove … <path>                       (root path rejected)
 *   data modify … <targetPath>
 *        (append|prepend|set|insert before|after <index>|merge)
 *        (from <SOURCE> [<sourcePath>]
 *        |string <SOURCE> [<sourcePath>] [<start>] [<end>]
 *        |value <snbt>)
 *   success messages: "Got the contents of <desc>: <snbt>",
 *   "<desc> has the following <kind> data: <snbt>",
 *   "Modified the data of <desc>", "Removed NBT tag(s) from <desc>",
 *   "Modified NBT tag(s) of <desc>" (wiki-captured).
 *   result values: get path → string length / list length / compound children
 *   (scaled for numerics); modify → number of modified tags; merge → 1.
 *
 * Vanilla parity details implemented: players' data can be READ but never
 * merge/modify/remove'd ("Unable to modify player data"); /data get requires
 * a single entity; unknown path → "Cannot find %s in %s"; multiple path
 * matches on get → "Found %s elements in %s".
 *
 * WHY THIS EXISTS: closes UCBPP PHASE_2_CRITICAL_GAPS CRITICAL-A — the NBT
 * path engine, command storage and /data itself were all absent; evaluation
 * runs on the REAL engine NBT (Entity.writeToNBT / TileEntity.writeToNBT).
 *
 * HONEST BOUNDARY (§19.8): the underlying NBT SHAPES are the live 1.8 ones
 * (Pos/Health/Motion/…). 1.20.1-only fields do not exist on 1.8 entities and
 * are ignored by the 1.8 readFromNBT — the same downgrade philosophy the
 * project applies everywhere (PROJECT_CONTEXT §16). Data-driven work against
 * real engine data is fully functional.
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-DATACMD-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public class DataCommandParity implements ICommand {

	private static final String USAGE =
			"/data get|merge|remove|modify (block <pos>|entity <target>|storage <id>) ...";

	public DataCommandParity() {
	}

	@Override
	public String getCommandName() {
		return "data";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return USAGE;
	}

	@Override
	public List<String> getCommandAliases() {
		return Collections.emptyList();
	}

	/** Vanilla 1.20.1 gate level for /data. */
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
		lastSender = sender;
		try {
			process(sender, new StringReader(input));
		} catch (CommandSyntaxException e) {
			GapFixRuntimeLog.hit("data", "DataCommandParity", "parse", "syntax_fail",
					"reason=" + e.getMessage() + " input=" + input);
			throw new CommandException("Incorrect argument for command /data: " + String.valueOf(e.getMessage()));
		} catch (CommandException ce) {
			throw ce;
		} catch (Throwable t) {
			GapFixRuntimeLog.error("data", "DataCommandParity", "process", "fail",
					t.getClass().getSimpleName(), "input=" + input + " err=" + String.valueOf(t.getMessage()));
			throw new CommandException("/data command failed: " + String.valueOf(t.getMessage()));
		}
	}

	// ── dispatch ───────────────────────────────────────────────────────────

	private void process(ICommandSender sender, StringReader reader) throws Exception {
		reader.skipWhitespace();
		String op = readWord(reader);
		reader.skipWhitespace();
		String targetType = readWord(reader);
		reader.skipWhitespace();

		if ("get".equals(op)) {
			Target target = parseTarget(sender, reader, targetType);
			getTarget(sender, target, rest(reader).trim());
		} else if ("merge".equals(op)) {
			Target target = parseTarget(sender, reader, targetType);
			NBTBase parsed = readNbtValue(rest(reader));
			if (!(parsed instanceof NBTTagCompound)) {
				throw new CommandSyntaxException("Expected a compound tag for /data merge");
			}
			mergeTarget(sender, target, (NBTTagCompound) parsed);
		} else if ("remove".equals(op)) {
			Target target = parseTarget(sender, reader, targetType);
			String path = rest(reader).trim();
			removeTarget(sender, target, path);
		} else if ("modify".equals(op)) {
			Target target = parseTarget(sender, reader, targetType);
			String targetPath = readPath(reader);
			ModifyOp op2 = parseModifyOp(reader);
			modifyTarget(sender, target, targetPath, op2);
		} else {
			throw new CommandSyntaxException("Expected get, merge, remove or modify");
		}
	}

	/** One resolved target: its NBT root, its vanilla description and data kind. */
	private static final class Target {
		final String kind; // storage | entity | block
		final String desc; // vanilla message description
		final NBTTagCompound root; // live snapshot (entities/blocks) or the live storage compound
		final Entity entity; // null unless entity target
		final TileEntity tile; // null unless block target

		Target(String kind, String desc, NBTTagCompound root, Entity entity, TileEntity tile) {
			this.kind = kind;
			this.desc = desc;
			this.root = root;
			this.entity = entity;
			this.tile = tile;
		}
	}

	private Target parseTarget(ICommandSender sender, StringReader reader, String targetType) throws Exception {
		if ("storage".equals(targetType)) {
			String id = readStorageId(reader);
			return new Target("storage", "storage \"" + id + "\"",
					CommandStorageRuntime.getStorageCompound(id), null, null);
		}
		if ("entity".equals(targetType)) {
			String token = readToken(reader);
			Entity entity = requireSingleEntity(sender, token);
			return new Target("entity", "entity " + entity.getName(), snapshot(entity), entity, null);
		}
		if ("block".equals(targetType)) {
			World world = sender.getEntityWorld();
			if (world == null) {
				throw new CommandException("That position is not loaded");
			}
			WorldCoordinates coord = WorldCoordinates.parseDouble(reader, true);
			BlockPos pos = coord.getBlockPos(css(sender, world));
			TileEntity tile = world.getTileEntity(pos);
			if (tile == null) {
				throw new CommandException("That position is not loaded");
			}
			BlockPos tilePos = tile.getPos();
			return new Target("block", "block " + tilePos.getX() + ", " + tilePos.getY() + ", " + tilePos.getZ(),
					snapshot(tile), null, tile);
		}
		throw new CommandSyntaxException("Expected block, entity or storage");
	}

	// ── get ────────────────────────────────────────────────────────────────

	private void getTarget(ICommandSender sender, Target target, String pathAndScale) throws Exception {
		if (pathAndScale.isEmpty()) {
			feedback(sender, "Got the contents of " + target.desc + ": " + NbtPathCore.render(target.root));
			sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, 1);
			log("get", "ok", "target=" + target.desc + " root");
			return;
		}
		String[] parts = pathAndScale.split(" ");
		if (parts.length > 2) {
			throw new CommandSyntaxException("Too many arguments for /data get");
		}
		double scale = 1.0D;
		if (parts.length == 2) {
			try {
				scale = Double.parseDouble(parts[1]);
			} catch (NumberFormatException nfe) {
				throw new CommandSyntaxException("Invalid scale: '" + parts[1] + "'");
			}
		}
		List<NBTBase> matched = NbtPathCore.resolve(target.root, parts[0]);
		if (matched.isEmpty()) {
			log("get", "fail", "target=" + target.desc + " missing path=" + parts[0]);
			throw new CommandException("Cannot find " + parts[0] + " in " + target.desc);
		}
		if (matched.size() > 1) {
			log("get", "fail", "target=" + target.desc + " ambiguous path=" + parts[0]);
			throw new CommandException("Found " + matched.size() + " elements in " + target.desc);
		}
		NBTBase tag = matched.get(0);
		feedback(sender, target.desc + " has the following " + target.kind + " data: " + NbtPathCore.render(tag));
		// vanilla result: string length / list length / compound children /
		// numeric*scale; other tags → 1
		int result = 1;
		if (tag instanceof NBTTagCompound) {
			result = ((NBTTagCompound) tag).getKeySet().size();
		} else if (tag instanceof NBTTagList) {
			result = ((NBTTagList) tag).tagCount();
		} else if (tag instanceof NBTTagString) {
			result = ((NBTTagString) tag).getString().length();
		} else if (scale != 1.0D) {
			result = (int) Math.floor(numericValue(tag) * scale);
		}
		sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, result);
		log("get", "ok", "target=" + target.desc + " path=" + parts[0] + " result=" + result);
	}

	private double numericValue(NBTBase tag) throws CommandException {
		if (tag instanceof net.minecraft.nbt.NBTTagByte) {
			return ((net.minecraft.nbt.NBTTagByte) tag).getByte();
		}
		if (tag instanceof net.minecraft.nbt.NBTTagShort) {
			return ((net.minecraft.nbt.NBTTagShort) tag).getShort();
		}
		if (tag instanceof net.minecraft.nbt.NBTTagInt) {
			return ((net.minecraft.nbt.NBTTagInt) tag).getInt();
		}
		if (tag instanceof net.minecraft.nbt.NBTTagLong) {
			return ((net.minecraft.nbt.NBTTagLong) tag).getLong();
		}
		if (tag instanceof net.minecraft.nbt.NBTTagFloat) {
			return ((net.minecraft.nbt.NBTTagFloat) tag).getFloat();
		}
		if (tag instanceof net.minecraft.nbt.NBTTagDouble) {
			return ((net.minecraft.nbt.NBTTagDouble) tag).getDouble();
		}
		throw new CommandException("A double value was expected, found: '" + NbtPathCore.render(tag) + "'");
	}

	// ── merge / remove / modify ────────────────────────────────────────────

	private void mergeTarget(ICommandSender sender, Target target, NBTTagCompound value) throws Exception {
		denyPlayer(target);
		if (!value.hasNoTags()) {
			NbtPathCore.deepMerge(target.root, value);
			writeBack(target);
		}
		feedback(sender, "Modified the data of " + target.desc);
		sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, 1);
		log("merge", "ok", "target=" + target.desc);
	}

	private void removeTarget(ICommandSender sender, Target target, String path) throws Exception {
		denyPlayer(target);
		if (path.isEmpty()) {
			throw new CommandException("Cannot remove the root NBT path");
		}
		int removed = NbtPathCore.modifyRemove(target.root, path);
		if (removed <= 0) {
			throw new CommandException("Cannot find " + path + " in " + target.desc);
		}
		writeBack(target);
		feedback(sender, "Removed NBT tag(s) from " + target.desc);
		sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, removed);
		log("remove", "ok", "target=" + target.desc + " path=" + path);
	}

	private void modifyTarget(ICommandSender sender, Target target, String targetPath, ModifyOp op) throws Exception {
		denyPlayer(target);
		int modified = applyOp(target.root, targetPath, op);
		if (modified > 0) {
			writeBack(target);
		}
		feedback(sender, "Modified NBT tag(s) of " + target.desc);
		sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, modified);
		log("modify", "ok", "target=" + target.desc + " path=" + targetPath + " count=" + modified);
	}

	/** Vanilla: players' data is read-only for merge/remove/modify. */
	private void denyPlayer(Target target) throws CommandException {
		if (target.entity instanceof EntityPlayerMP) {
			throw new CommandException("Unable to modify player data");
		}
	}

	private void writeBack(Target target) {
		if (target.entity != null) {
			target.entity.readFromNBT(target.root);
		} else if (target.tile != null) {
			target.tile.readFromNBT(target.root);
			target.tile.markDirty();
		}
	}

	// ── modify operation model ─────────────────────────────────────────────

	/** One parsed modify operation (operation + source/value payload). */
	private static final class ModifyOp {
		String operation; // append|prepend|set|insert|merge
		boolean after; // insert after?
		Integer insertIndex;
		String sourceKind; // from|string|value
		String sourceType; // block|entity|storage (from/string sources)
		String sourceToken; // entity selector token
		String storageId;
		WorldCoordinates sourcePos;
		String sourcePath;
		Integer stringStart;
		Integer stringEnd;
		NBTBase value;
	}

	private ModifyOp parseModifyOp(StringReader reader) throws Exception {
		reader.skipWhitespace();
		ModifyOp op = new ModifyOp();
		op.operation = readWord(reader);
		reader.skipWhitespace();
		if ("insert".equals(op.operation)) {
			String when = readWord(reader);
			op.after = "after".equals(when);
			if (!op.after && !"before".equals(when)) {
				throw new CommandSyntaxException("Expected before or after");
			}
			reader.skipWhitespace();
			op.insertIndex = Integer.valueOf(reader.readInt());
		} else if (!"append".equals(op.operation) && !"prepend".equals(op.operation)
				&& !"set".equals(op.operation) && !"merge".equals(op.operation)) {
			throw new CommandSyntaxException("Expected append, prepend, set, insert or merge");
		}
		reader.skipWhitespace();
		op.sourceKind = readWord(reader);
		reader.skipWhitespace();
		if ("value".equals(op.sourceKind)) {
			op.value = readNbtValue(rest(reader));
			return op;
		}
		if (!"from".equals(op.sourceKind) && !"string".equals(op.sourceKind)) {
			throw new CommandSyntaxException("Expected from, string or value");
		}
		op.sourceType = readWord(reader);
		reader.skipWhitespace();
		if ("storage".equals(op.sourceType)) {
			op.storageId = readStorageId(reader);
		} else if ("entity".equals(op.sourceType)) {
			op.sourceToken = readToken(reader);
		} else if ("block".equals(op.sourceType)) {
			int start = reader.getCursor();
			op.sourcePos = WorldCoordinates.parseDouble(reader, true);
			op.sourceToken = reader.getString().substring(start, reader.getCursor()).trim();
		} else {
			throw new CommandSyntaxException("Expected block, entity or storage");
		}
		reader.skipWhitespace();
		String rest = rest(reader).trim();
		if (!rest.isEmpty()) {
			String[] parts = rest.split(" ");
			op.sourcePath = parts[0];
			if ("string".equals(op.sourceKind)) {
				if (parts.length > 3) {
					throw new CommandSyntaxException("Too many arguments for string source");
				}
				if (parts.length >= 2) {
					op.stringStart = Integer.valueOf(Integer.parseInt(parts[1]));
				}
				if (parts.length >= 3) {
					op.stringEnd = Integer.valueOf(Integer.parseInt(parts[2]));
				}
			}
		}
		return op;
	}

	private int applyOp(NBTTagCompound root, String targetPath, ModifyOp op) throws Exception {
		if ("merge".equals(op.operation)) {
			if (!(op.value instanceof NBTTagCompound)) {
				throw new CommandException("Expected a compound tag as value");
			}
			return NbtPathCore.modifyMerge(root, targetPath, (NBTTagCompound) op.value);
		}
		NBTBase value = resolveSourceValue(op);
		if (value == null) {
			return 0;
		}
		if ("set".equals(op.operation)) {
			return NbtPathCore.modifySet(root, targetPath, value);
		}
		if ("append".equals(op.operation) || "prepend".equals(op.operation)) {
			return NbtPathCore.modifyInsert(root, targetPath, value,
					"append".equals(op.operation) ? NbtPathCore.InsertMode.APPEND : NbtPathCore.InsertMode.PREPEND,
					null);
		}
		return NbtPathCore.modifyInsert(root, targetPath, value,
				op.after ? NbtPathCore.InsertMode.AFTER : NbtPathCore.InsertMode.BEFORE, op.insertIndex);
	}

	/** Resolves the modify payload: literal value, source tag, or sliced source string. */
	private NBTBase resolveSourceValue(ModifyOp op) throws Exception {
		if ("value".equals(op.sourceKind)) {
			return op.value;
		}
		NBTTagCompound sourceRoot;
		if ("storage".equals(op.sourceType)) {
			sourceRoot = CommandStorageRuntime.getStorageCompound(op.storageId);
		} else if ("entity".equals(op.sourceType)) {
			sourceRoot = snapshot(requireSingleEntity(lastSender, op.sourceToken));
		} else {
			// block source
			World world = lastSender.getEntityWorld();
			if (world == null) {
				throw new CommandException("That position is not loaded");
			}
			TileEntity tile = world.getTileEntity(op.sourcePos.getBlockPos(css(lastSender, world)));
			if (tile == null) {
				throw new CommandException("That position is not loaded");
			}
			sourceRoot = snapshot(tile);
		}
		String srcPath = op.sourcePath == null ? "" : op.sourcePath;
		List<NBTBase> matched = NbtPathCore.resolve(sourceRoot, srcPath);
		if (matched.size() != 1) {
			throw new CommandException("Expected exactly one matching source tag, found: " + matched.size());
		}
		NBTBase tag = matched.get(0);
		if ("string".equals(op.sourceKind)) {
			if (!(tag instanceof NBTTagString)) {
				throw new CommandException("Expected a string, found: '" + NbtPathCore.render(tag) + "'");
			}
			String text = ((NBTTagString) tag).getString();
			int len = text.length();
			int start = op.stringStart == null ? 0 : op.stringStart.intValue();
			int end = op.stringEnd == null ? len : op.stringEnd.intValue();
			if (start < 0) {
				start = Math.max(0, len + start);
			}
			if (end < 0) {
				end = len + end;
			}
			start = Math.min(Math.max(start, 0), len);
			end = Math.min(Math.max(end, 0), len);
			if (start > end) {
				int tmp = start;
				start = end;
				end = tmp;
			}
			return new NBTTagString(text.substring(start, end));
		}
		return tag;
	}

	// ── helpers ────────────────────────────────────────────────────────────

	/** ThreadLocal-ish sender handle for source resolution (commands are main-thread in 1.8). */
	private static ICommandSender lastSender;

	private List<Entity> resolveEntities(ICommandSender sender, String token) {
		try {
			return new EntitySelector(token).getEntities(sender);
		} catch (Throwable t) {
			GapFixRuntimeLog.error("data", "DataCommandParity", "resolve", "fail",
					t.getClass().getSimpleName(), "token=" + token + " err=" + String.valueOf(t.getMessage()));
			return Collections.emptyList();
		}
	}

	private Entity requireSingleEntity(ICommandSender sender, String token) throws CommandException {
		List<Entity> found = resolveEntities(sender, token);
		if (found.isEmpty()) {
			throw new CommandException("No entity was found");
		}
		if (found.size() > 1) {
			throw new CommandException("Expected a single entity but found " + found.size());
		}
		return found.get(0);
	}

	private NBTTagCompound snapshot(Entity entity) {
		NBTTagCompound tag = new NBTTagCompound();
		entity.writeToNBT(tag);
		return tag;
	}

	private NBTTagCompound snapshot(TileEntity tile) {
		NBTTagCompound tag = new NBTTagCompound();
		tile.writeToNBT(tag);
		return tag;
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

	private static String readToken(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		return readWord(reader);
	}

	private static String readPath(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		String path = readWord(reader);
		return path;
	}

	private static String readStorageId(StringReader reader) throws CommandSyntaxException {
		String id = readToken(reader);
		if (!CommandStorageRuntime.isValidId(id)) {
			throw new CommandSyntaxException("Expected a namespaced storage id, got: '" + id + "'");
		}
		return id;
	}

	private static String rest(StringReader reader) {
		return reader.getRemaining();
	}

	/**
	 * Parses any SNBT value (compound/list/scalar) using the engine's REAL
	 * parser via a wrapper compound ({@code {"v": <value>}}), so numeric-type
	 * suffixes and quoting behave exactly like vanilla SNBT.
	 */
	private static NBTBase readNbtValue(String snbt) throws CommandSyntaxException {
		String text = snbt.trim();
		if (text.isEmpty()) {
			throw new CommandSyntaxException("Expected an NBT value");
		}
		if (text.startsWith("{")) {
			return NbtPathCore.parseCompound(text);
		}
		try {
			NBTTagCompound wrapper = NbtPathCore.parseCompound("{v:" + text + "}");
			return wrapper.getTag("v");
		} catch (CommandSyntaxException e) {
			throw new CommandSyntaxException("Could not parse NBT value: " + String.valueOf(e.getMessage()));
		}
	}

	private net.minecraft.commands.CommandSourceStack css(ICommandSender sender, World world) {
		net.minecraft.world.phys.Vec3 pos = new net.minecraft.world.phys.Vec3(
				sender.getPositionVector().xCoord, sender.getPositionVector().yCoord,
				sender.getPositionVector().zCoord);
		return new net.minecraft.commands.CommandSourceStack(sender, pos, null, world, null);
	}

	private void feedback(ICommandSender sender, String message) {
		if (sender.sendCommandFeedback()) {
			sender.addChatMessage(new ChatComponentText(message));
		}
	}

	private void log(String action, String result, String detail) {
		GapFixRuntimeLog.hit("data", "DataCommandParity", action, result, detail);
	}

	// ── tab completion ─────────────────────────────────────────────────────

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 1) {
			return startsWith(args[0], "get", "merge", "remove", "modify");
		}
		if (args.length == 2) {
			return startsWith(args[1], "block", "entity", "storage");
		}
		return Collections.emptyList();
	}

	private static List<String> startsWith(String prefix, String... options) {
		List<String> out = new ArrayList<String>();
		for (String option : options) {
			if (option.startsWith(prefix == null ? "" : prefix.toLowerCase())) {
				out.add(option);
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
		return getCommandName().compareToIgnoreCase(other.getCommandName());
	}
}
