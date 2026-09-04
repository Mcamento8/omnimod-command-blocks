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
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 ColumnPosArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.coordinates.ColumnPosArgument}
 * — a 2-axis (x, z) position used by /locate-like commands. Y comes from the
 * source position.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-COLPOS-001
 */
public class ColumnPosArgument implements ArgumentType<ColumnPosArgument.ColumnPos> {

	/** A parsed 2-axis column value (lazy resolution like WorldCoordinates). */
	public static final class ColumnPos {
		public final double x;
		public final double z;
		public final boolean relX;
		public final boolean relZ;

		public ColumnPos(double x, double z, boolean relX, boolean relZ) {
			this.x = x;
			this.z = z;
			this.relX = relX;
			this.relZ = relZ;
		}

		public BlockPos getBlockPos(CommandSourceStack source) {
			Vec3 base = source != null ? source.getPosition() : new Vec3(0, 0, 0);
			double wx = relX ? base.x + x : x;
			double wz = relZ ? base.z + z : z;
			return new BlockPos(net.minecraft.util.MathHelper.floor_double(wx), 0,
					net.minecraft.util.MathHelper.floor_double(wz));
		}
	}

	private ColumnPosArgument() {
	}

	public static ColumnPosArgument columnPos() {
		return new ColumnPosArgument();
	}

	@Override
	public ColumnPos parse(StringReader reader) throws CommandSyntaxException {
		double x = readAxis(reader);
		reader.skipWhitespace();
		if (!reader.canRead()) {
			throw new CommandSyntaxException("Expected 2 coordinates, found 1");
		}
		double z = readAxis(reader);
		return new ColumnPos(x, z, false, false);
	}

	private static double readAxis(StringReader reader) throws CommandSyntaxException {
		boolean rel = reader.canRead() && reader.peek() == '~';
		if (rel) {
			reader.skip();
		}
		int start = reader.getCursor();
		while (reader.canRead() && (Character.isDigit(reader.peek()) || reader.peek() == '-'
				|| reader.peek() == '+' || reader.peek() == '.')) {
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		if (s.isEmpty()) {
			return rel ? 0.0D : Double.NaN;
		}
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new CommandSyntaxException("Invalid coordinate '" + s + "'");
		}
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "columnPos()";
	}
}
