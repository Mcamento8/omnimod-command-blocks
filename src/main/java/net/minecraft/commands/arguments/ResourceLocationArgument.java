package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.util.ResourceLocation;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ResourceLocationArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.ResourceLocationArgument}.
 * Parses a namespaced id ("minecraft:stone" / "modid:item") into the 1.8
 * {@link ResourceLocation} (which accepts both "domain:path" and bare "path").
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-RL-001
 */
public class ResourceLocationArgument implements ArgumentType<ResourceLocation> {

	private ResourceLocationArgument() {
	}

	public static ResourceLocationArgument id() {
		return new ResourceLocationArgument();
	}

	@Override
	public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		if (s.isEmpty()) {
			throw new CommandSyntaxException("Expected resource location at position " + start);
		}
		try {
			return new ResourceLocation(s);
		} catch (Throwable t) {
			throw new CommandSyntaxException("Invalid resource location '" + s + "': " + t.getMessage());
		}
	}

	/** Real 1.20.1 static: read the parsed id. */
	public static ResourceLocation getId(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, ResourceLocation.class);
	}

	/** Real 1.20.1 static (alias surface used by some mods). */
	public static ResourceLocation getResource(CommandContext<?> ctx, String name) {
		return getId(ctx, name);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "resourceLocation()";
	}
}
