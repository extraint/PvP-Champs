package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Combat Master axe disable: as soon as the opponent raises a shield in reach, swap to an axe and hit it at once,
 * without waiting for the full attack cooldown. The axe disables the shield; the bot then goes back to its sword.
 */
public final class ShieldBreak implements Move {
	private int left;

	@Override public String id() { return "shield_break"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (!c.target.isBlocking()) return MoveEngine.reject(id(), "not_blocking");
		if (c.dist > 3.4) return MoveEngine.reject(id(), "range");
		if (c.find(Items.NETHERITE_AXE) < 0 && c.find(Items.DIAMOND_AXE) < 0) return MoveEngine.reject(id(), "no_axe");
		left = 12;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.dist > 4.5) return false;
		c.face(c.targetEye());
		if (!(c.hold(Items.NETHERITE_AXE) || c.hold(Items.DIAMOND_AXE))) return false;
		// Hit as soon as the axe is in hand: the shield disable does not need a full charge.
		if (c.dist <= 3.2) {
			c.attackOnce();
			return false;
		}
		c.run(1f, true);
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 20);
	}
}
