package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Spear-mace lunge swap: lunge with a spear, swap to a wind charge or mace while the lunge recovers, and lunge
 * again. The bot keeps this up for several lunges in a couple of seconds, closing the gap from up to 12 blocks.
 */
public final class LungeSwap implements Move {
	private enum Phase { DASH, JAB, SWAP }

	private static final int MAX_LUNGES = 4;

	private Phase phase;
	private int t, lunges;

	@Override public String id() { return "lunge_swap"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.dist < 3.5) return MoveEngine.reject(id(), "too_close");
		if (c.dist > 12) return MoveEngine.reject(id(), "too_far");
		if (c.find(Items.NETHERITE_SPEAR) < 0 && c.find(Items.DIAMOND_SPEAR) < 0) return MoveEngine.reject(id(), "no_spear");
		phase = Phase.DASH;
		t = 0;
		lunges = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 80 || !c.target.isAlive() || c.dist > 14) return false;
		c.face(c.targetEye());
		switch (phase) {
			case DASH -> {
				if (!(c.hold(Items.NETHERITE_SPEAR) || c.hold(Items.DIAMOND_SPEAR))) return false;
				c.run(1f, true);
				// Jump so the lunge carries the bot forward, then jab once the target is in reach.
				if (c.bot.onGround() && c.dist > 4) c.jumpOnce();
				if (c.dist <= 4.0) {
					c.attackOnce();
					lunges++;
					phase = Phase.SWAP;
					t = 0;
				}
			}
			case SWAP -> {
				// Swap to the mace or a wind charge during the lunge recovery, then go again while in range.
				if (lunges >= MAX_LUNGES) return false;
				if (c.hold(Items.WIND_CHARGE) || c.hold(Items.MACE)) {
					phase = c.dist <= 4.0 ? Phase.JAB : Phase.DASH;
					t = 0;
				} else return false;
			}
			case JAB -> {
				if (c.bot.getAttackStrengthScale(0.5f) >= 0.9f) {
					c.attackOnce();
					lunges++;
					phase = Phase.SWAP;
					t = 0;
				} else if (t > 8) return false;
			}
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
