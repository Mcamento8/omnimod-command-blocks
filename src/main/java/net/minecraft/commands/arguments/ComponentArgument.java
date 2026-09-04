package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.network.chat.Component;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ComponentArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ComponentArgument} — parses
 * a text component (vanilla: JSON / SNBT). OmniMod accepts plain text or a
 * quoted string and wraps it into a literal {@link Component}; full JSON text
 * components are an honest boundary (the engine renders legacy chat).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-COMP-001
 */
public class ComponentArgument implements ArgumentType<Component> {

	private ComponentArgument() {
	}

	public static ComponentArgument textComponent() {
		return new ComponentArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to a Component. */
	public static Component getComponent(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, Component.class);
	}

	@Override
	public Component parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		String token;
		if (reader.canRead() && (reader.peek() == '"' || reader.peek() == '\'')) {
			token = reader.readString();
		} else {
			// readRemaining() CONSUMES (getRemaining() only peeks — same fix as MessageArgument).
			token = reader.readRemaining();
		}
		if (token == null || token.isEmpty()) {
			throw new CommandSyntaxException("Expected a text component at position " + reader.getCursor());
		}
		return Component.literal(token);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "textComponent()";
	}
}
