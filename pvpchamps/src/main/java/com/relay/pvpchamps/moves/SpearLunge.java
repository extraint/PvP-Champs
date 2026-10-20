package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * A netherite spear reaches further than a sword: the pros poke at the edge of range, then step back out before
 * the reply. Triggers when the bot is holding a spear and the opponent is just outside sword range.
 */
public final class SpearLunge implements Move {
	private int left;

	@Override public String id() { return "spear_lunge"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0 || c.dist < 3.2 || c.dist > 6.5) return false;
		if (c.find(Items.NETHERITE_SPEAR) < 0 && c.find(Items.DIAMOND_SPEAR) < 0) return false;
		left = 30;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.dist > 8) return false;
		c.face(c.targetEye());
		if (!(c.hold(Items.NETHERITE_SPEAR) || c.hold(Items.DIAMOND_SPEAR))) return false;
		if (c.dist <= 4.5) {
			c.run(0f, false);
			if (c.bot.getAttackStrengthScale(0.5f) >= 0.9f) {
				c.attackOnce();
				return false; // one poke, then back to the normal fight
			}
		} else {
			c.run(1f, true);
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 60);
	}
}
