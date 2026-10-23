package com.relay.pvpchamps.moves;

/**
 * The reach combo from PvP videos: hit at the very edge of reach (about 3 blocks), then move so the opponent cannot
 * hit back, and repeat. The bot holds a ring of 2.7 to 3.05 blocks: closer than that it backs off, further and it
 * sprints in. Each hit is followed by a w-tap (sprint dropped for a tick) so the knockback is full, and the strafe
 * side swaps so the opponent's aim has to keep chasing. The combo ends when the opponent lands a hit, raises a
 * shield, or after four hits.
 */
public final class ReachCombo implements Move {
	private int t, hits, tapLeft, lastHurt;
	private float dir;

	@Override public String id() { return "reach_combo"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.carriesMace()) return MoveEngine.reject(id(), "mace_kit");
		if (c.dist < 2.6 || c.dist > 3.3) return MoveEngine.reject(id(), "range");
		if (c.target.isBlocking()) return MoveEngine.reject(id(), "shield_up");
		if (c.bot.getAttackStrengthScale(0.5f) < 0.9f) return MoveEngine.reject(id(), "not_charged");
		if (c.bot.hurtTime > 0) return MoveEngine.reject(id(), "being_hit");
		t = 0;
		hits = 0;
		tapLeft = 0;
		lastHurt = 0;
		dir = c.st.rng.nextBoolean() ? 1f : -1f;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 80 || hits >= 4 || !c.target.isAlive() || c.dist > 6.5) return false;
		if (c.bot.hurtTime > 8 || c.target.isBlocking()) return false; // they got a hit in, or they blocked: the combo is over
		c.face(c.target.getEyePosition().subtract(0, 0.3, 0));
		// Keep to the ring: in when too far, back off when too close, strafe either way.
		float fwd = c.dist > 3.05 ? 1f : c.dist < 2.7 ? -1f : 0.2f;
		boolean tap = tapLeft > 0;
		if (tap) tapLeft--;
		c.run(fwd, !tap && fwd > 0);
		c.pack.setStrafing(dir * 0.8f);
		boolean ready = c.bot.getAttackStrengthScale(0.5f) >= 0.95f;
		if (ready && c.dist <= 3.0 && c.dist >= 2.0) {
			c.attackOnce();
			hits++;
			tapLeft = 2; // w-tap: drop the sprint so the next hit launches them
			dir = -dir;  // swap the strafe side after every hit
		}
		if (c.bot.onGround() && c.dist > 3.4 && c.st.rng.nextInt(6) == 0) c.jumpOnce();
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.st.setCooldown(id(), 10);
	}
}
