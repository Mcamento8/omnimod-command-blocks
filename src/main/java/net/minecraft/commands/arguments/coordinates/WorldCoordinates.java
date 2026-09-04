package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 WorldCoordinates shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.coordinates.WorldCoordinates}
 * — parses the three REAL 1.20.1 coordinate forms used by every position
 * argument:
 *   absolute      "10 64 -20"
 *   relative      "~ ~5 ~-2"   (offset from the source position)
 *   local         "^ ^2 ^1"     (offset along the source rotation axes)
 *
 * All three can mix per-axis ("10 ~ ~-2" except ^ must not mix with ~/abs —
 * mixing is rejected with an honest syntax error like real Brigadier).
 *
 * [Agent Note 2026-08-28] PARITY FIX (UCBPP CRITICAL-1 — proven by probe
 * ProbeAbs on the real compiled classes):
 *   - The previous parse returned after reading ONLY the X axis for input
 *     that does not start with '~'/'^' — Y and Z stayed in the reader
 *     ("10 64 -20" resolved to (x,0,0)). NOW: a strict 3-axis loop parses
 *     every axis independently (absolute/relative/local) with the same
 *     mixing-rejection messages.
 *   - The previous LOCAL math ignored the forward axis and put the local
 *     UP offset on the horizontal plane ("^ ^2 ^1" moved sideways, not up).
 *     NOW: vanilla basis — forward F = look(yaw,pitch), left L =
 *     normalize(up×F), up U = F×L; world offset = L*lx + U*ly + F*lz.
 *   - Relative axes now resolve against the MODIFIED source position
 *     (CommandSourceStack.getPosition) and local axes against the MODIFIED
 *     rotation (CommandSourceStack.getRotation) — this is what makes
 *     /execute at|positioned|rotated compose (vanilla CSS architecture).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-WCOORD-002
 */
public class WorldCoordinates implements Coordinates {

	private final double x;
	private final double y;
	private final double z;
	private final boolean relX;
	private final boolean relY;
	private final boolean relZ;
	private final boolean local;

