package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Pros break a shield with an axe first (an axe hit disables it for a few seconds), then drop a mace smash into
 * the open guard. Triggers when the opponent is blocking with a shield, and the bot carries both weapons.
 */
public final class StunSlam implements Move {
	private int left;
	private boolean disabled;

	@Override public String id() { return "stun_slam"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0 || c.dist > 4 || !c.target.isBlocking()) return false;
		if (c.find(Items.NETHERITE_AXE) < 0 && c.find(Items.DIAMOND_AXE) < 0) return false;
		if (c.find(Items.MACE) < 0) return false;
		left = 40;
		disabled = false;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.dist > 5) return false;
		c.face(c.targetEye());
		c.run(c.dist > 2.5 ? 1f : 0f, true);
		if (!disabled) {
			if (c.hold(Items.NETHERITE_AXE) || c.hold(Items.DIAMOND_AXE)) {
				if (c.dist <= 3.1 && c.bot.getAttackStrengthScale(0.5f) >= 0.9f) {
					c.attackOnce();
					disabled = true;
				}
			}
			return true;
		}
		// The shield is down: switch to the mace and smash on the way down.
		if (c.hold(Items.MACE)) {
			boolean falling = !c.bot.onGround() && c.bot.getDeltaMovement().y < 0;
			if (c.dist <= 3.1 && (falling || c.bot.onGround()) && c.bot.getAttackStrengthScale(0.5f) >= 0.9f) {
				c.attackOnce();
				return false;
			}
			if (c.bot.onGround() && c.dist < 4) c.jumpOnce();
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 120);
	}
}
