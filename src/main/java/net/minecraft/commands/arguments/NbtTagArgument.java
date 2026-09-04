package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 NbtTagArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.NbtTagArgument} — a single
 * SNBT tag token consumed greedily. NBT evaluation is an honest boundary
 * (the /data runtime is scheduled separately); the token is stored verbatim.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-NBTTAG-001
 */
public class NbtTagArgument implements ArgumentType<String> {

	private NbtTagArgument() {
	}

	public static NbtTagArgument nbtTag() {
		return new NbtTagArgument();
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
			throw new CommandSyntaxException("Expected an NBT tag at position " + start);
		}
		return token;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "nbtTag()";
	}
}
