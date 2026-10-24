package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Spear-mace lunge swap: lunge with a spear, swap to a wind charge or mace while the lunge recovers, and lunge
 * again. The bot keeps this up for several lunges in a couple of seconds, closing the gap from up to 12 blocks.
 */
public final class LungeSwap implements Move {
	private enum Phase { DASH, JAB, SWAP }

	private static final int MAX_LUNGES = 4, MAX_AIR_LUNGES = 6;

	private Phase phase;
	private int t, lunges, airCd, swapTicks;

	@Override public String id() { return "lunge_swap"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.dist < 3.5) return MoveEngine.reject(id(), "too_close");
		if (c.dist > (c.bot.onGround() ? 12 : 18)) return MoveEngine.reject(id(), "too_far");
		if (c.find(Items.NETHERITE_SPEAR) < 0 && c.find(Items.DIAMOND_SPEAR) < 0) return MoveEngine.reject(id(), "no_spear");
		phase = Phase.DASH;
		t = 0;
		lunges = 0;
		airCd = 0;
		swapTicks = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 80 || !c.target.isAlive() || c.dist > 20) return false;
		if (!c.bot.onGround() && c.bot.getY() > c.target.getY() + 1.0) return airLunges(c); // up in the air: keep lunging at them
		if (c.dist > 14) return false;
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

	/**
	 * Airborne above or near the opponent (after a wind charge): lunge toward them again and again, swapping to the mace
	 * between lunges so the next one is ready, and once the bot is above them hold the mace and let mace_smash dive.
	 */
	private boolean airLunges(Ctx c) {
		double horiz = Math.hypot(c.target.getX() - c.bot.getX(), c.target.getZ() - c.bot.getZ());
		double above = c.bot.getY() - c.target.getY();
		c.face(c.target.position().add(0, 1.0, 0));
		c.run(1f, true);
		if (horiz <= 4.5 && above >= 2.5) { c.hold(Items.MACE); return false; } // over them: the mace dive takes it
		if (airCd > 0) airCd--;
		if (swapTicks > 0) { swapTicks--; c.hold(Items.MACE); return true; }  // the swap that readies the next lunge
		if (airCd == 0 && horiz > 3.0 && lunges < MAX_LUNGES + MAX_AIR_LUNGES && (c.hold(Items.NETHERITE_SPEAR) || c.hold(Items.DIAMOND_SPEAR))) {
			c.attackOnce();                                                 // the lunge carries it toward them
			lunges++;
			airCd = 7;
			swapTicks = 2;
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
