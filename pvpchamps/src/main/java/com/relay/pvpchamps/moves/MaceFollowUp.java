package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * The moment a mace smash lands, the bot reacts with no delay: it drops the mace for the sword (or the axe, if the
 * opponent is blocking) and hits straight away while they are still reeling, chasing to stay in reach.
 */
public final class MaceFollowUp implements Move {
	private int t;

	@Override public String id() { return "mace_followup"; }

	@Override public boolean start(Ctx c) { t = 0; return true; } // only ever started through forceMove

	@Override
	public boolean tick(Ctx c) {
		if (++t > 30 || !c.target.isAlive() || c.dist > 6) return false;
		var weapon = c.target.isBlocking() && c.find(Items.NETHERITE_AXE) >= 0 ? Items.NETHERITE_AXE
			: c.find(Items.NETHERITE_SWORD) >= 0 ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD;
		if (!c.hold(weapon)) return false;
		c.face(c.target.getEyePosition().subtract(0, 0.3, 0));
		c.run(c.dist > 2.8 ? 1f : 0f, true);
		if (c.dist <= 3.2 && c.bot.getAttackStrengthScale(0.5f) >= 0.9f) {
			c.attackOnce();
			if (weapon == Items.NETHERITE_SWORD || weapon == Items.DIAMOND_SWORD) return t < 12; // a short flurry, then back to Vexbot
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
	}
}
