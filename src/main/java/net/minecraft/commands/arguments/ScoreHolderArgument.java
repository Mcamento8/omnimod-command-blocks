package net.minecraft.commands.arguments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ScoreHolderArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ScoreHolderArgument} —
 * /execute if|store score, /scoreboard players... Parses a selector
 * ({@code @a}) or a plain holder name; resolution to holder NAMES goes through
 * the real {@link net.minecraft.commands.arguments.selector.EntitySelector}.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-SHOLD-001
 */
public class ScoreHolderArgument implements ArgumentType<ScoreHolderArgument.ScoreHolder> {

	/** A holder token that resolves to one or more score-holder names. */
	public static final class ScoreHolder {
		public final String token;
		public final boolean selector;

		public ScoreHolder(String token, boolean selector) {
			this.token = token;
			this.selector = selector;
		}

		/** Resolve to holder names (entity names for selectors, token otherwise). */
		public List<String> findNames(ICommandSender sender) {
			List<String> out = new ArrayList<String>();
			if (selector) {
				List<Entity> matched =
						new net.minecraft.commands.arguments.selector.EntitySelector(token).getEntities(sender);
				for (Entity e : matched) {
					if (e != null && e.getName() != null) {
						out.add(e.getName());
					}
				}
				return out;
			}
			out.add(token);
			return out;
		}
	}

	private ScoreHolderArgument() {
	}

	public static ScoreHolderArgument scoreHolder() {
		return new ScoreHolderArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to holder names. */
	public static List<String> getNames(CommandContext<?> ctx, String name) {
		ScoreHolder h = ctx.getArgument(name, ScoreHolder.class);
		if (h == null) {
			return Collections.emptyList();
		}
		return h.findNames(EntityArgument.sourceOf(ctx));
	}

	@Override
	public ScoreHolder parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected a score holder at position " + start);
		}
		return new ScoreHolder(token, token.startsWith("@"));
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "scoreHolder()";
	}
}
