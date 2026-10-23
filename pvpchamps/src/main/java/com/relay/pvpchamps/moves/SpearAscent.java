package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Spear and mace from far away: instead of walking over, launch up with a wind charge, lunge with the spear toward
 * the opponent while swapping to another wind charge to keep the height, and arrive almost above them. Then the
 * move ends and the mace dive (mace_smash) takes over as the bot comes down.
 */
public final class SpearAscent implements Move {
	private enum Phase { LAUNCH, AIR }

	private Phase phase;
	private int t, lungeCd, boosts;

	@Override public String id() { return "spear_ascent"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (!c.carriesMace()) return MoveEngine.reject(id(), "no_mace");
		if (c.count(Items.WIND_CHARGE) < 2) return MoveEngine.reject(id(), "no_wind_charges");
		if (c.find(Items.NETHERITE_SPEAR) < 0 && c.find(Items.DIAMOND_SPEAR) < 0) return MoveEngine.reject(id(), "no_spear");
		if (c.dist < 12 || c.dist > 48) return MoveEngine.reject(id(), "range");
		if (!c.bot.onGround() || c.bot.isUsingItem()) return MoveEngine.reject(id(), "busy");
		if (c.target.getY() > c.bot.getY() + 3) return MoveEngine.reject(id(), "target_above");
		phase = Phase.LAUNCH;
		t = 0;
		lungeCd = 0;
		boosts = 0;
		return true;
	}

	private double horizontal(Ctx c) {
		return Math.hypot(c.target.getX() - c.bot.getX(), c.target.getZ() - c.bot.getZ());
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 110 || !c.target.isAlive()) return false;
		double above = c.bot.getY() - c.target.getY();
		switch (phase) {
			case LAUNCH -> {
				if (!c.hold(Items.WIND_CHARGE)) return false;
				c.pack.look(c.bot.getYRot(), 90f);          // straight down at the feet
				if (t == 1) c.jumpOnce();
				if (t == 5) { c.useOnce(); phase = Phase.AIR; t = 0; boosts = 1; }
			}
			case AIR -> {
				if (c.bot.onGround() && t > 8) return false;                       // came down without arriving
				if (horizontal(c) <= 5 && above >= 3.5) return false;             // above them: the mace dive takes it from here
				c.face(c.target.position().add(0, 1.0, 0));
				c.run(1f, true);
				if (lungeCd > 0) lungeCd--;
				// Out of height while still far away: another wind charge underfoot.
				if (c.bot.getDeltaMovement().y < -0.35 && above < 9 && boosts < 3 && c.count(Items.WIND_CHARGE) > 0 && horizontal(c) > 5) {
					if (c.hold(Items.WIND_CHARGE)) {
						c.pack.look(c.bot.getYRot(), 90f);
						c.useOnce();
						boosts++;
						return true;
					}
				}
				if (lungeCd == 0 && horizontal(c) > 6 && (c.hold(Items.NETHERITE_SPEAR) || c.hold(Items.DIAMOND_SPEAR))) {
					c.attackOnce();                                                 // the lunge carries it toward them
					lungeCd = 14;
				}
			}
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 200);
	}
}
