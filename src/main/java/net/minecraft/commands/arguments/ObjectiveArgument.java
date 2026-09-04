package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.ICommandSender;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ObjectiveArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ObjectiveArgument} — used by
 * /execute if|store score, /scoreboard... Parses a word and resolves it
 * against the REAL 1.8 {@link Scoreboard} of the sender's world
 * ({@code Scoreboard.getObjective}).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-OBJ-001
 */
public class ObjectiveArgument implements ArgumentType<String> {

	private ObjectiveArgument() {
	}

	public static ObjectiveArgument objective() {
		return new ObjectiveArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to a ScoreObjective. */
	public static ScoreObjective getObjective(CommandContext<?> ctx, String name)
			throws CommandSyntaxException {
		String token = ctx.getArgument(name, String.class);
		if (token == null) {
			throw new CommandSyntaxException("Unknown scoreboard objective");
		}
		ICommandSender sender = EntityArgument.sourceOf(ctx);
		ScoreObjective obj = resolve(sender, token);
		if (obj == null) {
			throw new CommandSyntaxException("Unknown scoreboard objective '" + token + "'");
		}
		return obj;
	}

	private static ScoreObjective resolve(ICommandSender sender, String token) {
		try {
			if (sender == null || sender.getEntityWorld() == null) {
				return null;
			}
			Scoreboard sb = sender.getEntityWorld().getScoreboard();
			return sb != null ? sb.getObjective(token) : null;
		} catch (Throwable ignored) {
			return null;
		}
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
			throw new CommandSyntaxException("Expected an objective at position " + start);
		}
		return token;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "objective()";
	}
}
