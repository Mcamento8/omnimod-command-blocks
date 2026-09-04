package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.HashMap;
import java.util.Map;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /effect
 * give|clear} SYNTAX translator — wired INTO the real 1.8
 * {@code CommandEffect} (dual-syntax; legacy form untouched = regression
 * anchor). Every map, every mod, zero hardcode.
 *
 * WHAT WAS BROKEN: 1.8 {@code /effect <player> <effect> ...} has no
 * subcommand form. Every 1.13+ map writes
 * {@code /effect give @p minecraft:speed 30 1 true} /
 * {@code /effect clear @p [effect]} — the subcommand word at arg 1
 * parse-fails on the 1.8 path (it is not a player selector).
 *
 * TRANSLATION (args-level, real engine still validates everything):
 * <pre>
 *  effect give @p minecraft:speed 30 1 true  → [@p speed 30 1 true]
 *  effect give @p speed infinite             → [@p speed 1000000]
 *  effect clear @p                           → [@p clear]
 *  effect clear @p minecraft:speed           → [@p speed 0]
 * </pre>
 * Names strip {@code minecraft:}; the 1.9-era renames are un-renamed to
 * their 1.8 registry keys (health→instant_health, damage→instant_damage,
 * jump→jump_boost, confusion→nausea) so the REAL Potion lookup succeeds.
 * Modern {@code infinite} maps to the 1.8 million-second cap (documented
 * approximation). 1.9+-only effects (levitation, glowing, …) fail through
 * the real handler's own unknown-effect error — honest boundary.
 *
 * Doc-ID: MCBP-EFFECT-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class EffectCommandParity {

	/** Modern (1.9+) name → real 1.8 registry name. */
	private static final Map<String, String> NAME_UNRENAME = buildRenames();

	private static Map<String, String> buildRenames() {
		Map<String, String> m = new HashMap<String, String>();
		m.put("instant_health", "health");
		m.put("instant_damage", "damage");
		m.put("jump_boost", "jump");
		m.put("nausea", "confusion");
		return m;
	}

	private EffectCommandParity() {
	}

	/**
	 * Translate modern {@code /effect give|clear} args into the legacy 1.8
	 * arg vector ({@code effect <targets> <effect|clear> ...}), or
	 * {@code null} when already 1.8 syntax (regression anchor).
	 */
	public static String[] translate(String[] args) {
		if (args == null || args.length == 0) {
			return null;
		}
		String sub = args[0].toLowerCase();
		if ("give".equals(sub)) {
			if (args.length < 3) {
				throw new IllegalArgumentException("Expected: effect give <targets> <effect> [seconds] ...");
			}
			String effect = unrename(stripNamespace(args[2]));
			int n = 2 + (args.length - 3);
			String[] out = new String[n];
			out[0] = args[1]; // targets
			out[1] = effect;
			for (int i = 3; i < args.length; ++i) {
				String a = args[i];
				if (i == 3 && "infinite".equalsIgnoreCase(a)) {
					a = "1000000"; // 1.8 cap ≈ vanilla infinite (documented)
				}
				out[i - 1] = a;
			}
			return out;
		}
		if ("clear".equals(sub)) {
			if (args.length < 2) {
				throw new IllegalArgumentException("Expected: effect clear <targets> [effect]");
			}
			if (args.length >= 3) {
				// 1.8 removes a single effect with duration 0
				return new String[] { args[1], unrename(stripNamespace(args[2])), "0" };
			}
			return new String[] { args[1], "clear" };
		}
		return null; // 1.8 legacy form — untouched
	}

	static String stripNamespace(String name) {
		if (name == null) {
			return "";
		}
		int colon = name.indexOf(':');
		return colon >= 0 ? name.substring(colon + 1) : name;
	}

	static String unrename(String modernName) {
		String legacy = NAME_UNRENAME.get(modernName);
		return legacy != null ? legacy : modernName;
	}
}
