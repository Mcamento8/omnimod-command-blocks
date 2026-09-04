package com.mojang.brigadier.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [Agent Note 2026-08-02] GENERAL: Brigadier command context shim.
 *
 * Mirrors {@code com.mojang.brigadier.context.CommandContext<S>} from Forge
 * 1.20.1. A context is passed to an {@code executes(...)} lambda giving it
 * access to the source ({@code getSource()}), parsed arguments
 * ({@code getArgument(name, type)}), and the child input string.
 *
 * [Agent Note 2026-08-28] PARITY UPGRADE (UCBPP CRITICAL-4) — repeated
 * arguments: vanilla /execute allows the same subcommand key to repeat
 * ({@code execute if entity @a if entity @b run ...}). The old putArgument
 * REPLACED the value, silently dropping every condition but the last. NOW:
 *   - {@link #putArgument} APPENDS when the key already exists: the stored
 *     value becomes an ordered {@code List<Object>} of every parse for that
 *     key (insertion order == source order).
 *   - {@link #getArgument} unwraps a SINGLE-element list transparently, so
 *     every existing caller (mods reading one value, the bridge, the old
 *     harness checks) keeps its exact behavior.
 *   - {@link #getArgumentEntries} exposes the ordered raw map (values may be
 *     lists) — this is what the execute engine replays in source order.
 *   - {@link #removeArgument} removes ALL values for the key (backtracking
 *     semantics stay correct for failed branches).
 *
 * GENERAL — part of the standard Brigadier API surface. No mod hardcode.
 *
 * Doc-ID: BRIG-CTX-002
 */
public class CommandContext<S> {
	private final S source;
	private final String input;
	private final Map<String, Object> arguments = new LinkedHashMap<String, Object>();

	public CommandContext(S source, String input) {
		this.source = source;
		this.input = input != null ? input : "";
	}

	public S getSource() {
		return source;
	}

	public String getInput() {
		return input;
	}

	@SuppressWarnings("unchecked")
	public <V> V getArgument(String name, Class<V> clazz) {
		Object v = arguments.get(name);
		if (v == null) {
			return null;
		}
		// [UCBPP CRITICAL-4] A repeated key is stored as a List — a single
		// element unwraps transparently (compat with every pre-existing caller).
		if (v instanceof List) {
			List<Object> list = (List<Object>) v;
			if (list.isEmpty()) {
				return null;
			}
			// A repeated key resolves to its MOST RECENT parse — the same
			// value a single-occurrence tree would have stored, so every
			// single-value caller keeps working.
			v = list.get(list.size() - 1);
		}
		try {
			return (V) v;
		} catch (ClassCastException e) {
			return null;
		}
	}

	/**
	 * [UCBPP CRITICAL-4] Store a parsed argument. When the key already holds a
	 * value, the new value is APPENDED (ordered List) instead of replacing it —
	 * repeated execute subcommands (if/unless chains) keep every condition.
	 */
	public void putArgument(String name, Object value) {
		if (arguments.containsKey(name)) {
			Object existing = arguments.get(name);
			List<Object> seq;
			if (existing instanceof List) {
				seq = (List<Object>) existing;
			} else {
				seq = new ArrayList<Object>(2);
				seq.add(existing);
				arguments.put(name, seq);
			}
			seq.add(value);
			return;
		}
		arguments.put(name, value);
	}

	/**
	 * [UCBPP CRITICAL-4] Ordered view of every parsed argument. Values are the
	 * raw stored objects (a repeated key yields the internal List). Iteration
	 * order == insertion order == the order the tokens appeared in the input —
	 * the contract the execute engine replays.
	 */
	public Map<String, Object> getArgumentEntries() {
		return Collections.unmodifiableMap(arguments);
	}

	/**
	 * [UCBPP CRITICAL-4] Remove a parsed argument (bridge backtracking: when an
	 * argument branch fails deeper in the tree its parsed value must not leak
	 * into the final context). Removes ALL stored values for the key.
	 */
	public void removeArgument(String name) {
		arguments.remove(name);
	}

	public boolean hasNodes() {
		return false;
	}
}
