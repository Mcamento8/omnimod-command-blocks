package net.minecraft.world.phys;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 Vec2 shim.
 *
 * Mirrors {@code net.minecraft.world.phys.Vec2} — a 2-component float vector.
 * The command system uses it as the (yaw, pitch) rotation carrier of
 * {@code CommandSourceStack.getRotation()} and as the parsed value of
 * {@code Vec2Argument}.
 *
 * CONVENTION (documented, UCBPP CRITICAL-3): for rotations this shim stores
 * x = yaw (degrees), y = pitch (degrees) — the same pairing
 * {@code Entity.rotationYaw/rotationPitch} uses in the 1.8 engine, so the
 * mapping is lossless. WorldCoordinates consumes this pair directly.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-VEC2-001
 */
public class Vec2 {
	public static final Vec2 ZERO = new Vec2(0.0F, 0.0F);
	public static final Vec2 ONE = new Vec2(1.0F, 1.0F);
	public static final Vec2 UNIT_X = new Vec2(1.0F, 0.0F);
	public static final Vec2 UNIT_Y = new Vec2(0.0F, 1.0F);
	public static final Vec2 UNIT_Z = new Vec2(1.0F, 1.0F);
	public static final Vec2 MAX = new Vec2(Float.MAX_VALUE, Float.MAX_VALUE);
	public static final Vec2 MIN = new Vec2(Float.MIN_VALUE, Float.MIN_VALUE);

	public final float x;
	public final float y;

	public Vec2(float x, float y) {
		this.x = x;
		this.y = y;
	}

	public Vec2 scale(float factor) {
		return new Vec2(this.x * factor, this.y * factor);
	}

	public float dot(Vec2 other) {
		return this.x * other.x + this.y * other.y;
	}

	public float length() {
		return (float) Math.sqrt((double) (this.x * this.x + this.y * this.y));
	}

	public float lengthSquared() {
		return this.x * this.x + this.y * this.y;
	}

	public Vec2 normalized() {
		float len = this.length();
		return len < 1.0E-4F ? ZERO : new Vec2(this.x / len, this.y / len);
	}

	public boolean equals(Vec2 other) {
		return this == other || (this.x == other.x && this.y == other.y);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Vec2 && this.equals((Vec2) other);
	}

	@Override
	public int hashCode() {
		return Float.floatToIntBits(this.x) * 31 + Float.floatToIntBits(this.y);
	}

	@Override
	public String toString() {
		return "(" + this.x + ", " + this.y + ")";
	}
}
