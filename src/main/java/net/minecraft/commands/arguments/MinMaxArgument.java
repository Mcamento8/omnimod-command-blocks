package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 MinMaxArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.RangeArgument} (the class is
 * historically called MinMaxArgument) with its vanilla nested subclasses:
 *   {@code MinMaxArgument.Ints}   — integer range (used by /execute if score matches)
 *   {@code MinMaxArgument.Floats} — decimal range
 * Syntax (vanilla): "5" | "5.." | "..10" | "5..10". At least one bound required.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-MINMAX-001
 */
public class MinMaxArgument {

	private MinMaxArgument() {
	}

	/** A parsed double-ended range (bounds are inclusive; null = unbounded). */
	public static final class DoubleRange {
		public final Double min;
		public final Double max;

		public DoubleRange(Double min, Double max) {
			this.min = min;
			this.max = max;
		}

		public boolean matches(double value) {
			if (min != null && value < min.doubleValue()) {
				return false;
			}
			if (max != null && value > max.doubleValue()) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			return (min == null ? "" : String.valueOf(min)) + ".." + (max == null ? "" : String.valueOf(max));
		}
	}

	/** Vanilla nested class: integer range argument. */
	public static final class Ints implements ArgumentType<DoubleRange> {
		private Ints() {
		}

		public static Ints intRange() {
			return new Ints();
		}

		@Override
		public DoubleRange parse(StringReader reader) throws CommandSyntaxException {
			return parseRange(reader, false);
		}
	}

	/** Vanilla nested class: float range argument. */
	public static final class Floats implements ArgumentType<DoubleRange> {
		private Floats() {
		}

		public static Floats floatRange() {
			return new Floats();
		}

		@Override
		public DoubleRange parse(StringReader reader) throws CommandSyntaxException {
			return parseRange(reader, true);
		}
	}

	private static DoubleRange parseRange(StringReader reader, boolean allowDecimal)
			throws CommandSyntaxException {
		reader.skipWhitespace();
		Double min = null;
		Double max = null;
		int start = reader.getCursor();
		// Lower bound (optional)
		if (reader.canRead() && reader.peek() != '.') {
			min = Double.valueOf(readBound(reader, allowDecimal));
		}
		if (reader.canRead() && reader.peek() == '.') {
			// Expect ".."
			if (reader.canRead(1) && reader.peek(1) == '.') {
				reader.skip();
				reader.skip();
			} else {
				throw new CommandSyntaxException("Expected '..' at position " + reader.getCursor());
			}
			// Upper bound (optional)
			reader.skipWhitespace();
			if (reader.canRead() && reader.peek() != ' ') {
				max = Double.valueOf(readBound(reader, allowDecimal));
			}
		} else if (min != null) {
			// Single value "5" == exact range 5..5
			max = min;
		} else {
			throw new CommandSyntaxException("Expected a range at position " + start);
		}
		if (min == null && max == null) {
			throw new CommandSyntaxException("Expected a range at position " + start);
		}
		return new DoubleRange(min, max);
	}

	private static double readBound(StringReader reader, boolean allowDecimal)
			throws CommandSyntaxException {
		int start = reader.getCursor();
		while (reader.canRead() && (Character.isDigit(reader.peek()) || reader.peek() == '-'
				|| reader.peek() == '+' || (allowDecimal && reader.peek() == '.'))) {
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		if (s.isEmpty()) {
			throw new CommandSyntaxException("Expected a number at position " + start);
		}
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new CommandSyntaxException("Invalid number '" + s + "' at position " + start);
		}
	}

	/** Convenience: list suggestions shared by both nested types. */
	static List<String> rangeSuggestions(String remaining) {
		if (remaining.isEmpty()) {
			return Collections.singletonList("..");
		}
		return Collections.emptyList();
	}
}
