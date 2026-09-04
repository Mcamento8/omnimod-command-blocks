package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ParticleArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ParticleArgument} — /particle
 * <name>. Parses a namespaced particle id; full 1.20.1 particle OPTIONS
 * parsing is an honest boundary (OmniMod's particle layer maps ids, not typed
 * options).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-PARTICLE-001
 */
public class ParticleArgument implements ArgumentType<String> {

	private ParticleArgument() {
	}

	public static ParticleArgument particle() {
		return new ParticleArgument();
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
			throw new CommandSyntaxException("Expected a particle id at position " + start);
		}
		return token;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "particle()";
	}
}
