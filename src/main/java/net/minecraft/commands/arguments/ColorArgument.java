package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ColorArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ColorArgument} — /team color.
 * The 16 vanilla formatting colors map to 0..15 (vanilla reference constants,
 * same order as the 1.8 EnumChatFormatting codes).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-COLOR-001
 */
public class ColorArgument implements ArgumentType<Integer> {

	private static final String[] COLORS = { "black", "dark_blue", "dark_green", "dark_aqua",
			"dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua",
			"red", "light_purple", "yellow", "white" };

	private ColorArgument() {
	}

	public static ColorArgument color() {
		return new ColorArgument();
	}

	@Override
	public Integer parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor()).toLowerCase();
		for (int i = 0; i < COLORS.length; ++i) {
			if (COLORS[i].equals(token)) {
				return Integer.valueOf(i);
			}
		}
		throw new CommandSyntaxException("Unknown color '" + token + "' at position " + start);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "color()";
	}
}
