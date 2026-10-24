package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Spear and mace on the ground: once in range the bot clicks every tick instead of waiting for a charged swing, with
 * constant side-steps, and keeps closing the gap so the spear never stops landing hits.
 */
public final class SpearSpam implements Move {
	private int t, flip;
	private float dir;

	@Override public String id() { return "spear_spam"; }

	private static boolean hasSpear(Ctx c) { return c.find(Items.NETHERITE_SPEAR) >= 0 || c.find(Items.DIAMOND_SPEAR) >= 0; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (!hasSpear(c)) return MoveEngine.reject(id(), "no_spear");
		if (!c.bot.onGround() || c.bot.isUsingItem()) return MoveEngine.reject(id(), "busy");
		if (c.dist > 4.6 || c.dist < 0.6) return MoveEngine.reject(id(), "range");
		if (c.target.getY() > c.bot.getY() + 1.5) return MoveEngine.reject(id(), "target_above");
		if (c.hp() <= 8f) return MoveEngine.reject(id(), "low_hp"); // heal first
		t = 0;
		flip = 0;
		dir = c.st.rng.nextBoolean() ? 1f : -1f;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 60 || !c.target.isAlive() || c.dist > 6.5 || c.hp() <= 8f) return false;
		if (!c.bot.onGround() && t > 2) return false;          // knocked up or launched: the air moves take it
		if (!(c.hold(Items.NETHERITE_SPEAR) || c.hold(Items.DIAMOND_SPEAR))) return false;
		c.face(c.target.getEyePosition().subtract(0, 0.4, 0));
		if (++flip >= 5 + c.st.rng.nextInt(3)) { dir = -dir; flip = 0; }
		c.pack.setStrafing(dir);
		c.run(c.dist > 2.4 ? 1f : 0f, true);
		if (c.dist <= 4.3) c.attackOnce();                      // click every tick: no waiting for the cooldown bar
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.setStrafing(0f);
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 6);
	}
}
