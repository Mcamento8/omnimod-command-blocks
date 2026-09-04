package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 NbtCompoundTagArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.NbtCompoundTagArgument} —
 * the greedy {...} compound consumed by /data merge. Stored verbatim; SNBT
 * evaluation is an honest boundary (see NbtTagArgument).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-NBTCOMP-001
 */
public class NbtCompoundTagArgument implements ArgumentType<String> {

	private NbtCompoundTagArgument() {
	}

	public static NbtCompoundTagArgument compoundTag() {
		return new NbtCompoundTagArgument();
	}

	@Override
	public String parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		if (!reader.canRead() || reader.peek() != '{') {
			throw new CommandSyntaxException("Expected a compound tag at position " + reader.getCursor());
		}
		int depth = 0;
		int start = reader.getCursor();
		while (reader.canRead()) {
			char c = reader.peek();
			reader.skip();
			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return reader.getString().substring(start, reader.getCursor());
				}
			}
		}
		throw new CommandSyntaxException("Expected closing '}' for compound tag at position " + start);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "compoundTag()";
	}
}
