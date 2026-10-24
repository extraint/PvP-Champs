package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Axe stun, then the slam: when the opponent has their shield up and the bot carries an axe, a mace and wind charges,
 * swing the axe to knock the shield down, swap to a wind charge and launch straight away, then hold the mace over them
 * so the dive (mace_smash) lands while the shield is still disabled.
 */
public final class StunSlam implements Move {
	private enum Phase { AXE, LAUNCH, RISE }

	private Phase phase;
	private int t;

	@Override public String id() { return "stun_slam"; }

	private static boolean hasAxe(Ctx c) { return c.find(Items.NETHERITE_AXE) >= 0 || c.find(Items.DIAMOND_AXE) >= 0; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (!c.carriesMace()) return MoveEngine.reject(id(), "no_mace");
		if (!hasAxe(c)) return MoveEngine.reject(id(), "no_axe");
		if (c.count(Items.WIND_CHARGE) < 1) return MoveEngine.reject(id(), "no_wind_charge");
		if (!c.target.isBlocking()) return MoveEngine.reject(id(), "not_blocking");
		if (!c.bot.onGround() || c.bot.isUsingItem()) return MoveEngine.reject(id(), "busy");
		if (c.dist > 5.5) return MoveEngine.reject(id(), "range");
		if (c.hp() <= 8f) return MoveEngine.reject(id(), "low_hp");
		phase = Phase.AXE;
		t = 0;
		return true;
	}

	private double horizontal(Ctx c) { return Math.hypot(c.target.getX() - c.bot.getX(), c.target.getZ() - c.bot.getZ()); }

	@Override
	public boolean tick(Ctx c) {
		if (++t > 90 || !c.target.isAlive()) return false;
		switch (phase) {
			case AXE -> {
				if (!(c.hold(Items.NETHERITE_AXE) || c.hold(Items.DIAMOND_AXE))) return false;
				c.face(c.target.getEyePosition().subtract(0, 0.3, 0));
				c.run(c.dist > 2.8 ? 1f : 0f, true);
				if (c.dist <= 3.1 && c.bot.getAttackStrengthScale(0.5f) >= 0.9f) {
					c.attackOnce();                     // the shield goes down for a few seconds
					phase = Phase.LAUNCH;
					t = 0;
				} else if (t > 30) return false;
			}
			case LAUNCH -> {
				if (!c.hold(Items.WIND_CHARGE)) return false;
				c.pack.look(c.bot.getYRot(), 90f);      // straight down at the feet
				if (t == 1) c.jumpOnce();
				if (t == 4) { c.useOnce(); phase = Phase.RISE; t = 0; }
			}
			case RISE -> {
				if (c.bot.onGround() && t > 6) return false;
				c.hold(Items.MACE);                      // mace in hand for the dive
				c.face(c.target.position().add(0, 1.0, 0));
				c.pack.setForward(horizontal(c) > 1.2 ? 1f : 0f);
				c.pack.setSprinting(horizontal(c) > 1.2);
				// Falling over them: hand over to the mace smash, which needs a real fall behind the swing.
				if (c.bot.getDeltaMovement().y < -0.25 && c.bot.fallDistance >= 1.0) return false;
			}
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.st.setCooldown(id(), 80);
	}
}
