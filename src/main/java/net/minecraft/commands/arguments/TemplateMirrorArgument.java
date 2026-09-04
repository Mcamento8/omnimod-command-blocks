package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 TemplateMirrorArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.TemplateMirrorArgument} —
 * /place template mirror axis. Vanilla names: none | left_right | front_back.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-TMIRROR-001
 */
public class TemplateMirrorArgument implements ArgumentType<String> {

	private static final String[] NAMES = { "none", "left_right", "front_back" };

	private TemplateMirrorArgument() {
	}

	public static TemplateMirrorArgument templateMirror() {
		return new TemplateMirrorArgument();
	}

	@Override
	public String parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor()).toLowerCase();
		for (String n : NAMES) {
			if (n.equals(token)) {
				return token;
			}
		}
		throw new CommandSyntaxException("Unknown mirror '" + token + "' at position " + start);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "templateMirror()";
	}
}
