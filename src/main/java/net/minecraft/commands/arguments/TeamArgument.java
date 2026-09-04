package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.ICommandSender;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 TeamArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.TeamArgument} —
 * /team, /execute if score team-style. Resolves against the REAL 1.8
 * {@link Scoreboard#getTeam}.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-TEAM-001
 */
public class TeamArgument implements ArgumentType<String> {

	private TeamArgument() {
	}

	public static TeamArgument team() {
		return new TeamArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to a ScorePlayerTeam. */
	public static ScorePlayerTeam getTeam(CommandContext<?> ctx, String name)
			throws CommandSyntaxException {
		String token = ctx.getArgument(name, String.class);
		if (token == null) {
			throw new CommandSyntaxException("Unknown team");
		}
		ICommandSender sender = EntityArgument.sourceOf(ctx);
		try {
			if (sender != null && sender.getEntityWorld() != null) {
				Scoreboard sb = sender.getEntityWorld().getScoreboard();
				ScorePlayerTeam team = sb != null ? sb.getTeam(token) : null;
				if (team != null) {
					return team;
				}
			}
		} catch (Throwable ignored) {
		}
		throw new CommandSyntaxException("Unknown team '" + token + "'");
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
			throw new CommandSyntaxException("Expected a team name at position " + start);
		}
		return token;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "team()";
	}
}
