package com.mojang.brigadier.suggestion;

import java.util.ArrayList;
import java.util.List;

/**
 * [Agent Note 2026-08-28] GENERAL: Brigadier SuggestionsBuilder shim.
 *
 * Mirrors {@code com.mojang.brigadier.suggestion.SuggestionsBuilder}: passed
 * into a mod's {@code .suggests((ctx, builder) -> builder.suggest("x"))}
 * callbacks. Suggestions are returned via {@link #build()} / {@link #buildFuture()}
 * (the future form is accepted but completes immediately — single-threaded
 * integrated server).
 *
 * GENERAL — standard Brigadier API surface, no mod hardcode.
 *
 * Doc-ID: BRIG-SUGGBLD-001
 */
public class SuggestionsBuilder {
	private final String input;
	private final String remaining;
	private final List<String> suggestions = new ArrayList<String>();

	public SuggestionsBuilder(String input, String remaining) {
		this.input = input != null ? input : "";
		this.remaining = remaining != null ? remaining : "";
	}

	public String getInput() {
		return input;
	}

	public String getRemaining() {
		return remaining;
	}

	public SuggestionsBuilder suggest(String text) {
		if (text != null && !suggestions.contains(text)) {
			suggestions.add(text);
		}
		return this;
	}

	public Suggestions build() {
		return new Suggestions(suggestions);
	}

	/** Real Brigadier returns a CompletableFuture; OmniMod is single-threaded
	 * so this completes immediately with the built suggestions. */
	public java.util.concurrent.CompletableFuture<Suggestions> buildFuture() {
		return java.util.concurrent.CompletableFuture.completedFuture(build());
	}
}
