package net.lax1dude.eaglercraft.v1_8.forge.command;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /clone} SYNTAX
 * translator — wired INTO the real 1.8 {@code CommandClone} (dual-syntax;
 * legacy form untouched = regression anchor). Every map, zero hardcode.
 *
 * WHAT WAS BROKEN: modern clone puts the FILTERED-mode filter BEFORE the
 * clone mode:
 * <pre>  clone <9 coords> filtered <filter> [normal|force|move]   (1.20.1)</pre>
 * while 1.8 expects:
 * <pre>  clone <9 coords> filtered [force|move] <filter> <meta>   (1.8.8)</pre>
 * A modern clone-with-filter parse-fails (1.8 reads the filter id as the
 * clone mode). Non-filtered forms (replace/masked + force/move/normal)
 * share the same order and already work — untouched.
 *
 * Cloned command blocks carry Mode/Facing/Conditional/auto NBT with the
 * tile (1.8 clone copies tile NBT), so modern command-block structures
 * clone correctly through the real engine path.
 *
 * Doc-ID: MCBP-CLONE-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class CloneCommandParity {

	private CloneCommandParity() {
	}

	/**
	 * Translate modern {@code /clone} args into the legacy 1.8 arg vector,
	 * or {@code null} when already 1.8 syntax. Legacy order:
	 * [9 coords | maskMode | cloneMode | filterBlock | filterMeta].
	 */
	public static String[] translate(String[] args) {
		if (args == null || args.length < 11 || !"filtered".equals(args[9])) {
			return null; // non-filtered forms are already 1.8-compatible
		}
		String arg10 = args[10];
		if ("normal".equals(arg10) || "force".equals(arg10) || "move".equals(arg10)) {
			return null; // 1.8 form (filter after clone mode) — untouched
		}
		// Modern form: filter at [10], optional clone mode at [11].
		String filterId = SetBlockCommandParity.stripDecorations(arg10);
		if (filterId.isEmpty()) {
			throw new IllegalArgumentException("Expected a filter block for clone filtered: " + arg10);
		}
		String cloneMode = "normal";
		if (args.length >= 12) {
			String m = args[11];
			if ("normal".equals(m) || "force".equals(m) || "move".equals(m)) {
				cloneMode = m;
			}
		}
		int filterMeta;
		if (CommandBlockModernRuntime.modernCommandBlockMode(filterId) != null) {
			filterId = CommandBlockModernRuntime.REAL_COMMAND_BLOCK_ID;
			filterMeta = 0;
		} else {
			filterMeta = SetBlockCommandParity.resolveMetaPublic(arg10);
			// filter props beyond id+meta are dropped — 1.8 matches block+meta
			// only (honest §19.8 boundary, same as the 1.8 clone filter)
		}
		String[] out = new String[13];
		for (int i = 0; i < 9; ++i) {
			out[i] = args[i];
		}
		out[9] = "filtered";
		out[10] = cloneMode;
		out[11] = filterId;
		out[12] = String.valueOf(filterMeta);
		return out;
	}
}
