package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ScoreboardSlotArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ScoreboardSlotArgument} —
 * /scoreboard objectives setdisplay <slot>. Vanilla maps display-slot names
 * (belowName/list/sidebar/team-*) to ints; the numeric 1.8 setDisplaySlot
 * ids are the same space (vanilla reference constants).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-SB SLOT-001
 */
public class ScoreboardSlotArgument implements ArgumentType<Integer> {

	private ScoreboardSlotArgument() {
	}

	public static ScoreboardSlotArgument displaySlot() {
		return new ScoreboardSlotArgument();
	}

	@Override
	public Integer parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor()).toLowerCase();
		if (token.equals("list")) {
			return Integer.valueOf(0);
		}
		if (token.equals("sidebar")) {
			return Integer.valueOf(1);
		}
		if (token.startsWith("sidebar.team.")) {
			String color = token.substring("sidebar.team.".length());
			// Vanilla formatting-code order: black..white = 0..15.
			String[] colors = { "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
					"dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red",
					"light_purple", "yellow", "white" };
			for (int i = 0; i < colors.length; ++i) {
				if (colors[i].equals(color)) {
					return Integer.valueOf(16 + i);
				}
			}
		}
		if (token.equals("belowname")) {
			return Integer.valueOf(2);
		}
		throw new CommandSyntaxException("Unknown display slot '" + token + "' at position " + start);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "displaySlot()";
	}
}