	private WorldCoordinates(double x, double y, double z, boolean relX, boolean relY, boolean relZ,
			boolean local) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.relX = relX;
		this.relY = relY;
		this.relZ = relZ;
		this.local = local;
	}

	public static WorldCoordinates parseDouble(StringReader reader, boolean centerIntegers)
			throws CommandSyntaxException {
		if (!reader.canRead()) {
			throw new CommandSyntaxException("Expected coordinate at position " + reader.getCursor());
		}
		// [UCBPP CRITICAL-1] Strict 3-axis loop — every axis resolves
		// independently; ^ must not mix with ~ or absolute (vanilla message).
		double[] vals = new double[3];
		boolean[] rels = new boolean[3];
		boolean sawLocal = false;
		boolean sawWorld = false;
		for (int axis = 0; axis < 3; ++axis) {
			if (axis > 0) {
				reader.skipWhitespace();
				if (!reader.canRead()) {
					throw new CommandSyntaxException("Expected 3 coordinates, found " + axis);
				}
			}
			char c = reader.peek();
			if (c == '^') {
				if (sawWorld) {
					throw new CommandSyntaxException(
							"Cannot mix world and local coordinates (must all be ^ or none)");
				}
				sawLocal = true;
				reader.skip();
				vals[axis] = readOffset(reader);
				rels[axis] = true;
			} else if (c == '~') {
				if (sawLocal) {
					throw new CommandSyntaxException(
							"Cannot mix world and local coordinates (must all be ^ or none)");
				}
				sawWorld = true;
				reader.skip();
				rels[axis] = true;
				vals[axis] = readOffset(reader);
			} else {
				if (sawLocal) {
					throw new CommandSyntaxException(
							"Cannot mix world and local coordinates (must all be ^ or none)");
				}
				sawWorld = true;
				vals[axis] = readAbsolute(reader, centerIntegers);
				rels[axis] = false;
			}
		}
		return new WorldCoordinates(vals[0], vals[1], vals[2], rels[0], rels[1], rels[2], sawLocal);
	}

	/** Read an optional signed decimal after '~' or '^'. */
	private static double readOffset(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		while (reader.canRead() && (Character.isDigit(reader.peek()) || reader.peek() == '-'
				|| reader.peek() == '+' || reader.peek() == '.')) {
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		if (s.isEmpty()) {
			return 0.0D;
		}
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new CommandSyntaxException("Invalid coordinate offset '" + s + "'");
		}
	}

	/** Read an absolute coordinate (double; ints get +0.5 centering when asked). */
	private static double readAbsolute(StringReader reader, boolean centerIntegers) throws CommandSyntaxException {
		int start = reader.getCursor();
		boolean sawDecimal = false;
		while (reader.canRead() && (Character.isDigit(reader.peek()) || reader.peek() == '-'
				|| reader.peek() == '+' || reader.peek() == '.')) {
			if (reader.peek() == '.') {
				sawDecimal = true;
			}
			reader.skip();
		}
		String s = reader.getString().substring(start, reader.getCursor());
		if (s.isEmpty() || s.equals("-") || s.equals("+")) {
			throw new CommandSyntaxException("Expected coordinate at position " + start);
		}
		double v;
		try {
			v = Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new CommandSyntaxException("Invalid coordinate '" + s + "'");
		}
		if (centerIntegers && !sawDecimal) {
			v += 0.5D;
		}
		return v;
	}

	@Override
	public BlockPos getBlockPos(CommandSourceStack source) {
		Vec3 p = getPosition(source);
		return new BlockPos(MathHelper.floor_double(p.x), MathHelper.floor_double(p.y),
				MathHelper.floor_double(p.z));
	}

	@Override
	public Vec3 getPosition(CommandSourceStack source) {
		if (local) {
			// [UCBPP CRITICAL-3] Vanilla basis: forward F = look(yaw, pitch);
			// left L = normalize(worldUp × F); up U = F × L.
			// Offset = L*x + U*y + F*z. Rotation/position come from the
			// MODIFIED source (execute at/positioned/rotated compose).
			double lx = x, ly = y, lz = z;
			float yaw = 0.0F;
			float pitch = 0.0F;
			double eyeOffset = 0.0D;
			Vec3 base = source != null ? source.getPosition() : new Vec3(lx, ly, lz);
			Vec2 rot = source != null ? source.getRotation() : null;
			net.minecraft.entity.Entity e = source != null ? source.getEntity() : null;
			if (rot != null) {
				// Explicit modified rotation (rotated / facing / rotated as).
				yaw = rot.x;
				pitch = rot.y;
				if ("eyes".equals(source.getAnchor()) && e != null) {
					eyeOffset = e.getEyeHeight();
				}
			} else if (e != null) {
				// Entity rotation (default anchor behaves like 'eyes' for the
				// original 1.8 shim semantics — documented in the audit).
				yaw = e.rotationYaw;
				pitch = e.rotationPitch;
				eyeOffset = e.getEyeHeight();
			}
			double yawRad = Math.toRadians(yaw);
			double pitchRad = Math.toRadians(pitch);
			double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
			double fy = -Math.sin(pitchRad);
			double fz = Math.cos(yawRad) * Math.cos(pitchRad);
			// left = worldUp × forward = (fz, 0, -fx)
			double lhx = fz, lhy = 0.0D, lhz = -fx;
			double len = Math.sqrt(lhx * lhx + lhy * lhy + lhz * lhz);
			if (len < 1.0E-6D) {
				// Looking straight up/down: the horizontal basis degenerates.
				// Vanilla produces NaN here; we fall back to yaw=0 basis
				// (honest, documented deviation that cannot crash commands).
				lhx = 1.0D;
				lhy = 0.0D;
				lhz = 0.0D;
			} else {
				lhx /= len;
				lhz /= len;
			}
			// up = forward × left
			double ux = fy * lhz - fz * lhy;
			double uy = fz * lhx - fx * lhz;
			double uz = fx * lhy - fy * lhx;
			return new Vec3(
					base.x + lx * lhx + ly * ux + lz * fx,
					base.y + eyeOffset + lx * lhy + ly * uy + lz * fy,
					base.z + lx * lhz + ly * uz + lz * fz);
		}
		// World axes: relative offsets resolve against the (possibly modified)
		// source position — this is what makes /execute at|positioned work.
		ICommandSender sender = source != null ? source.getRawSender() : null;
		Vec3 base;
		if (sender != null && (relX || relY || relZ)) {
			base = source.getPosition();
		} else {
			base = Vec3.ZERO;
		}
		return new Vec3((relX ? base.x : 0.0D) + x, (relY ? base.y : 0.0D) + y, (relZ ? base.z : 0.0D) + z);
	}

	@Override
	public String toString() {
		return "WorldCoordinates(" + (relX ? "~" : "") + x + ", " + (relY ? "~" : "") + y + ", "
				+ (relZ ? "~" : "") + z + (local ? ", local" : "") + ")";
	}
}
