package com.relay.pvpchamps.moves;

/**
 * Combat Master never stands around: if the bot has barely moved for about a second while the opponent is within
 * reach of a quick rush, run at them and attack until the fight is back on.
 */
public final class AntiStall implements Move {
	private int left;

	@Override public String id() { return "anti_stall"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.stillTicks < 20) return false;
		if (c.dist < 2.0 || c.dist > 24) return MoveEngine.reject(id(), "range");
		if (c.bot.isUsingItem() || c.bot.isFallFlying()) return MoveEngine.reject(id(), "busy");
		if (c.hp() <= 12f) return MoveEngine.reject(id(), "low_hp"); // Vexbot heals and pops totems at low health
		left = 30;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.dist > 26) return false;
		if (c.hp() <= 10f) return false; // hand control back so Vexbot can heal
		c.face(c.targetEye());
		c.run(1f, true);
		if (c.bot.onGround() && c.dist > 5 && c.st.rng.nextInt(10) == 0) c.jumpOnce();
		if (c.dist <= 3.05 && c.bot.getAttackStrengthScale(0.5f) >= 0.95f) c.attackOnce();
		return c.dist > 2.2 || left > 10;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.st.stillTicks = 0;
	}
}
