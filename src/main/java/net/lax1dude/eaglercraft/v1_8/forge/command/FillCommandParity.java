package net.lax1dude.eaglercraft.v1_8.forge.command;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /fill} SYNTAX
 * translator — wired INTO the real 1.8 {@code CommandFill} (dual-syntax:
 * modern args translated to the legacy arg vector in-place, legacy form
 * untouched = regression anchor). Every map, every mod, zero hardcode.
 *
 * WHAT WAS BROKEN: 1.8 {@code CommandFill} expects
 * {@code fill <from> <to> <block> <meta> [mode] [nbt]}; modern maps write
 * {@code fill <from> <to> <block[props]{nbt}> [mode]} plus the replace-filter
 * form {@code fill ... replace <filter[props]>}. Bracketed tokens and the
 * modern arg order (mode right after the block, no meta) parse-fail.
 *
 * TRANSLATION (args-level):
 * <pre>
 *  fill ~ ~ ~ ~5 ~5 ~5 minecraft:repeating_command_block{Command:"say hi"}
 *    → [~ ~ ~ ~5 ~5 ~5 | minecraft:command_block | 0 | replace |
 *       {Mode:"repeating",auto:1b,Command:"say hi"}]
 *  fill 0 0 0 5 5 5 air keep        → [0 0 0 5 5 5 | air | 0 | keep]
 *  fill ... minecraft:planks replace minecraft:stonebrick[...] →
 *    [... | minecraft:planks | 0 | replace | minecraft:stonebrick | 0]
 * </pre>
 * Mode vocabulary (destroy|hollow|keep|outline|replace) is identical in
 * 1.8 and 1.20.1 and passes through. Filter props beyond id+meta drop
 * (1.8 filter matches block+meta only — honest §19.8 boundary).
 *
 * Doc-ID: MCBP-FILL-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class FillCommandParity {

	private FillCommandParity() {
	}

	/**
	 * Translate modern {@code /fill} args into the legacy 1.8 arg vector,
	 * or {@code null} when already 1.8 syntax (caller keeps them — anchor).
	 * Legacy 1.8 fill order: [x1 y1 z1 x2 y2 z2 block meta [mode [filter
	 * [filterMeta [nbt]]]]].
	 */
	public static String[] translate(String[] args) {
		if (args == null || args.length < 7) {
			return null;
		}
		String blockToken = args[6];
		boolean tokenModern = SetBlockCommandParity.hasModernDecorations(blockToken)
				|| CommandBlockModernRuntime.modernCommandBlockMode(SetBlockCommandParity.stripDecorations(blockToken)) != null;
		boolean modernOrder = args.length >= 8 && SetBlockCommandParity.isModeWord(args[7]);
		if (!tokenModern && !modernOrder) {
			return null; // plain 1.8 (numeric meta / nothing) — legacy path
		}

		String idPart = SetBlockCommandParity.stripDecorations(blockToken);
		if (idPart.isEmpty()) {
			throw new IllegalArgumentException("Expected a block id in: " + blockToken);
		}
		String cbMode = CommandBlockModernRuntime.modernCommandBlockMode(idPart);

		int meta = 0;
		String extraKeys = "";
		String realId;
		if (cbMode != null) {
			realId = CommandBlockModernRuntime.REAL_COMMAND_BLOCK_ID;
			StringBuilder keys = new StringBuilder("Mode:\"").append(cbMode).append('"');
			if (CommandBlockModernRuntime.defaultAutoForMode(cbMode)) {
				keys.append(",auto:1b");
			}
			extraKeys = SetBlockCommandParity.foldCommandBlockProps(keys,
					SetBlockCommandParity.extractProps(blockToken)).toString();
		} else {
			realId = idPart;
			meta = SetBlockCommandParity.resolveMetaPublic(blockToken);
		}
		String mergedNbt = SetBlockCommandParity.mergeNbt(extraKeys,
				SetBlockCommandParity.extractNbt(blockToken));

		// mode word (modern: [7] directly; 1.8-ish modern token: [8])
		String mode = "replace";
		for (int i = 7; i < args.length; ++i) {
			if (SetBlockCommandParity.isModeWord(args[i])) {
				mode = args[i];
				break;
			}
		}
		// keep a 1.8 numeric meta if the modern token path met one
		if (args.length >= 8 && SetBlockCommandParity.isNumeric(args[7])) {
			try {
				meta = Integer.parseInt(args[7]);
			} catch (NumberFormatException ignored) {
				// unreachable — isNumeric already checked
			}
			if (args.length >= 9 && SetBlockCommandParity.isModeWord(args[8])) {
				mode = args[8];
			}
		}

		// modern replace-filter: fill ... replace <filter[props]>
		String filterId = null;
		int filterMeta = 0;
		if ("replace".equals(mode)) {
			for (int i = 8; i < args.length; ++i) {
				String a = args[i];
				if (!SetBlockCommandParity.isModeWord(a) && !SetBlockCommandParity.isNumeric(a)
						&& a.indexOf('{') < 0) {
					String fid = SetBlockCommandParity.stripDecorations(a);
					if (CommandBlockModernRuntime.modernCommandBlockMode(fid) != null) {
						filterId = CommandBlockModernRuntime.REAL_COMMAND_BLOCK_ID;
						filterMeta = 0;
					} else {
						filterId = fid;
						filterMeta = SetBlockCommandParity.resolveMetaPublic(a);
					}
					break;
				}
			}
		}

		// legacy 1.8 fill arg shape: [x1 y1 z1 x2 y2 z2 block meta mode ...] = 9 base args
		int n = 9 + (filterId != null ? 2 : 0) + (mergedNbt.isEmpty() ? 0 : 1);
		String[] out = new String[n];
		for (int i = 0; i < 6; ++i) {
			out[i] = args[i];
		}
		out[6] = realId;
		out[7] = String.valueOf(meta);
		out[8] = mode;
		int k = 9;
		if (filterId != null) {
			out[k++] = filterId;
			out[k++] = String.valueOf(filterMeta);
		}
		if (k < n) {
			out[k] = mergedNbt;
		}
		return out;
	}
}
