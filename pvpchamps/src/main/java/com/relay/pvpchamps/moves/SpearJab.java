package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Spear & mace on the ground: a spear hits harder the faster the bot is moving, so build speed in a straight run
 * at the opponent, jab at the end of it, then peel away before the counter-hit. Stays out of the mace's way.
 */
public final class SpearJab implements Move {
	private int t;
	private boolean jabbed;

	@Override public String id() { return "spear_jab"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.find(Items.NETHERITE_SPEAR) < 0) return MoveEngine.reject(id(), "no_spear");
		if (!c.bot.onGround() || c.bot.isUsingItem()) return MoveEngine.reject(id(), "busy");
		if (c.dist < 3.5 || c.dist > 9) return MoveEngine.reject(id(), "range");
		if (c.target.getY() > c.bot.getY() + 1.5) return MoveEngine.reject(id(), "target_above");
		t = 0;
		jabbed = false;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (!c.target.isAlive() || ++t > 40 || !c.bot.onGround() && t > 3) return false;
		if (!c.hold(Items.NETHERITE_SPEAR)) return false;
		if (!jabbed) {
			c.face(c.target.getEyePosition().subtract(0, 0.5, 0));
			c.run(1f, true);
			if (c.dist <= 4.2) { c.attackOnce(); jabbed = true; t = 20; }
			return true;
		}
		c.runBack(true); // a short backwards sprint to reset before the counter
		return t < 30;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 50);
	}
}
