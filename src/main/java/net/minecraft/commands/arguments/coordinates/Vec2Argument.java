package net.minecraft.commands.arguments.coordinates;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.world.phys.Vec2;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 Vec2Argument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.coordinates.Vec2Argument} —
 * used by /execute rotated, /worldborder center... Parses a 2-axis
 * yaw/pitch pair supporting ~ relative offsets against the source rotation.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-VEC2-001
 */
public class Vec2Argument implements ArgumentType<Vec2Argument.InputCoordinates> {

	/** A parsed 2-axis value: raw numbers + rel flags (resolution is lazy). */
	public static final class InputCoordinates {
		public final double x;
		public final double y;
		public final boolean relX;
		public final boolean relY;

		public InputCoordinates(double x, double y, boolean relX, boolean relY) {
			this.x = x;
			this.y = y;
			this.relX = relX;
			this.relY = relY;
		}

		/** Resolve against a source rotation (yaw, pitch). */
		public Vec2 toRotation(float baseYaw, float basePitch) {
			return new Vec2((float) ((relX ? baseYaw : 0.0D) + x),
					(float) ((relY ? basePitch : 0.0D) + y));
		}
	}

	private final boolean centerIntegers;

	private Vec2Argument(boolean centerIntegers) {
		this.centerIntegers = centerIntegers;
	}

	public static Vec2Argument vec2() {
		return new Vec2Argument(false);
	}

	public static Vec2Argument vec2(boolean centerIntegers) {
		return new Vec2Argument(centerIntegers);
	}

	@Override
	public InputCoordinates parse(StringReader reader) throws CommandSyntaxException {
		if (!reader.canRead()) {
			throw new CommandSyntaxException("Expected coordinate at position " + reader.getCursor());
		}
		boolean sawLocal = false;
		double[] vals = new double[2];
		boolean[] rels = new boolean[2];
		for (int axis = 0; axis < 2; ++axis) {
			if (axis > 0) {
				reader.skipWhitespace();
				if (!reader.canRead()) {
					throw new CommandSyntaxException("Expected 2 coordinates, found " + axis);
				}
			}
			char c = reader.peek();
			if (c == '^') {
				sawLocal = true;
				reader.skip();
				vals[axis] = readOffset(reader);
				rels[axis] = true;
			} else if (c == '~') {
				reader.skip();
				rels[axis] = true;
				vals[axis] = readOffset(reader);
			} else {
				if (sawLocal) {
					throw new CommandSyntaxException(
							"Cannot mix world and local coordinates (must all be ^ or none)");
				}
				vals[axis] = readAbsolute(reader, centerIntegers);
				rels[axis] = false;
			}
		}
		return new InputCoordinates(vals[0], vals[1], rels[0], rels[1]);
	}

	private static double readOffset(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		while (reader.canRead() && (Character.isDigit(reader.peek()) || reader.peek() == '-'
				|| reader.peek() == '+' || reader.peek() == '.')) {
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		if (s.isEmpty()) {
			return 0.0D;
		}
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new CommandSyntaxException("Invalid coordinate offset '" + s + "'");
		}
	}

	private static double readAbsolute(StringReader reader, boolean centerIntegers) throws CommandSyntaxException {
		int start = reader.getCursor();
		boolean sawDecimal = false;
		while (reader.canRead() && (Character.isDigit(reader.peek()) || reader.peek() == '-'
				|| reader.peek() == '+' || reader.peek() == '.')) {
			if (reader.peek() == '.') {
				sawDecimal = true;
			}
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		if (s.isEmpty() || s.equals("-") || s.equals("+")) {
			throw new CommandSyntaxException("Expected coordinate at position " + start);
		}
		double v;
		try {
			v = Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new CommandSyntaxException("Invalid coordinate '" + s + "'");
		}
		if (centerIntegers && !sawDecimal) {
			v += 0.5D;
		}
		return v;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		if (remaining.isEmpty() || remaining.equals("~")) {
			return Collections.singletonList("~ ~");
		}
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "vec2()";
	}
}
