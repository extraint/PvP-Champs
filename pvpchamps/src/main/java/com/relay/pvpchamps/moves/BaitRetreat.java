package com.relay.pvpchamps.moves;

/**
 * Footwork: back off a couple of blocks as if disengaging, then step straight back in as the opponent follows,
 * handing the next swing to {@link PopPunish}'s sprint-and-crit routine for a moment.
 */
public final class BaitRetreat implements Move {
	private int left;
	private float strafe;
	private final PopPunish counter;

	public BaitRetreat(PopPunish counter) { this.counter = counter; }

	@Override public String id() { return "bait_retreat"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0 || c.dist < 2.0 || c.dist > 4.5 || c.hp() < 10f) return false;
		var held = c.bot.getMainHandItem().getItem();
		if (held == net.minecraft.world.item.Items.MACE) return false;
		if (c.st.rng.nextDouble() > 0.18) return false;
		left = 12 + c.st.rng.nextInt(5);
		strafe = c.st.rng.nextBoolean() ? 1f : -1f;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || c.dist > 7) {
			c.st.setCooldown(id(), 120 + c.st.rng.nextInt(80));
			counter.begin(22);
			c.st.forceMove(counter);
			return false;
		}
		c.face(c.targetEye());
		c.run(-1f, false);
		c.pack.setStrafing(strafe);
		return true;
	}

	@Override public void end(Ctx c) { c.pack.stopMovement(); }
}
