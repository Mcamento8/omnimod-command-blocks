package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 HeightmapArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.HeightmapArgument} — the
 * vanilla 1.20.1 heightmap names (reference surface); resolution to the 1.8
 * world heightmap is at use time.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-HMAP-001
 */
public class HeightmapArgument implements ArgumentType<String> {

	private static final String[] NAMES = { "motion_blocking", "motion_blocking_no_leaves",
			"ocean_floor", "ocean_floor_wg", "world_surface", "world_surface_wg" };

	private HeightmapArgument() {
	}

	public static HeightmapArgument heightmap() {
		return new HeightmapArgument();
	}

	@Override
	public String parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor()).toLowerCase();
		for (String n : NAMES) {
			if (n.equals(token)) {
				return token;
			}
		}
		throw new CommandSyntaxException("Unknown heightmap '" + token + "' at position " + start);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "heightmap()";
	}
}
