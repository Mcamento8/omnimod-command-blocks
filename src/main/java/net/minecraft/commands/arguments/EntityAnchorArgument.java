package net.minecraft.commands.arguments;

import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 EntityAnchorArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.EntityAnchorArgument}
 * (8 imports in the real corpus): parses "feet"/"eyes" into an Anchor value
 * used with /execute anchored + facing in the parity track.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-ANCHOR-001
 */
public class EntityAnchorArgument implements ArgumentType<EntityAnchorArgument.Anchor> {

	public enum Anchor {
		FEET,
		EYES;

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	private EntityAnchorArgument() {
	}

	public static EntityAnchorArgument anchor() {
		return new EntityAnchorArgument();
	}

	@Override
	public Anchor parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		if (s.equalsIgnoreCase("feet")) {
			return Anchor.FEET;
		}
		if (s.equalsIgnoreCase("eyes")) {
			return Anchor.EYES;
		}
		throw new CommandSyntaxException("Invalid anchor '" + s + "' (expected feet or eyes)");
	}

	/** Real 1.20.1 static: read the parsed anchor. */
	public static Anchor getAnchor(CommandContext<?> ctx, String name) {
		Anchor a = ctx.getArgument(name, Anchor.class);
		return a != null ? a : Anchor.FEET;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Arrays.asList("feet", "eyes");
	}

	@Override
	public String toString() {
		return "anchor()";
	}
}
