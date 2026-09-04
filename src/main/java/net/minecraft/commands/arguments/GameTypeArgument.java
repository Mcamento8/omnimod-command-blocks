package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 GameTypeArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.GameTypeArgument} — used by
 * /gamemode, /defaultgamemode. Accepts the vanilla 1.20.1 names AND the 1.8
 * shorthand letters (both are real vanilla surfaces across versions):
 *   survival|s -> 0, creative|c -> 1, adventure|a -> 2, spectator|sp -> 3
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-GTYPE-001
 */
public class GameTypeArgument implements ArgumentType<Integer> {

	private GameTypeArgument() {
	}

	public static GameTypeArgument gameType() {
		return new GameTypeArgument();
	}

	@Override
	public Integer parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor()).toLowerCase();
		if (token.equals("survival") || token.equals("s")) {
			return Integer.valueOf(0);
		}
		if (token.equals("creative") || token.equals("c")) {
			return Integer.valueOf(1);
		}
		if (token.equals("adventure") || token.equals("a")) {
			return Integer.valueOf(2);
		}
		if (token.equals("spectator") || token.equals("sp")) {
			return Integer.valueOf(3);
		}
		throw new CommandSyntaxException("Invalid game mode '" + token + "' at position " + start);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "gameType()";
	}
}
