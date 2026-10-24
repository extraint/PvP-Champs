package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Diamond SMP advice from a tier-one guide: start every fight with the axe, because there is no downside as long as
 * you can swap to the sword afterwards. The first swing may break a shield or land a heavy hit; then the bot goes
 * back to the sword and Vexbot takes over. It runs once per fight.
 */
public final class AxeOpener implements Move {
	private int t;
	private boolean swung;

	@Override public String id() { return "axe_opener"; }

	private static boolean hasAxe(Ctx c) { return c.find(Items.NETHERITE_AXE) >= 0 || c.find(Items.DIAMOND_AXE) >= 0; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (!hasAxe(c)) return MoveEngine.reject(id(), "no_axe");
		if (c.carriesMace()) return MoveEngine.reject(id(), "mace_kit");
		if (c.dist > 4.6 || c.dist < 1.0) return MoveEngine.reject(id(), "range");
		if (c.bot.hurtTime > 0) return MoveEngine.reject(id(), "being_hit");
		t = 0;
		swung = false;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 50 || !c.target.isAlive()) return false;
		if (!(c.hold(Items.NETHERITE_AXE) || c.hold(Items.DIAMOND_AXE))) return false;
		c.face(c.target.getEyePosition().subtract(0, 0.3, 0));
		c.run(c.dist > 2.8 ? 1f : 0f, true);
		if (!swung && c.dist <= 3.1 && c.bot.getAttackStrengthScale(0.5f) >= 0.9f) {
			c.attackOnce();
			swung = true;
			t = 40; // a moment to let the hit land, then back to the sword
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		if (!(c.hold(Items.NETHERITE_SWORD) || c.hold(Items.DIAMOND_SWORD))) c.restoreSlot(); // swap back to the sword
		c.st.setCooldown(id(), 1200); // once per fight
	}
}
