package com.relay.pvpchamps.moves;

/**
 * Combat Master melee: stay on the opponent with constant side-steps and hit the instant the cooldown is full,
 * instead of standing in front of them. Alternates direction every few ticks and keeps closing the gap.
 */
public final class StrafePressure implements Move {
	private int left, flip, reset;
	private float dir;

	@Override public String id() { return "strafe_pressure"; }

	@Override
	public boolean start(Ctx c) {
		if (c.carriesMace()) return MoveEngine.reject(id(), "mace_kit"); // leave mace fights to Vexbot
		if (c.dist > 4.2 || c.dist < 0.8) return MoveEngine.reject(id(), "range");
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.bot.isUsingItem() || c.bot.isFallFlying()) return MoveEngine.reject(id(), "busy");
		if (c.hp() <= 12f) return MoveEngine.reject(id(), "low_hp"); // Vexbot heals and pops totems at low health
		left = 40;
		flip = 0;
		dir = c.st.rng.nextBoolean() ? 1f : -1f;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.dist > 5.5) return false;
		if (c.hp() <= 10f) return false; // hand control back so Vexbot can heal
		c.face(c.targetEye());
		if (++flip >= 3 + c.st.rng.nextInt(3)) { dir = -dir; flip = 0; }
		c.pack.setStrafing(dir);
		if (reset > 0) { // sprint reset: drop sprint for two ticks after a hit so the next hit gets its knockback
			reset--;
			c.run(0f, false);
		} else c.run(c.dist > 2.6 ? 1f : 0f, true);
		// Crit window: jump, then hit while falling. Chance is high so most hits land as crits.
		boolean falling = !c.bot.onGround() && c.bot.getDeltaMovement().y < 0;
		if (c.bot.onGround() && c.dist < 3.3 && c.st.rng.nextInt(3) != 0) c.jumpOnce();
		if (c.dist <= 3.05 && c.bot.getAttackStrengthScale(0.5f) >= 0.95f && (falling || c.bot.onGround())) {
			c.attackOnce();
			MoveEngine.reject(id(), falling ? "hit_crit" : "hit_ground");
			reset = 2;
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.setStrafing(0f);
		c.pack.stopMovement();
		c.st.setCooldown(id(), 2);
	}
}
