package com.relay.pvpchamps.moves;

/**
 * Combat Master melee: circle the opponent, jump and hit on the way down for criticals, and tap shift in the air
 * now and then (about 1 airborne tick in 20) to tune the arc while strafe-critting. After a hit it keeps on the opponent to chain the next one into a combo.
 */
public final class MasterCircle implements Move {
	private int left, flip, noCrit, comboLeft, shiftLeft;
	private float dir;

	@Override public String id() { return "master_circle"; }

	@Override
	public boolean start(Ctx c) {
		if (c.carriesMace()) return MoveEngine.reject(id(), "mace_kit"); // leave mace fights to Vexbot
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.dist > 4.5 || c.dist < 0.6) return MoveEngine.reject(id(), "range");
		if (c.bot.isUsingItem() || c.bot.isFallFlying()) return MoveEngine.reject(id(), "busy");
		if (c.hp() <= 12f) return MoveEngine.reject(id(), "low_hp");
		left = 70;
		flip = 0;
		noCrit = 0;
		comboLeft = 0;
		shiftLeft = 0;
		dir = c.st.rng.nextBoolean() ? 1f : -1f;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.dist > 6.5) return false;
		if (c.hp() <= 10f) return false; // hand control back so Vexbot can heal
		c.face(c.targetEye());
		boolean onGround = c.bot.onGround();
		boolean falling = !onGround && c.bot.getDeltaMovement().y < 0;

		// Circle: hold one direction for a stretch, then swap; now and then a quick swap to break the pattern.
		int stretch = c.st.rng.nextInt(5) == 0 ? 3 : 8 + c.st.rng.nextInt(8);
		if (++flip >= stretch) { dir = -dir; flip = 0; }
		c.pack.setStrafing(dir);

		// Keep the distance right for hitting: close in when far, ease off when on top of them.
		float fwd = c.dist > 2.8 ? 1f : c.dist < 1.6 ? -0.4f : 0.5f;
		if (comboLeft > 0) { comboLeft--; fwd = 1f; } // chase the knockback so the next hit lands
		c.pack.setForward(fwd);
		c.pack.setSprinting(fwd > 0 && !c.bot.isShiftKeyDown());

		// A shift tap in the air is rare: about 1 airborne tick in 20 starts a two-tick shift, then back to unshifted.
		if (!onGround && shiftLeft == 0 && c.st.rng.nextInt(20) == 0) shiftLeft = 2;
		if (shiftLeft > 0) { shiftLeft--; c.pack.setSneaking(true); }
		else c.pack.setSneaking(false);

		// Crit arc: jump when close, hit while falling.
		if (onGround && c.dist < 3.8 && c.st.rng.nextInt(3) != 0) { c.jumpOnce(); noCrit = 0; }
		else if (onGround) noCrit++;

		boolean ready = c.bot.getAttackStrengthScale(0.5f) >= 0.95f;
		if (c.dist <= 3.05 && ready && (falling || noCrit > 6)) {
			c.attackOnce();
			MoveEngine.reject(id(), falling ? "hit_crit" : "hit_ground");
			comboLeft = 6;
			noCrit = 0;
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.setStrafing(0f);
		c.pack.setSneaking(false);
		c.pack.stopMovement();
		c.st.setCooldown(id(), 14); // hand the fight back to Vexbot's own tuned logic (blocking, combo breaks, knockback) between bursts
	}
}
