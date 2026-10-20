package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Spear-mace lunge swap: lunge with a spear while falling, then swap to the mace or a wind charge for the next
 * hit so the cooldown of the swap overlaps the fall. Triggers only while the bot is falling toward the target.
 */
public final class LungeSwap implements Move {
	private enum Phase { LUNGE, SWAP, FOLLOW }

	private Phase phase;
	private int t;

	@Override public String id() { return "lunge_swap"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0 || c.bot.onGround() || c.bot.getDeltaMovement().y >= 0) return false;
		if (c.dist < 2.8 || c.dist > 5.5) return false;
		if (c.find(Items.NETHERITE_SPEAR) < 0 && c.find(Items.DIAMOND_SPEAR) < 0) return false;
		phase = Phase.LUNGE;
		t = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 30 || !c.target.isAlive()) return false;
		c.face(c.targetEye());
		switch (phase) {
			case LUNGE -> {
				if (!(c.hold(Items.NETHERITE_SPEAR) || c.hold(Items.DIAMOND_SPEAR))) return false;
				c.run(1f, true);
				if (c.dist <= 4.0) {
					c.attackOnce();
					phase = Phase.SWAP;
					t = 0;
				}
			}
			case SWAP -> {
				// Swap right after the lunge: the wind charge (no attack damage) has the shortest cooldown.
				if (c.hold(Items.WIND_CHARGE) || c.hold(Items.MACE)) {
					phase = Phase.FOLLOW;
					t = 0;
				} else return false;
			}
			case FOLLOW -> {
				if (c.dist <= 3.1 && c.bot.getAttackStrengthScale(0.5f) >= 0.9f) {
					c.attackOnce();
					return false;
				}
				if (t > 10) return false;
			}
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 40);
	}
}
