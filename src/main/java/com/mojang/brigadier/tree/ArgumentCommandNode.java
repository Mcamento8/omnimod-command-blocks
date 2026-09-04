package com.mojang.brigadier.tree;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier argument command node shim.
 *
 * Mirrors {@code com.mojang.brigadier.tree.ArgumentCommandNode<S, T>} from
 * Forge 1.20.1. An argument node consumes ONE dynamic input token, parses it
 * through its {@link ArgumentType} and stores the typed result on the
 * {@code CommandContext} under its {@code name} — exactly what mod lambdas
 * read via {@code ctx.getArgument(name, ...)} / static helpers like
 * {@code EntityArgument.getPlayer(ctx, "target")}.
 *
 * This replaces the previous behavior where RequiredArgumentBuilder built a
 * LiteralCommandNode and the bridge treated arguments as nameless wildcards
 * (root cause: mod arguments were never parsed, so
 * {@code ctx.getArgument} was always null and typed dispatch was impossible).
 *
 * MATCHING: a node with an executor runs the executor after consuming its
 * token (real Brigadier allows executes() on argument nodes); a node with
 * children keeps parsing deeper tokens.
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-ARGNODE-001
 */
public class ArgumentCommandNode<S, T> extends CommandNode<S> {
	private final String name;
	private final ArgumentType<T> type;
	private final SuggestionProvider<S> suggestionProvider;

	public ArgumentCommandNode(String name, ArgumentType<T> type, Command<S> command, Predicate<S> requirement,
			boolean hasRequirement, SuggestionProvider<S> suggestionProvider) {
		super(command, requirement, hasRequirement);
		this.name = name != null ? name : "arg";
		this.type = type;
		this.suggestionProvider = suggestionProvider;
	}

	public String getName() {
		return name;
	}

	public ArgumentType<T> getType() {
		return type;
	}

	public SuggestionProvider<S> getSuggestionProvider() {
		return suggestionProvider;
	}

	/**
	 * Parse ONE token (or a quoted/greedy value per the type) and store the
	 * typed result on the context. Returns true when the type consumed input.
	 */
	public T parse(StringReader reader, CommandContext<S> context) throws CommandSyntaxException {
		int start = reader.getCursor();
		T result = type.parse(reader);
		if (reader.getCursor() == start) {
			// The type consumed nothing — treat as a failed match.
			throw new CommandSyntaxException("Expected argument <" + name + "> at position " + start);
		}
		context.putArgument(name, result);
		return result;
	}

	/** Offer completions for the unfinished last word (bridge tab completion). */
	public List<String> listSuggestions(String remaining, CommandContext<S> context) {
		if (suggestionProvider != null && context != null) {
			try {
				Suggestions s = suggestionProvider.getSuggestions(context,
						new SuggestionsBuilder(remaining, remaining));
				if (s != null) {
					return s.getList();
				}
			} catch (Throwable ignored) {
				// fall through to the type's own suggestions
			}
		}
		return type != null ? type.listSuggestions(remaining) : Collections.<String>emptyList();
	}
}
