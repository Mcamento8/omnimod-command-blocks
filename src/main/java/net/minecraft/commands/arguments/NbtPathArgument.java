package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 NbtPathArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.NbtPathArgument} — the
 * {@code <path>} of /data get|merge|modify|remove. The path is stored
 * verbatim; path EVALUATION against 1.8 NBT is an honest boundary (the /data
 * runtime is scheduled separately per the UCBPP roadmap).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-NBTPATH-001
 */
public class NbtPathArgument implements ArgumentType<String> {

	private NbtPathArgument() {
	}

	public static NbtPathArgument nbtPath() {
		return new NbtPathArgument();
	}

	@Override
	public String parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected an NBT path at position " + start);
		}
		return token;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "nbtPath()";
	}
}
