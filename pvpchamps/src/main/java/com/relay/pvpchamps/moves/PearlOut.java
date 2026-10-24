package com.relay.pvpchamps.moves;

import com.relay.pvpchamps.MatchManager;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Crystal and anchor fights dig holes. A bot that ends up several blocks below the arena floor throws an ender pearl up
 * and out toward the opponent's side to get back onto the floor, and tries again if the first one is not enough.
 */
public final class PearlOut implements Move {
	private int t;

	@Override public String id() { return "pearl_out"; }

	@Override
	public boolean start(Ctx c) {
		Vec3 o = MatchManager.origin();
		if (o == null) return MoveEngine.reject(id(), "no_origin");
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.bot.getY() > o.y - 4.5) return MoveEngine.reject(id(), "not_in_hole"); // a blast crater is not a hole
		if (!c.bot.onGround()) return MoveEngine.reject(id(), "busy"); // a raised shield counts as using an item, so that is not a reason to stay in the hole
		if (c.find(Items.ENDER_PEARL) < 0) return MoveEngine.reject(id(), "no_pearl");
		t = 0;
		MoveEngine.reject(id(), "start");
		return true;
	}

	private float yaw, pitch;

	/**
	 * Flies a pearl through the hole's blocks for each throw angle and direction (speed 1.5, gravity 0.03, drag 0.99) and
	 * keeps the shallowest throw that comes down on the floor and not on the wall of the hole.
	 */
	private void pickThrow(Ctx c, float baseYaw, double floorY) {
		var level = c.bot.level();
		float[] yawOffsets = {0f, 40f, -40f, 80f, -80f, 120f, -120f, 160f, 180f};
		for (float pit = -40f; pit >= -89f; pit -= 3f) {
			for (float off : yawOffsets) {
				double ry = Math.toRadians(baseYaw + off), rp = Math.toRadians(pit);
				double vx = -Math.sin(ry) * Math.cos(rp) * 1.5, vy = -Math.sin(rp) * 1.5, vz = Math.cos(ry) * Math.cos(rp) * 1.5;
				double x = c.bot.getX(), y = c.bot.getEyeY(), z = c.bot.getZ();
				for (int i = 0; i < 140; i++) {
					x += vx; y += vy; z += vz;
					vx *= 0.99; vy = vy * 0.99 - 0.03; vz *= 0.99;
					var bp = net.minecraft.core.BlockPos.containing(x, y, z);
					if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
						if (y >= floorY - 0.9 && vy < 0) { yaw = baseYaw + off; pitch = pit; return; } // lands on the floor
						break; // hit the side of the hole
					}
				}
			}
		}
		yaw = baseYaw;
		pitch = -86f; // nothing clears: nearly straight up, the best chance there is
	}

	@Override
	public boolean tick(Ctx c) {
		Vec3 o = MatchManager.origin();
		if (o == null || ++t > 30) return false;
		if (!c.hold(Items.ENDER_PEARL)) return false;
		if (t == 1) {
			Vec3 to = c.target.position().subtract(c.bot.position()).multiply(1, 0, 1);
			if (to.lengthSqr() < 1.0) to = o.subtract(c.bot.position()).multiply(1, 0, 1);
			Vec3 dir = to.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : to.normalize();
			pickThrow(c, (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), o.y);
		}
		c.pack.look(yaw, pitch);
		if (t == 3) c.useOnce();
		return t < 14;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 100);
	}
}
