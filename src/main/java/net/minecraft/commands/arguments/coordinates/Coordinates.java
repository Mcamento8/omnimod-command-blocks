package net.minecraft.commands.arguments.coordinates;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.phys.Vec3;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 Coordinates interface shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.coordinates.Coordinates}.
 * BlockPosArgument.parse produces a WorldCoordinates implementing this; mods
 * may hold it ({@code ctx.getArgument("pos", Coordinates.class)}) or use the
 * BlockPosArgument static helpers.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-COORD-001
 */
public interface Coordinates {

	/** Absolute block position for this coordinates at the given source. */
	BlockPos getBlockPos(CommandSourceStack source);

	/** Precise world position (double precision) for this coordinates. */
	Vec3 getPosition(CommandSourceStack source);
}
