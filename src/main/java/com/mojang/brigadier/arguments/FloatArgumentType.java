package com.mojang.brigadier.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier FloatArgumentType shim.
 *
 * Mirrors {@code com.mojang.brigadier.arguments.FloatArgumentType} from Forge
 * 1.20.1 (float()/float(min)/float(min,max) + range enforcement).
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-ARG-FLOAT-001
 */
public class FloatArgumentType implements ArgumentType<Float> {
	private final float minimum;
	private final float maximum;

	private FloatArgumentType(float minimum, float maximum) {
		this.minimum = minimum;
		this.maximum = maximum;
	}

	public static FloatArgumentType floatArg() {
		return new FloatArgumentType(-Float.MAX_VALUE, Float.MAX_VALUE);
	}

	public static FloatArgumentType floatArg(float min) {
		return new FloatArgumentType(min, Float.MAX_VALUE);
	}

	public static FloatArgumentType floatArg(float min, float max) {
		return new FloatArgumentType(min, max);
	}

	public static float getFloat(com.mojang.brigadier.context.CommandContext<?> ctx, String name) {
		Float v = ctx.getArgument(name, Float.class);
		return v != null ? v.floatValue() : 0.0F;
	}

	@Override
	public Float parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		float value = reader.readFloat();
		if (value < minimum || value > maximum) {
			reader.setCursor(start);
			throw new CommandSyntaxException(
					"Float must be between " + minimum + " and " + maximum + ", found " + value);
		}
		return Float.valueOf(value);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "float(" + minimum + ", " + maximum + ")";
	}
}
