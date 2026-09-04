package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 NBT path engine for the
 * /data command family (and execute if-data / store paths).
 *
 * Mirrors {@code net.minecraft.commands.arguments.NbtPathArgument} semantics
 * from Forge 1.20.1 (the Phase-2 shim only STORES the path verbatim — this
 * core EVALUATES it, closing the documented honest boundary of UCBPP Phase 2,
 * see tmp_command_bridge_harness/PHASE_2_CRITICAL_GAPS.md CRITICAL-A).
 *
 * Path grammar (vanilla 1.20.1):
 *   ""               → the root tag itself
 *   key              → compound key access
 *   key.sub          → nested access
 *   "quoted key"     → quoted segment (basic escapes \" and \\)
 *   [i]              → list index (0-based, in-bounds required)
 *   [{}]             → every compound element of a list (fan-out)
 *   [{k:v,...}]      → every compound element matching the filter (partial,
 *                      recursive match; numeric filters compare by type+value)
 *
 * WHY THIS EXISTS: /data get|merge|remove|modify and execute if-data/store
 * need real evaluation against the engine's live 1.8 NBT (the same NBT the
 * engine actually stores — Pos/Health/… shapes), not a parallel schema.
 *
 * GENERAL — operates on the real 1.8 NBT classes only; no mod id, no command
 * name, no hardcode. Java-8 compatible (mobile parity).
 *
 * Doc-ID: UCBPP-P3-NBTPATHCORE-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public final class NbtPathCore {

	private NbtPathCore() {
	}

	/** Modify insert modes (vanilla /data modify … append|prepend|insert before|after). */
	public enum InsertMode {
		APPEND, PREPEND, BEFORE, AFTER
	}

	/** A mutable leaf location: list index or compound key inside its parent. */
	public static final class Location {
		public final NBTBase parent;
		public final String key; // null when index is set
		public final Integer index; // null when key is set

		Location(NBTBase parent, String key, Integer index) {
			this.parent = parent;
			this.key = key;
			this.index = index;
		}
	}

	// ── Segment model + parsing ────────────────────────────────────────────

	static final class Segment {
		String key; // null → invalid (bracket always follows a key)
		Integer index; // [i]
		NBTTagCompound filter; // [{...}]
		boolean filterAll; // [{}]
		boolean hasBracket;
	}

	public static List<Segment> parse(String path) throws CommandSyntaxException {
		List<Segment> segments = new ArrayList<Segment>();
		int i = 0;
		int len = path.length();
		while (i < len) {
			Segment seg = new Segment();
			char c = path.charAt(i);
			if (c == '[') {
				throw new CommandSyntaxException("Invalid NBT path position at " + i + ": " + path);
			}
			if (c == '"') {
				StringBuilder sb = new StringBuilder();
				++i;
				while (i < len && path.charAt(i) != '"') {
					if (path.charAt(i) == '\\' && i + 1 < len) {
						++i;
					}
					sb.append(path.charAt(i));
					++i;
				}
				if (i >= len) {
					throw new CommandSyntaxException("Unterminated quoted key in NBT path: " + path);
				}
				++i;
				seg.key = sb.toString();
			} else {
				int start = i;
				while (i < len && path.charAt(i) != '.' && path.charAt(i) != '[') {
					++i;
				}
				seg.key = path.substring(start, i);
				if (seg.key.isEmpty()) {
					throw new CommandSyntaxException("Empty key in NBT path: " + path);
				}
			}
			if (i < len && path.charAt(i) == '[') {
				int close = path.indexOf(']', i);
				if (close < 0) {
					throw new CommandSyntaxException("Unterminated '[' in NBT path: " + path);
				}
				String inner = path.substring(i + 1, close).trim();
				if (inner.equals("{}")) {
					seg.filterAll = true;
				} else if (inner.startsWith("{")) {
					seg.filter = parseCompound(inner);
				} else {
					try {
						seg.index = Integer.valueOf(Integer.parseInt(inner));
					} catch (NumberFormatException nfe) {
						throw new CommandSyntaxException("Invalid list index '" + inner + "' in NBT path: " + path);
					}
					if (seg.index.intValue() < 0) {
						throw new CommandSyntaxException(
								"Invalid (negative) list index '" + inner + "' in NBT path: " + path);
					}
				}
				seg.hasBracket = true;
				i = close + 1;
			}
			segments.add(seg);
			if (i < len) {
				if (path.charAt(i) == '.') {
					++i;
					if (i >= len) {
						throw new CommandSyntaxException("Trailing '.' in NBT path: " + path);
					}
				} else if (path.charAt(i) != '[') {
					throw new CommandSyntaxException(
							"Unexpected character '" + path.charAt(i) + "' in NBT path: " + path);
				}
			}
		}
		return segments;
	}

	// ── Resolution (get) ───────────────────────────────────────────────────

	/** Resolves the path against a root tag. Empty result = no match (vanilla "Cannot find"). */
	public static List<NBTBase> resolve(NBTBase root, String path) throws CommandSyntaxException {
		List<Segment> segments = parse(path);
		List<NBTBase> matched = new ArrayList<NBTBase>();
		matched.add(root);
		for (Segment seg : segments) {
			List<NBTBase> next = new ArrayList<NBTBase>();
			for (NBTBase tag : matched) {
				stepFanOut(tag, seg, next);
			}
			matched = next;
			if (matched.isEmpty()) {
				return matched;
			}
		}
		return matched;
	}

	/** Applies one segment to one tag, appending every match to {@code out} (fan-out for filters). */
	private static void stepFanOut(NBTBase tag, Segment seg, List<NBTBase> out) {
		NBTBase cur = tag;
		if (seg.key != null) {
			if (!(cur instanceof NBTTagCompound)) {
				return;
			}
			NBTTagCompound compound = (NBTTagCompound) cur;
			if (!compound.hasKey(seg.key)) {
				return;
			}
			cur = compound.getTag(seg.key);
		}
		if (seg.index != null) {
			if (!(cur instanceof NBTTagList)) {
				return;
			}
			NBTTagList list = (NBTTagList) cur;
			if (seg.index.intValue() >= list.tagCount()) {
				return;
			}
			out.add(list.get(seg.index.intValue()));
		} else if (seg.filterAll || seg.filter != null) {
			if (!(cur instanceof NBTTagList)) {
				return;
			}
			NBTTagList list = (NBTTagList) cur;
			for (int i = 0; i < list.tagCount(); ++i) {
				NBTBase item = list.get(i);
				if (item instanceof NBTTagCompound) {
					if (seg.filterAll || filterMatches((NBTTagCompound) item, seg.filter)) {
						out.add(item);
					}
				}
			}
		} else {
			out.add(cur);
		}
	}

	// ── Mutation locations (modify/insert/remove) ──────────────────────────

	/**
	 * Walks the parent chain (creating missing compound intermediates when
	 * {@code createMissing}) and returns the leaf locations for the LAST
	 * segment. A filtered last segment yields one location per matched element
	 * (its real list index), matching vanilla modify-over-filter semantics.
	 */
	public static List<Location> resolveLocations(NBTTagCompound root, String path, boolean createMissing)
			throws CommandSyntaxException {
		List<Segment> segments = parse(path);
		if (segments.isEmpty()) {
			throw new CommandSyntaxException("Cannot modify the root NBT path");
		}
		List<NBTBase> current = new ArrayList<NBTBase>();
		current.add(root);
		for (int si = 0; si < segments.size() - 1; ++si) {
			List<NBTBase> next = new ArrayList<NBTBase>();
			for (NBTBase tag : current) {
				stepOrCreate(tag, segments.get(si), createMissing, next);
			}
			current = next;
			if (current.isEmpty()) {
				return new ArrayList<Location>();
			}
		}
		Segment last = segments.get(segments.size() - 1);
		List<Location> locations = new ArrayList<Location>();
		for (NBTBase parent : current) {
			if (last.index != null) {
				if (parent instanceof NBTTagList) {
					NBTTagList list = (NBTTagList) parent;
					if (last.index.intValue() < list.tagCount()) {
						locations.add(new Location(list, null, last.index));
					}
				}
			} else if (last.filterAll || last.filter != null) {
				if (parent instanceof NBTTagList) {
					NBTTagList list = (NBTTagList) parent;
					for (int i = 0; i < list.tagCount(); ++i) {
						NBTBase item = list.get(i);
						if (item instanceof NBTTagCompound) {
							if (last.filterAll || filterMatches((NBTTagCompound) item, last.filter)) {
								locations.add(new Location(list, null, Integer.valueOf(i)));
							}
						}
					}
				}
			} else if (parent instanceof NBTTagCompound) {
				locations.add(new Location(parent, last.key, null));
			}
		}
		return locations;
	}

	/** Parent-chain step; creates missing compound intermediates when allowed. */
	private static void stepOrCreate(NBTBase tag, Segment seg, boolean createMissing, List<NBTBase> out) {
		NBTBase cur = tag;
		if (seg.key != null) {
			if (!(cur instanceof NBTTagCompound)) {
				return;
			}
			NBTTagCompound compound = (NBTTagCompound) cur;
			if (!compound.hasKey(seg.key)) {
				if (!createMissing) {
					return;
				}
				NBTTagCompound created = new NBTTagCompound();
				compound.setTag(seg.key, created);
				cur = created;
			} else {
				cur = compound.getTag(seg.key);
			}
		}
		if (seg.index != null) {
			if (!(cur instanceof NBTTagList)) {
				return;
			}
			NBTTagList list = (NBTTagList) cur;
			if (seg.index.intValue() >= list.tagCount()) {
				return;
			}
			cur = list.get(seg.index.intValue());
			out.add(cur);
		} else if (seg.filterAll || seg.filter != null) {
			if (!(cur instanceof NBTTagList)) {
				return;
			}
			NBTTagList list = (NBTTagList) cur;
			for (int i = 0; i < list.tagCount(); ++i) {
				NBTBase item = list.get(i);
				if (item instanceof NBTTagCompound) {
					if (seg.filterAll || filterMatches((NBTTagCompound) item, seg.filter)) {
						out.add(item);
					}
				}
			}
		} else {
			out.add(cur);
		}
	}

	/** modify set: assigns {@code value} at every location; returns modified count. */
	public static int modifySet(NBTTagCompound root, String path, NBTBase value) throws CommandSyntaxException {
		int count = 0;
		for (Location loc : resolveLocations(root, path, true)) {
			if (loc.index != null) {
				((NBTTagList) loc.parent).set(loc.index.intValue(), value.copy());
			} else {
				((NBTTagCompound) loc.parent).setTag(loc.key, value.copy());
			}
			++count;
		}
		return count;
	}

	/** modify merge: deep-merges {@code value} into every matched compound location. */
	public static int modifyMerge(NBTTagCompound root, String path, final NBTTagCompound value)
			throws CommandSyntaxException {
		int count = 0;
		for (Location loc : resolveLocations(root, path, true)) {
			if (!(loc.parent instanceof NBTTagCompound)) {
				continue;
			}
			NBTTagCompound compound = (NBTTagCompound) loc.parent;
			if (loc.index != null) {
				continue; // merge targets compound keys only (vanilla)
			}
			NBTBase existing = compound.getTag(loc.key);
			if (existing instanceof NBTTagCompound) {
				deepMerge((NBTTagCompound) existing, value);
			} else {
				compound.setTag(loc.key, value.copy());
			}
			++count;
		}
		return count;
	}

	/**
	 * modify append/prepend/insert-before/insert-after. Missing key targets
	 * create a fresh list (vanilla semantics); existing non-list targets fail
	 * with the vanilla "Expected a list" message.
	 */
	public static int modifyInsert(NBTTagCompound root, String path, NBTBase value, InsertMode mode,
			Integer insertIndex) throws CommandSyntaxException {
		int count = 0;
		for (Location loc : resolveLocations(root, path, true)) {
			if (!(loc.parent instanceof NBTTagCompound)) {
				continue;
			}
			NBTTagCompound compound = (NBTTagCompound) loc.parent;
			NBTTagList list;
			if (compound.hasKey(loc.key)) {
				NBTBase existing = compound.getTag(loc.key);
				if (!(existing instanceof NBTTagList)) {
					throw new CommandSyntaxException("Expected a list, found: '" + render(existing) + "'");
				}
				list = (NBTTagList) existing;
			} else {
				list = new NBTTagList();
				compound.setTag(loc.key, list);
			}
			int at;
			switch (mode) {
				case APPEND:
					at = list.tagCount();
					break;
				case PREPEND:
					at = 0;
					break;
				case BEFORE:
					at = insertIndex.intValue();
					if (at < 0 || at > list.tagCount()) {
						throw new CommandSyntaxException(
								"Index " + at + " out of bounds for list of length " + list.tagCount());
					}
					break;
				case AFTER:
				default:
					at = insertIndex.intValue() + 1;
					if (at < 1 || at > list.tagCount()) {
						throw new CommandSyntaxException(
								"Index " + insertIndex + " out of bounds for list of length " + list.tagCount());
					}
					break;
			}
			// NBTTagList has no mid-insert in 1.8 — rebuild the tail (small lists
			// only; NBT payloads are engine-size, not unbounded).
			NBTTagList rebuilt = new NBTTagList();
			for (int i = 0; i < at; ++i) {
				rebuilt.appendTag(list.get(i).copy());
			}
			rebuilt.appendTag(value.copy());
			for (int i = at; i < list.tagCount(); ++i) {
				rebuilt.appendTag(list.get(i).copy());
			}
			compound.setTag(loc.key, rebuilt);
			++count;
		}
		return count;
	}

	/** remove: deletes every matched location (root path rejected by the parser). */
	public static int modifyRemove(NBTTagCompound root, String path) throws CommandSyntaxException {
		// locations must be removed highest-index-first per list to keep indices valid
		int count = 0;
		List<Segment> segments = parse(path);
		List<Location> locations = resolveLocations(root, path, false);
		// group by parent list and sort descending by index
		List<Location> listLocs = new ArrayList<Location>();
		for (Location loc : locations) {
			if (loc.index != null) {
				listLocs.add(loc);
			} else if (((NBTTagCompound) loc.parent).hasKey(loc.key)) {
				((NBTTagCompound) loc.parent).removeTag(loc.key);
				++count;
			}
		}
		if (!listLocs.isEmpty()) {
			java.util.Collections.sort(listLocs, new java.util.Comparator<Location>() {
				@Override
				public int compare(Location a, Location b) {
					return b.index.intValue() - a.index.intValue();
				}
			});
			NBTTagList lastList = null;
			int lastIndex = -1;
			int delta = 0;
			for (Location loc : listLocs) {
				NBTTagList list = (NBTTagList) loc.parent;
				if (list != lastList) {
					lastList = list;
					lastIndex = -1;
					delta = 0;
				}
				int effective = loc.index.intValue() + delta;
				if (effective < list.tagCount()) {
					list.removeTag(effective);
					++count;
					--delta;
				}
				++lastIndex;
			}
		}
		// unused-variable guard: segments kept for parse-side effect (validation)
		if (segments.isEmpty()) {
			throw new CommandSyntaxException("Cannot modify the root NBT path");
		}
		return count;
	}

	// ── Vanilla /data merge (root-level deep merge) ────────────────────────

	public static void deepMerge(NBTTagCompound into, NBTTagCompound from) {
		for (String key : from.getKeySet()) {
			NBTBase fromTag = from.getTag(key);
			if (fromTag instanceof NBTTagCompound && into.hasKey(key)
					&& into.getTag(key) instanceof NBTTagCompound) {
				deepMerge((NBTTagCompound) into.getTag(key), (NBTTagCompound) fromTag);
			} else {
				into.setTag(key, fromTag.copy());
			}
		}
	}

	// ── SNBT parsing / rendering (vanilla 1.20.1 output style) ─────────────

	/** Parses an SNBT compound via the engine's real parser (1.8 JsonToNBT). */
	public static NBTTagCompound parseCompound(String snbt) throws CommandSyntaxException {
		try {
			NBTTagCompound parsed = JsonToNBT.getTagFromJson(snbt);
			// [Agent Note 2026-08-29] 1.20.1 parity normalization: the 1.8 parser
			// maps a bare "[1,2,3]" to an int ARRAY, while vanilla 1.20.1 SNBT
			// treats it as an int LIST ("[I;…" is the explicit array form).
			// Convert bare int arrays to int lists so /data modify list ops and
			// /data get render exactly like 1.20.1 (only when no explicit
			// "[I;" marker exists in the input).
			if (!snbt.contains("[I;")) {
				normalizeIntLists(parsed);
			}
			return parsed;
		} catch (NBTException e) {
			throw new CommandSyntaxException("Could not parse NBT: " + String.valueOf(e.getMessage()));
		}
	}

	private static void normalizeIntLists(NBTBase tag) {
		if (tag instanceof NBTTagCompound) {
			NBTTagCompound compound = (NBTTagCompound) tag;
			// collect keys first — setTag during iteration would mutate the key set
			java.util.List<String> keys = new ArrayList<String>(compound.getKeySet());
			for (String key : keys) {
				NBTBase child = compound.getTag(key);
				if (child instanceof NBTTagIntArray) {
					int[] arr = ((NBTTagIntArray) child).getIntArray();
					NBTTagList list = new NBTTagList();
					for (int value : arr) {
						list.appendTag(new net.minecraft.nbt.NBTTagInt(value));
					}
					compound.setTag(key, list);
				} else {
					normalizeIntLists(child);
				}
			}
		} else if (tag instanceof NBTTagList) {
			NBTTagList list = (NBTTagList) tag;
			for (int i = 0; i < list.tagCount(); ++i) {
				normalizeIntLists(list.get(i));
			}
		}
	}

	/**
	 * Renders a tag in vanilla-1.20.1-style compact SNBT. The 1.8 NBT toString
	 * renders lists with index prefixes ({@code [0:1,1:2]}) while vanilla 1.20.1
	 * prints {@code [1,2]} — this renderer produces the 1.20.1 shape.
	 */
	public static String render(NBTBase tag) {
		StringBuilder sb = new StringBuilder();
		renderInto(tag, sb);
		return sb.toString();
	}

	private static void renderInto(NBTBase tag, StringBuilder sb) {
		if (tag instanceof NBTTagCompound) {
			NBTTagCompound compound = (NBTTagCompound) tag;
			sb.append('{');
			boolean first = true;
			for (String key : compound.getKeySet()) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				renderKey(key, sb);
				sb.append(':');
				renderInto(compound.getTag(key), sb);
			}
			sb.append('}');
		} else if (tag instanceof NBTTagList) {
			NBTTagList list = (NBTTagList) tag;
			sb.append('[');
			for (int i = 0; i < list.tagCount(); ++i) {
				if (i != 0) {
					sb.append(',');
				}
				renderInto(list.get(i), sb);
			}
			sb.append(']');
		} else if (tag instanceof NBTTagString) {
			renderKey(((NBTTagString) tag).getString(), sb);
		} else if (tag instanceof NBTTagByteArray) {
			byte[] arr = ((NBTTagByteArray) tag).getByteArray();
			sb.append("[B;");
			for (int i = 0; i < arr.length; ++i) {
				if (i != 0) {
					sb.append(',');
				}
				sb.append(arr[i]).append('B');
			}
			sb.append(']');
		} else if (tag instanceof NBTTagIntArray) {
			int[] arr = ((NBTTagIntArray) tag).getIntArray();
			sb.append("[I;");
			for (int i = 0; i < arr.length; ++i) {
				if (i != 0) {
					sb.append(',');
				}
				sb.append(arr[i]);
			}
			sb.append(']');
		} else {
			// numeric + rest: 1.8 NBTBase subclasses already render vanilla-style
			// suffixes (1b / 1s / 1 / 1L / 1.0f / 1.0d) via their toString.
			sb.append(String.valueOf(tag));
		}
	}

	private static void renderKey(String key, StringBuilder sb) {
		boolean needsQuote = key.isEmpty();
		for (int i = 0; i < key.length() && !needsQuote; ++i) {
			char c = key.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.' && c != '+') {
				needsQuote = true;
			}
		}
		if (!needsQuote) {
			sb.append(key);
			return;
		}
		sb.append('"');
		for (int i = 0; i < key.length(); ++i) {
			char c = key.charAt(i);
			if (c == '"' || c == '\\') {
				sb.append('\\');
			}
			sb.append(c);
		}
		sb.append('"');
	}

	// ── Filter matching (partial + recursive) ──────────────────────────────

	private static boolean filterMatches(NBTTagCompound candidate, NBTTagCompound filter) {
		for (String key : filter.getKeySet()) {
			if (!candidate.hasKey(key)) {
				return false;
			}
			NBTBase fv = filter.getTag(key);
			NBTBase cv = candidate.getTag(key);
			if (fv instanceof NBTTagCompound) {
				if (!(cv instanceof NBTTagCompound) || !filterMatches((NBTTagCompound) cv, (NBTTagCompound) fv)) {
					return false;
				}
			} else if (!cv.equals(fv)) {
				return false;
			}
		}
		return true;
	}
}
