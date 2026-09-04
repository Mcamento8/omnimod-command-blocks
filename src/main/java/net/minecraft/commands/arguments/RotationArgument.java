package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 RotationArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.RotationArgument} — used by
 * {@code /execute rotated <yaw> <pitch>}. Parses a 2-axis rotation where each
 * axis is either absolute or ~ relative to the source rotation; resolution is
 * lazy (needs a source).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-ROTATION-001
 */
public class RotationArgument implements ArgumentType<RotationArgument.RotationInput> {

	/** A parsed rotation (yaw/pitch, absolute or ~ relative). */
	public static final class RotationInput {
		public final double yaw;
		public final double pitch;
		public final boolean relYaw;
		public final boolean relPitch;

		public RotationInput(double yaw, double pitch, boolean relYaw, boolean relPitch) {
			this.yaw = yaw;
			this.pitch = pitch;
			this.relYaw = relYaw;
			this.relPitch = relPitch;
		}

		/** Resolve against the source's current rotation (vanilla getRotation). */
		public Vec2 getRotation(CommandSourceStack source) {
			Vec2 base = source != null ? source.getRotation() : Vec2.ZERO;
			return new Vec2((float) ((relYaw ? base.x : 0.0D) + yaw),
					(float) ((relPitch ? base.y : 0.0D) + pitch));
		}
	}

	private RotationArgument() {
	}

	public static RotationArgument rotation() {
		return new RotationArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to a rotation. */
	public static Vec2 getRotation(CommandContext<?> ctx, String name) {
		RotationInput r = ctx.getArgument(name, RotationInput.class);
		Object src = ctx.getSource();
		CommandSourceStack css = src instanceof CommandSourceStack ? (CommandSourceStack) src : null;
		return r != null ? r.getRotation(css) : Vec2.ZERO;
	}

	@Override
	public RotationInput parse(StringReader reader) throws CommandSyntaxException {
		if (!reader.canRead()) {
			throw new CommandSyntaxException("Expected rotation at position " + reader.getCursor());
		}
		boolean relYaw = reader.peek() == '~';
		if (relYaw) {
			reader.skip();
		}
		double yaw = readNumber(reader);
		reader.skipWhitespace();
		if (!reader.canRead()) {
			throw new CommandSyntaxException("Expected 2 rotations, found 1");
		}
		boolean relPitch = reader.peek() == '~';
		if (relPitch) {
			reader.skip();
		}
		double pitch = readNumber(reader);
		return new RotationInput(yaw, pitch, relYaw, relPitch);
	}

	private static double readNumber(StringReader reader) throws CommandSyntaxException {
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
			throw new CommandSyntaxException("Invalid rotation value '" + s + "'");
		}
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
		return "rotation()";
	}
}
