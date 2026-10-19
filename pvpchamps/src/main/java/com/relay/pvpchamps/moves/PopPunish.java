package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * A fresh totem pop leaves the opponent at one heart with a short absorption shield. The pros commit hard
 * right then: sprint in, jump, and hit on the way down for criticals.
 */
public final class PopPunish implements Move {
	private int left, sinceJump;

	@Override public String id() { return "pop_punish"; }

	@Override
	public boolean start(Ctx c) {
		boolean popped = c.target.getAbsorptionAmount() >= 7.5f && c.st.lastTargetAbsorb < 1f && c.target.getHealth() <= 3f;
		if (!popped || c.dist > 14) return false;
		var held = c.bot.getMainHandItem().getItem();
		if (held == Items.MACE || held == Items.TRIDENT) return false; // the mace controller owns those fights
		left = 50;
		sinceJump = 0;
		return true;
	}

	/** The bait-retreat counter reuses this for a short, sharper window. */
	public void begin(int ticks) { left = ticks; sinceJump = 0; }

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.dist > 16) return false;
		c.face(c.targetEye());
		c.run(1f, true);
		sinceJump++;
		boolean falling = !c.bot.onGround() && c.bot.getDeltaMovement().y < 0;
		if (c.bot.onGround() && c.dist < 5 && sinceJump > 6) { c.jumpOnce(); sinceJump = 0; }
		if (c.dist <= 3.1 && c.bot.getAttackStrengthScale(0.5f) >= 0.9f && (falling || sinceJump > 12)) c.attackOnce();
		return true;
	}

	@Override public void end(Ctx c) { c.pack.stopMovement(); }
}
