package com.relay.pvpchamps.moves;

/**
 * The 3-block hit: the opponent is backing off to exactly three blocks. Hit the instant they cross into reach,
 * while they are still moving away, instead of stepping in and trading. Only fires with a full attack charge.
 */
public final class ThreeBlockHit implements Move {
	private int left;

	@Override public String id() { return "three_block_hit"; }

	@Override
	public boolean start(Ctx c) {
		if (c.carriesMace()) return MoveEngine.reject(id(), "mace_kit"); // leave mace fights to Vexbot
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.dist < 2.95 || c.dist > 3.25) return MoveEngine.reject(id(), "range");
		if (c.bot.getAttackStrengthScale(0.5f) < 0.95f) return MoveEngine.reject(id(), "not_charged");
		left = 6;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive()) return false;
		c.face(c.targetEye());
		if (c.dist <= 3.2 && c.bot.getAttackStrengthScale(0.5f) >= 0.95f) {
			c.attackOnce();
			return false;
		}
		return c.dist >= 2.95 && c.dist <= 3.5;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.st.setCooldown(id(), 10);
	}
}
