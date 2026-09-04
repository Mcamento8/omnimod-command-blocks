package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 TimeArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.TimeArgument} — used by
 * {@code /time add|set} and {@code /schedule}. Parses an integer game time
 * with an optional vanilla unit suffix (REAL 1.20.1 semantics):
 *   none/t -> ticks (1)
 *   s      -> seconds (x20)
 *   d      -> in-game days (x24000)
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-TIME-001
 */
public class TimeArgument implements ArgumentType<Integer> {

	private TimeArgument() {
	}

	public static TimeArgument time() {
		return new TimeArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to tick count. */
	public static int getTime(CommandContext<?> ctx, String name) {
		Integer v = ctx.getArgument(name, Integer.class);
		return v != null ? v.intValue() : 0;
	}

	@Override
	public Integer parse(StringReader reader) throws CommandSyntaxException {
		int ticks = reader.readInt();
		// Optional unit suffix directly attached to the number ("100s", "2d").
		if (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			char unit = reader.peek();
			if (unit == 't') {
				reader.skip();
			} else if (unit == 's') {
				reader.skip();
				ticks = Math.multiplyExact(ticks, 20);
			} else if (unit == 'd') {
				reader.skip();
				ticks = Math.multiplyExact(ticks, 24000);
			}
		}
		return Integer.valueOf(ticks);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "time()";
	}
}
