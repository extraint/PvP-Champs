package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** When the fight is spread out, pearl onto the opponent instead of jogging over: aimed with a real arc. */
public final class PearlClose implements Move {
	private int phase, wait;

	@Override public String id() { return "pearl_close"; }

	@Override
	public boolean start(Ctx c) {
		if (c.dist < 18 || c.dist > 48 || c.st.cooldown(id()) > 0) return false;
		if (!c.bot.onGround() || c.bot.isFallFlying() || c.bot.isUsingItem() || c.count(Items.ENDER_PEARL) == 0) return false;
		if (c.st.rng.nextDouble() > 0.4) return false;
		phase = 0;
		wait = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		Vec3 vel = c.target.getDeltaMovement();
		// Land just ahead of where they are heading, on the ground rather than on their head.
		Vec3 aimPoint = c.target.position().add(vel.x * 6, 0.2, vel.z * 6);
		switch (phase) {
			case 0 -> {
				if (!c.hold(Items.ENDER_PEARL)) return false;
				float[] a = Ballistics.aim(c.bot.getEyePosition(), aimPoint);
				c.pack.look(a[0], a[1]);
				c.pack.stopMovement();
				if (++wait >= 3) { phase = 1; wait = 0; }
			}
			case 1 -> {
				float[] a = Ballistics.aim(c.bot.getEyePosition(), aimPoint);
				c.pack.look(a[0], a[1]);
				c.useOnce();
				phase = 2;
				wait = 0;
			}
			default -> {
				if (++wait >= 2) { c.st.setCooldown(id(), 200); return false; }
			}
		}
		return true;
	}

	@Override public void end(Ctx c) { c.restoreSlot(); }
}
