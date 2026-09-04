package net.minecraft.commands.arguments.coordinates;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 BlockPosArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.coordinates.BlockPosArgument}
 * (6 imports in the real corpus; needed by the vanilla-parity track for
 * /execute if block, /fill... in later phases). Mods write:
 *   Commands.argument("pos", BlockPosArgument.blockPos())
 *   ... BlockPosArgument.getBlockPos(ctx, "pos")
 * or hold: ctx.getArgument("pos", Coordinates.class)
 *
 * Supports absolute / ~relative / ^local via {@link WorldCoordinates}.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-BPOS-001
 */
public class BlockPosArgument implements ArgumentType<WorldCoordinates> {

	private BlockPosArgument() {
	}

	public static BlockPosArgument blockPos() {
		return new BlockPosArgument();
	}

	@Override
	public WorldCoordinates parse(StringReader reader) throws CommandSyntaxException {
		return WorldCoordinates.parseDouble(reader, true);
	}

	/** Real 1.20.1 static: resolve the named argument to a block position. */
	public static BlockPos getBlockPos(CommandContext<?> ctx, String name) {
		Coordinates c = ctx.getArgument(name, Coordinates.class);
		if (c != null) {
			return c.getBlockPos(sourceStackOf(ctx));
		}
		return BlockPos.ORIGIN;
	}

	/** Real 1.20.1 static: resolve the named argument to a precise position. */
	public static Vec3 getSpawnablePos(CommandContext<?> ctx, String name) {
		return getPosition(ctx, name);
	}

	public static Vec3 getPosition(CommandContext<?> ctx, String name) {
		Coordinates c = ctx.getArgument(name, Coordinates.class);
		if (c != null) {
			return c.getPosition(sourceStackOf(ctx));
		}
		return Vec3.ZERO;
	}

	private static CommandSourceStack sourceStackOf(CommandContext<?> ctx) {
		Object src = ctx.getSource();
		return src instanceof CommandSourceStack ? (CommandSourceStack) src : null;
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
		return "blockPos()";
	}
}
