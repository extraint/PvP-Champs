package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Defensive players survive by retreating to eat or drink and regenerating back. Top-tier bots punish that: the moment
 * the opponent starts eating a golden apple, or runs away at low health, sprint-jump onto them and hit on every
 * full cooldown, so a heal costs them hearts instead of restoring them.
 */
public final class HealPunish implements Move {
	private int left, reset, idle;

	@Override public String id() { return "heal_punish"; }

	private static boolean eating(Ctx c) {
		if (!c.target.isUsingItem()) return false;
		var item = c.target.getUseItem().getItem();
		return item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE;
	}

	/** Moving away fast while hurt: on the way to somewhere safe to heal. */
	private static boolean retreating(Ctx c) {
		if (c.target.getHealth() > 12f || c.dist < 2.5) return false;
		var away = c.target.position().subtract(c.bot.position()).multiply(1, 0, 1);
		if (away.lengthSqr() < 1e-4) return false;
		return c.target.getDeltaMovement().multiply(1, 0, 1).dot(away.normalize()) > 0.1;
	}

	@Override
	public boolean start(Ctx c) {
		if (c.carriesMace()) return MoveEngine.reject(id(), "mace_kit"); // leave mace fights to Vexbot
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (ReachCombo.skillOf(c) < 0.55) return MoveEngine.reject(id(), "tier");
		if (c.dist < 1.0 || c.dist > 14) return MoveEngine.reject(id(), "range");
		if (c.bot.isUsingItem() || c.bot.isFallFlying()) return MoveEngine.reject(id(), "busy");
		if (c.hp() <= 8f) return MoveEngine.reject(id(), "low_hp"); // heal first
		if (!eating(c) && !retreating(c)) return MoveEngine.reject(id(), "not_healing");
		left = 50;
		reset = 0;
		idle = 0;
		MoveEngine.reject(id(), eating(c) ? "start_eating" : "start_retreating");
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.dist > 16) return false;
		if (c.hp() <= 8f) return false; // hand control back so the bot can heal
		idle = (eating(c) || retreating(c)) ? 0 : idle + 1;
		if (idle > 12) return false; // they stopped healing: back to the normal fight
		c.face(c.targetEye());
		if (reset > 0) { reset--; c.run(0f, false); } else c.run(1f, true);
		if (c.bot.onGround() && c.dist > 4.0 && c.st.rng.nextInt(3) == 0) c.jumpOnce(); // bunny-hop to keep the sprint speed up
		if (c.dist <= 3.05 && c.bot.getAttackStrengthScale(0.5f) >= 0.95f) { c.attackOnce(); reset = 2; }
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.st.setCooldown(id(), 16);
	}
}
