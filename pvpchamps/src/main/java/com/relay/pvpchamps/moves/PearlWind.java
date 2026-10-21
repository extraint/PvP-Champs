package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Pearl catch: the bot throws an ender pearl at the opponent, then a wind charge at the landing spot so the pearl
 * and the wind charge both connect. The wind charge launches the bot onto the pearl's landing point.
 */
public final class PearlWind implements Move {
	private int phase, wait;

	@Override public String id() { return "pearl_wind"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.dist < 6 || c.dist > 30) return MoveEngine.reject(id(), "range");
		// Never while airborne: a pearl thrown mid-fall goes down and ruins the mace dive.
		if (!c.bot.onGround() || c.bot.getDeltaMovement().y < -0.1) return MoveEngine.reject(id(), "airborne");
		if (c.bot.isFallFlying() || c.bot.isUsingItem()) return false;
		if (c.bot.getMainHandItem().getItem() == net.minecraft.world.item.Items.MACE) return MoveEngine.reject(id(), "holding_mace");
		if (c.count(Items.ENDER_PEARL) == 0 || c.count(Items.WIND_CHARGE) == 0) return false;
		phase = 0;
		wait = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (!c.bot.onGround() && phase < 2) return false; // left the ground before the pearl went: abort, do not throw it down
		Vec3 landing = c.target.position().add(c.target.getDeltaMovement().x * 6, 0.2, c.target.getDeltaMovement().z * 6);
		switch (phase) {
			case 0 -> {
				if (!c.hold(Items.ENDER_PEARL)) return false;
				float[] a = Ballistics.aim(c.bot.getEyePosition(), landing);
				c.pack.look(a[0], a[1]);
				c.pack.stopMovement();
				if (++wait >= 3) { phase = 1; wait = 0; }
			}
			case 1 -> {
				float[] a = Ballistics.aim(c.bot.getEyePosition(), landing);
				c.pack.look(a[0], a[1]);
				c.useOnce(); // the pearl
				phase = 2;
				wait = 0;
			}
			case 2 -> {
				// Throw the wind charge toward the pearl's landing spot, a few ticks later, so both land together.
				if (++wait < 4) return true;
				if (!c.hold(Items.WIND_CHARGE)) return false;
				c.face(landing.add(0, 1.0, 0));
				c.useOnce();
				phase = 3;
				wait = 0;
			}
			default -> {
				if (++wait >= 2) { c.st.setCooldown(id(), 160); return false; }
			}
		}
		return true;
	}

	@Override public void end(Ctx c) { c.restoreSlot(); }
}
