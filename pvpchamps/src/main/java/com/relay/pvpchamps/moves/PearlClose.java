package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** When the fight is spread out, pearl onto the opponent instead of jogging over: aimed with a real arc. */
public final class PearlClose implements Move {
	private static long lastThrow = -1000;
	private static long clock;
	private int phase, wait;

	@Override public String id() { return "pearl_close"; }

	@Override
	public boolean start(Ctx c) {
		clock++;
		if (clock - lastThrow < 400) return false; // one pearl at a time for the whole fight: nobody answers a pearl with another
		if (c.dist < 18 || c.dist > 48 || c.st.cooldown(id()) > 0) return false;
		if (!c.bot.onGround() || c.bot.isFallFlying() || c.bot.isUsingItem() || c.count(Items.ENDER_PEARL) == 0) return false;
		if (c.st.rng.nextDouble() > 0.3) return false;
		// Aggro pearl only to secure a pop or a kill, or with a big health lead to keep the pressure (HT2 guide).
		if (!(c.target.getHealth() <= 8f || c.hp() - c.target.getHealth() >= 8f)) return false;
		phase = 0;
		wait = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		Vec3 vel = c.target.getDeltaMovement();
		// Land where they will be when the pearl arrives: lead by the flight time, not a fixed guess.
		double flight = Math.max(1, Math.min(40, c.dist / Ballistics.PEARL_SPEED));
		Vec3 land = c.target.position().add(vel.x * flight * 0.5, 0.2, vel.z * flight * 0.5);
		Vec3 back = c.bot.position().subtract(land).multiply(1, 0, 1);
		Vec3 aimPoint = back.lengthSqr() > 1 ? land.add(back.normalize().scale(2.5)) : land; // stop short of them, never overshoot
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
				lastThrow = clock;
				phase = 2;
				wait = 0;
			}
			default -> {
				if (++wait >= 2) { c.st.setCooldown(id(), 1200); return false; }
			}
		}
		return true;
	}

	@Override public void end(Ctx c) { c.restoreSlot(); }
}
