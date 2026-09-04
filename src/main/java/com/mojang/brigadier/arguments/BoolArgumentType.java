package com.mojang.brigadier.arguments;

import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier BoolArgumentType shim.
 *
 * Mirrors {@code com.mojang.brigadier.arguments.BoolArgumentType} from Forge
 * 1.20.1: parses true/false. Suggestions offer both literals (real Brigadier
 * behavior).
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-ARG-BOOL-001
 */
public class BoolArgumentType implements ArgumentType<Boolean> {
	private static final List<String> SUGGESTIONS = Arrays.asList("true", "false");

	private BoolArgumentType() {
	}

	public static BoolArgumentType bool() {
		return new BoolArgumentType();
	}

	public static boolean getBool(com.mojang.brigadier.context.CommandContext<?> ctx, String name) {
		Boolean v = ctx.getArgument(name, Boolean.class);
		return v != null && v.booleanValue();
	}

	@Override
	public Boolean parse(StringReader reader) throws CommandSyntaxException {
		return Boolean.valueOf(reader.readBoolean());
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return SUGGESTIONS;
	}

	@Override
	public String toString() {
		return "bool()";
	}
}
