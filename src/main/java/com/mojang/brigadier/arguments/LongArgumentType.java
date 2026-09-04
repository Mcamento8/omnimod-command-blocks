package com.mojang.brigadier.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier LongArgumentType shim.
 *
 * Mirrors {@code com.mojang.brigadier.arguments.LongArgumentType} from Forge
 * 1.20.1 (longArg()/longArg(min)/longArg(min,max) + range enforcement).
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-ARG-LONG-001
 */
public class LongArgumentType implements ArgumentType<Long> {
	private final long minimum;
	private final long maximum;

	private LongArgumentType(long minimum, long maximum) {
		this.minimum = minimum;
		this.maximum = maximum;
	}

	public static LongArgumentType longArg() {
		return new LongArgumentType(Long.MIN_VALUE, Long.MAX_VALUE);
	}

	public static LongArgumentType longArg(long min) {
		return new LongArgumentType(min, Long.MAX_VALUE);
	}

	public static LongArgumentType longArg(long min, long max) {
		return new LongArgumentType(min, max);
	}

	public static long getLong(com.mojang.brigadier.context.CommandContext<?> ctx, String name) {
		Long v = ctx.getArgument(name, Long.class);
		return v != null ? v.longValue() : 0L;
	}

	@Override
	public Long parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		long value;
		try {
			value = Long.parseLong(reader.readInt() + "");
		} catch (NumberFormatException e) {
			throw new CommandSyntaxException("Invalid long at position " + start);
		}
		if (value < minimum || value > maximum) {
			reader.setCursor(start);
			throw new CommandSyntaxException(
					"Long must be between " + minimum + " and " + maximum + ", found " + value);
		}
		return Long.valueOf(value);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "long(" + minimum + ", " + maximum + ")";
	}
}
