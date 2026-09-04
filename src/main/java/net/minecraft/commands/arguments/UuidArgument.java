package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 UuidArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.UuidArgument}: parses a
 * UUID string token into java.util.UUID.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-UUID-001
 */
public class UuidArgument implements ArgumentType<UUID> {

	private UuidArgument() {
	}

	public static UuidArgument uuid() {
		return new UuidArgument();
	}

	@Override
	public UUID parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			throw new CommandSyntaxException("Invalid UUID '" + s + "'");
		}
	}

	/** Real 1.20.1 static: read the parsed UUID. */
	public static UUID getUuid(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, UUID.class);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "uuid()";
	}
}
