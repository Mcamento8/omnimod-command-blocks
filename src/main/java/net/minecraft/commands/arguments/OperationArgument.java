package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 OperationArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.OperationArgument} — the
 * scoreboard operation between two scores: = += -= *= /= %= < > >< .
 * Supported ops map 1:1 onto the 1.8 Scoreboard playersOperateScoreboard
 * semantics; the token is returned for the caller to apply.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-OPER-001
 */
public class OperationArgument implements ArgumentType<String> {

	private OperationArgument() {
	}

	public static OperationArgument operation() {
		return new OperationArgument();
	}

	@Override
	public String parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())
				&& (reader.peek() == '=' || reader.peek() == '+' || reader.peek() == '-'
						|| reader.peek() == '*' || reader.peek() == '/' || reader.peek() == '%'
						|| reader.peek() == '<' || reader.peek() == '>')) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.equals("=") || token.equals("+=") || token.equals("-=") || token.equals("*=")
				|| token.equals("/=") || token.equals("%=") || token.equals("<") || token.equals(">")
				|| token.equals("<=") || token.equals(">=") || token.equals("><")) {
			return token;
		}
		throw new CommandSyntaxException("Unknown operation '" + token + "' at position " + start);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "operation()";
	}
}
