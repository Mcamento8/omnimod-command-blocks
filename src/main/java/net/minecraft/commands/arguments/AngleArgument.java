package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 AngleArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.AngleArgument} — a single
 * angle value supporting ~ relative offset (used by /rotate-style commands).
 * Returns the raw double; relative resolution is lazy against the caller.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-ANGLE-001
 */
public class AngleArgument implements ArgumentType<AngleArgument.Angle> {

	/** A parsed angle: absolute or relative (~). */
	public static final class Angle {
		public final double value;
		public final boolean relative;

		public Angle(double value, boolean relative) {
			this.value = value;
			this.relative = relative;
		}

		public double resolve(double base) {
			return relative ? base + value : value;
		}
	}

	private AngleArgument() {
	}

	public static AngleArgument angle() {
		return new AngleArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to an angle double. */
	public static double getAngle(CommandContext<?> ctx, String name) {
		Angle a = ctx.getArgument(name, Angle.class);
		return a != null ? a.value : 0.0D;
	}

	@Override
	public Angle parse(StringReader reader) throws CommandSyntaxException {
		if (!reader.canRead()) {
			throw new CommandSyntaxException("Expected angle at position " + reader.getCursor());
		}
		boolean relative = reader.peek() == '~';
		if (relative) {
			reader.skip();
		}
		int start = reader.getCursor();
		while (reader.canRead() && (Character.isDigit(reader.peek()) || reader.peek() == '-'
				|| reader.peek() == '+' || reader.peek() == '.')) {
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		if (s.isEmpty()) {
			return new Angle(0.0D, relative);
		}
		try {
			return new Angle(Double.parseDouble(s), relative);
		} catch (NumberFormatException e) {
			throw new CommandSyntaxException("Invalid angle '" + s + "'");
		}
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		if (remaining.isEmpty()) {
			return Collections.singletonList("~");
		}
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "angle()";
	}
}
