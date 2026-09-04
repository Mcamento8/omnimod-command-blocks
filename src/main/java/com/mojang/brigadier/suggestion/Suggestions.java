package com.mojang.brigadier.suggestion;

import java.util.ArrayList;
import java.util.List;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier Suggestions shim.
 *
 * Mirrors {@code com.mojang.brigadier.suggestion.Suggestions}: the result
 * object a {@link SuggestionProvider} returns from a mod command tree's
 * {@code .suggests((ctx, builder) -> ...)} callback. The bridge reads
 * {@link #getList()} to feed 1.8 tab completion.
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-SUGG-001
 */
public class Suggestions {
	private final List<String> suggestions;

	public Suggestions(List<String> suggestions) {
		this.suggestions = suggestions != null ? suggestions : new ArrayList<String>();
	}

	public List<String> getList() {
		return suggestions;
	}

	public boolean isEmpty() {
		return suggestions.isEmpty();
	}
}
