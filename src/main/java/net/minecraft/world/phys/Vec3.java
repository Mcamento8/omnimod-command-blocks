package net.minecraft.world.phys;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 Vec3 shim (minimal surface).
 *
 * Mirrors {@code net.minecraft.world.phys.Vec3} — the 1.20.1 position class
 * that mod command lambdas receive from
 * {@code context.getSource().getPosition()}. Backed by the same math shape as
 * 1.8's {@code net.minecraft.util.Vec3} but under the 1.20.1 package so mod
 * bytecode classloads. Only the commonly-referenced surface is implemented;
 * the engine's own math stays on net.minecraft.util.Vec3.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-VEC3-001
 */
public class Vec3 {
	public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);

	public final double x;
	public final double y;
	public final double z;

	public Vec3(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vec3 add(double dx, double dy, double dz) {
		return new Vec3(x + dx, y + dy, z + dz);
	}

	public Vec3 subtract(net.minecraft.util.Vec3 other) {
		return new Vec3(x - other.xCoord, y - other.yCoord, z - other.zCoord);
	}

	public double distanceTo(Vec3 other) {
		double dx = x - other.x;
		double dy = y - other.y;
		double dz = z - other.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ")";
	}
}
