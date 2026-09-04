package com.mojang.brigadier.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier DoubleArgumentType shim.
 *
 * Mirrors {@code com.mojang.brigadier.arguments.DoubleArgumentType} from Forge
 * 1.20.1 (doubleArg()/doubleArg(min)/doubleArg(min,max) + range enforcement).
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-ARG-DOUBLE-001
 */
public class DoubleArgumentType implements ArgumentType<Double> {
	private final double minimum;
	private final double maximum;

	private DoubleArgumentType(double minimum, double maximum) {
		this.minimum = minimum;
		this.maximum = maximum;
	}

	public static DoubleArgumentType doubleArg() {
		return new DoubleArgumentType(-Double.MAX_VALUE, Double.MAX_VALUE);
	}

	public static DoubleArgumentType doubleArg(double min) {
		return new DoubleArgumentType(min, Double.MAX_VALUE);
	}

	public static DoubleArgumentType doubleArg(double min, double max) {
		return new DoubleArgumentType(min, max);
	}

	public static double getDouble(com.mojang.brigadier.context.CommandContext<?> ctx, String name) {
		Double v = ctx.getArgument(name, Double.class);
		return v != null ? v.doubleValue() : 0.0D;
	}

	@Override
	public Double parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		double value = reader.readDouble();
		if (value < minimum || value > maximum) {
			reader.setCursor(start);
			throw new CommandSyntaxException(
					"Double must be between " + minimum + " and " + maximum + ", found " + value);
		}
		return Double.valueOf(value);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "double(" + minimum + ", " + maximum + ")";
	}
}
