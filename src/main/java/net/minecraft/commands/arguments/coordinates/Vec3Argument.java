package net.minecraft.commands.arguments.coordinates;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec3;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 Vec3Argument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.coordinates.Vec3Argument} —
 * used by /execute positioned, /summon, /particle... Parses absolute / ~
 * relative / ^ local via {@link WorldCoordinates} (3-axis strict parse).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-VEC3-001
 */
public class Vec3Argument implements ArgumentType<WorldCoordinates> {

	private final boolean centerIntegers;

	private Vec3Argument(boolean centerIntegers) {
		this.centerIntegers = centerIntegers;
	}

	public static Vec3Argument vec3() {
		return new Vec3Argument(true);
	}

	public static Vec3Argument vec3(boolean centerIntegers) {
		return new Vec3Argument(centerIntegers);
	}

	@Override
	public WorldCoordinates parse(StringReader reader) throws CommandSyntaxException {
		return WorldCoordinates.parseDouble(reader, centerIntegers);
	}

	/** Real 1.20.1 static: resolve the named argument to a precise position. */
	public static Vec3 getVec3(CommandContext<?> ctx, String name) {
		Coordinates c = ctx.getArgument(name, Coordinates.class);
		if (c != null) {
			Object src = ctx.getSource();
			CommandSourceStack css = src instanceof CommandSourceStack ? (CommandSourceStack) src : null;
			return c.getPosition(css);
		}
		return Vec3.ZERO;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		if (remaining.isEmpty() || remaining.equals("~")) {
			return Collections.singletonList("~ ~ ~");
		}
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "vec3()";
	}
}
