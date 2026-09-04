package com.mojang.brigadier.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier ArgumentType interface shim.
 *
 * Mirrors {@code com.mojang.brigadier.arguments.ArgumentType<T>} from Forge
 * 1.20.1. Argument nodes in a mod command tree hold an ArgumentType that
 * parses raw input tokens into typed values placed on the
 * {@code CommandContext} (readable by the mod lambda via
 * {@code ctx.getArgument(name, clazz)}).
 *
 * WHY THIS EXISTS: mods reference concrete argument types
 * (IntegerArgumentType.integer(), StringArgumentType.word(),
 * EntityArgument.entities(), BlockPosArgument.blockPos()...) in
 * {@code Commands.argument("name", <type>)}. Without these shims a mod
 * command class cannot even be classloaded (NoClassDefFoundError) and the
 * bridge cannot parse the mod's declared arguments.
 *
 * TIP COMPLETION: {@link #listSuggestions} receives the remaining word so the
 * bridge can offer per-type completions (numeric ranges, boolean literals,
 * player names via the concrete types).
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-ARGTYPE-001
 */
public interface ArgumentType<T> {

	/** Parse the next value from the reader (real Brigadier signature). */
	T parse(StringReader reader) throws CommandSyntaxException;

	/**
	 * Suggest completions for the current (unfinished) word. Default: no
	 * suggestions. Concrete types override this (booleans, selectors, etc.).
	 */
	default java.util.List<String> listSuggestions(String remaining) {
		return java.util.Collections.emptyList();
	}
}
