package com.mojang.brigadier.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier IntegerArgumentType shim.
 *
 * Mirrors {@code com.mojang.brigadier.arguments.IntegerArgumentType} from
 * Forge 1.20.1: created via {@code IntegerArgumentType.integer()} /
 * {@code integer(min)} / {@code integer(min, max)}; parses an int token and
 * enforces the declared range (Brigadier throws a syntax exception outside
 * the range — the bridge surfaces it as a red chat line, never a silent
 * failure).
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-ARG-INT-001
 */
public class IntegerArgumentType implements ArgumentType<Integer> {
	private final int minimum;
	private final int maximum;

	private IntegerArgumentType(int minimum, int maximum) {
		this.minimum = minimum;
		this.maximum = maximum;
	}

	public static IntegerArgumentType integer() {
		return new IntegerArgumentType(Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	public static IntegerArgumentType integer(int min) {
		return new IntegerArgumentType(min, Integer.MAX_VALUE);
	}

	public static IntegerArgumentType integer(int min, int max) {
		return new IntegerArgumentType(min, max);
	}

	public static int getInteger(com.mojang.brigadier.context.CommandContext<?> ctx, String name) {
		Integer v = ctx.getArgument(name, Integer.class);
		return v != null ? v.intValue() : 0;
	}

	public int getMinimum() {
		return minimum;
	}

	public int getMaximum() {
		return maximum;
	}

	@Override
	public Integer parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		int value = reader.readInt();
		if (value < minimum || value > maximum) {
			reader.setCursor(start);
			throw new CommandSyntaxException(
					"Integer must be between " + minimum + " and " + maximum + ", found " + value);
		}
		return Integer.valueOf(value);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		// Numeric args are typed by the user; offer the range bounds as hints.
		if (minimum != Integer.MIN_VALUE && maximum != Integer.MAX_VALUE) {
			return Collections.singletonList(minimum + ".." + maximum);
		}
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "integer(" + minimum + ", " + maximum + ")";
	}
}
