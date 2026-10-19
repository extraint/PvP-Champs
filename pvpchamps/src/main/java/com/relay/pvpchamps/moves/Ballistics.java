package com.relay.pvpchamps.moves;

import net.minecraft.world.phys.Vec3;

/** Aims thrown items (ender pearls) so they land on a target instead of just pointing at it. */
public final class Ballistics {
	private Ballistics() {}

	public static final double PEARL_SPEED = 1.5, GRAVITY = 0.03, DRAG = 0.99;

	/** yaw/pitch (degrees, Minecraft convention) that makes a projectile fired from {@code from} come down nearest {@code to}. */
	public static float[] aim(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float bestPitch = 0;
		double best = Double.MAX_VALUE;
		for (float pitch = -80; pitch <= 45; pitch += 0.5f) {
			double miss = closestApproach(from, yaw, pitch, to);
			if (miss < best) { best = miss; bestPitch = pitch; }
		}
		return new float[] { yaw, bestPitch };
	}

	private static double closestApproach(Vec3 from, float yaw, float pitch, Vec3 to) {
		double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
		double vx = -Math.sin(yr) * Math.cos(pr) * PEARL_SPEED, vy = -Math.sin(pr) * PEARL_SPEED, vz = Math.cos(yr) * Math.cos(pr) * PEARL_SPEED;
		double x = from.x, y = from.y, z = from.z, best = Double.MAX_VALUE;
		for (int t = 0; t < 120; t++) {
			x += vx; y += vy; z += vz;
			vx *= DRAG; vy = vy * DRAG - GRAVITY; vz *= DRAG;
			double d = (x - to.x) * (x - to.x) + (y - to.y) * (y - to.y) + (z - to.z) * (z - to.z);
			if (d < best) best = d;
			if (y < to.y - 8) break;
		}
		return best;
	}
}
