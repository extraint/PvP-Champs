package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Pros break contact to reset: run, splash XP bottles at their feet to mend worn armour, eat a golden apple if
 * hurt, then come back. Triggers on worn armour or low health, and only while the opponent isn't on top of them.
 */
public final class MendReset implements Move {
	private enum Phase { RUN, MEND, EAT }

	private Phase phase;
	private int t, splashes;

	@Override public String id() { return "mend_reset"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0 || c.dist < 7 || c.dist > 45) return false;
		boolean wornArmour = c.armourFraction() < 0.5 && c.count(Items.EXPERIENCE_BOTTLE) > 0;
		boolean hurt = c.hp() <= 8f && c.target.getHealth() > c.bot.getHealth() + 3f
			&& (c.count(Items.GOLDEN_APPLE) > 0 || c.count(Items.EXPERIENCE_BOTTLE) > 0);
		if (!wornArmour && !hurt) return false;
		phase = Phase.RUN;
		t = 0;
		splashes = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		t++;
		if (c.dist < 6 && phase != Phase.RUN) { c.st.setCooldown(id(), 300); return false; } // caught: go back to fighting
		switch (phase) {
			case RUN -> {
				Vec3 away = c.bot.position().subtract(c.target.position()).normalize().scale(8);
				c.face(c.bot.getEyePosition().add(away.x, 0, away.z));
				c.run(1f, true);
				if (c.dist > 20 || t > 40) { c.pack.stopMovement(); phase = c.armourFraction() < 0.7 && c.count(Items.EXPERIENCE_BOTTLE) > 0 ? Phase.MEND : Phase.EAT; t = 0; }
			}
			case MEND -> {
				if (!c.hold(Items.EXPERIENCE_BOTTLE)) { phase = Phase.EAT; t = 0; return true; }
				c.pack.look(c.bot.getYRot(), 90f); // straight down at their own feet
				if (t % 6 == 0) { c.useOnce(); splashes++; }
				if (splashes >= 6 || c.armourFraction() > 0.92) { phase = c.hp() < 14f ? Phase.EAT : null; t = 0; if (phase == null) { c.st.setCooldown(id(), 500); return false; } }
			}
			case EAT -> {
				if (c.count(Items.GOLDEN_APPLE) == 0 || !c.hold(Items.GOLDEN_APPLE)) { c.st.setCooldown(id(), 500); return false; }
				if (t == 1) c.pack.start(vexbot.fakeplayer.bot.BotPlayerActionPack.ActionType.USE, vexbot.fakeplayer.bot.BotPlayerActionPack.Action.continuous());
				if (t > 36) { c.pack.stop(vexbot.fakeplayer.bot.BotPlayerActionPack.ActionType.USE); c.st.setCooldown(id(), 500); return false; }
			}
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stop(vexbot.fakeplayer.bot.BotPlayerActionPack.ActionType.USE);
		c.pack.stopMovement();
		c.restoreSlot();
	}
}
