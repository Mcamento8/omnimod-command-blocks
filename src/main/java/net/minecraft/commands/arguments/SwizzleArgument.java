package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 SwizzleArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.SwizzleArgument} — used by
 * {@code /execute align <swizzle>}. Parses a NON-EMPTY subset of "xyz" with
 * NO repeated letters, in any order (vanilla accepts "yxz"). The stored int[]
 * lists the axes in the order typed.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-SWIZZLE-001
 */
public class SwizzleArgument implements ArgumentType<int[]> {

	private SwizzleArgument() {
	}

	public static SwizzleArgument swizzle() {
		return new SwizzleArgument();
	}

	@Override
	public int[] parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		boolean seenX = false;
		boolean seenY = false;
		boolean seenZ = false;
		while (reader.canRead()) {
			char c = reader.peek();
			if (c == 'x') {
				if (seenX) {
					throw new CommandSyntaxException("Duplicate axis 'x' at position " + reader.getCursor());
				}
				seenX = true;
			} else if (c == 'y') {
				if (seenY) {
					throw new CommandSyntaxException("Duplicate axis 'y' at position " + reader.getCursor());
				}
				seenY = true;
			} else if (c == 'z') {
				if (seenZ) {
					throw new CommandSyntaxException("Duplicate axis 'z' at position " + reader.getCursor());
				}
				seenZ = true;
			} else {
				break;
			}
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected an axis swizzle at position " + start);
		}
		int[] axes = new int[token.length()];
		for (int i = 0; i < token.length(); ++i) {
			char c = token.charAt(i);
			axes[i] = c == 'x' ? 0 : (c == 'y' ? 1 : 2);
		}
		return axes;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		if (remaining.isEmpty()) {
			return Collections.singletonList("xyz");
		}
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "swizzle()";
	}
}
