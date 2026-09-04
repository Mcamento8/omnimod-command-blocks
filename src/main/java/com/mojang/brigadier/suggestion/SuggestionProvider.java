package com.mojang.brigadier.suggestion;

import com.mojang.brigadier.context.CommandContext;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier SuggestionProvider shim.
 *
 * Mirrors {@code com.mojang.brigadier.suggestion.SuggestionProvider<S>}: the
 * functional interface mods pass to {@code RequiredArgumentBuilder.suggests(...)}
 * to offer custom tab completions (e.g. registry values, saved names). The
 * bridge invokes it during tab completion and converts the returned
 * {@link Suggestions} into 1.8 command suggestions.
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-SUGGPRV-001
 */
@FunctionalInterface
public interface SuggestionProvider<S> {
	com.mojang.brigadier.suggestion.Suggestions getSuggestions(CommandContext<S> context,
			SuggestionsBuilder builder) throws com.mojang.brigadier.exceptions.CommandSyntaxException;
}
