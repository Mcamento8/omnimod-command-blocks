package com.mojang.brigadier.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier StringArgumentType shim.
 *
 * Mirrors {@code com.mojang.brigadier.arguments.StringArgumentType} from
 * Forge 1.20.1 with the three REAL word modes:
 *   word()          -> a single unquoted token
 *   string()        -> quoted or unquoted single value (greedy-safe via reader)
 *   greedyString()  -> consumes the REST of the input (used by /say, /msg...)
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-ARG-STR-001
 */
public class StringArgumentType implements ArgumentType<String> {

	public enum StringType {
		SINGLE_WORD,
		QUOTABLE_PHRASE,
		GREEDY_PHRASE
	}

	private final StringType type;

	private StringArgumentType(StringType type) {
		this.type = type;
	}

	public static StringArgumentType word() {
		return new StringArgumentType(StringType.SINGLE_WORD);
	}

	public static StringArgumentType string() {
		return new StringArgumentType(StringType.QUOTABLE_PHRASE);
	}

	public static StringArgumentType greedyString() {
		return new StringArgumentType(StringType.GREEDY_PHRASE);
	}

	public static String getString(com.mojang.brigadier.context.CommandContext<?> ctx, String name) {
		String v = ctx.getArgument(name, String.class);
		return v != null ? v : "";
	}

	public StringType getType() {
		return type;
	}

	@Override
	public String parse(StringReader reader) throws CommandSyntaxException {
		if (type == StringType.GREEDY_PHRASE) {
			return reader.readRemaining();
		}
		if (type == StringType.SINGLE_WORD) {
			reader.skipWhitespace();
			int start = reader.getCursor();
			while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
				reader.skip();
			}
			return reader.getString().substring(start, reader.getCursor());
		}
		return reader.readString();
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "string(" + type + ")";
	}
}
