package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ObjectiveCriteriaArgument
 * shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ObjectiveCriteriaArgument} —
 * /scoreboard objectives add <name> <criteria>. Parses the criteria token;
 * full criteria registry parity is at use time (1.8 IScoreObjectiveCriteria).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-OCRIT-001
 */
public class ObjectiveCriteriaArgument implements ArgumentType<String> {

	private ObjectiveCriteriaArgument() {
	}

	public static ObjectiveCriteriaArgument criteria() {
		return new ObjectiveCriteriaArgument();
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
			throw new CommandSyntaxException("Expected a criterion at position " + start);
		}
		return token;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "criteria()";
	}
}
