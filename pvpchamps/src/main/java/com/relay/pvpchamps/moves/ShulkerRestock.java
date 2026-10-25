package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;

/**
 * Crystal kit: when the supplies run low (crystals, obsidian, golden apples or pearls) and the opponent is not on top
 * of the bot, place the restock shulker on the floor. The match removes the block the moment it is placed and refills
 * the kit, so this just has to put the box down.
 */
public final class ShulkerRestock implements Move {
	private int t;

	@Override public String id() { return "shulker_restock"; }

	private static int crystals(Ctx c) {
		return c.count(Items.END_CRYSTAL) + (c.st.stash != null ? c.st.stash.getCount() : 0); // CrystalGate may hold the stack aside
	}

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.find(Items.SHULKER_BOX) < 0) return MoveEngine.reject(id(), "no_shulker");
		// Only the Crystal kit has the empty restock shulker; other kits' shulkers hold real items and are Vexbot's to place.
		if (c.st.stash == null && c.count(Items.END_CRYSTAL) == 0 && c.count(Items.RESPAWN_ANCHOR) == 0 && c.count(Items.GLOWSTONE) == 0) return MoveEngine.reject(id(), "not_crystal_kit");
		if (!c.bot.onGround() || c.bot.isFallFlying()) return MoveEngine.reject(id(), "airborne");
		boolean low = crystals(c) <= 24 || c.count(Items.OBSIDIAN) <= 16 || c.count(Items.GOLDEN_APPLE) <= 16 || c.count(Items.ENDER_PEARL) <= 16
			|| c.count(Items.TIPPED_ARROW) <= 32;
		if (!low) return MoveEngine.reject(id(), "stocked");
		if (c.dist < 5.0 && c.hp() > 6f) return MoveEngine.reject(id(), "opponent_close"); // a free moment, not mid-trade
		t = 0;
		MoveEngine.reject(id(), "start");
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 14 || !c.target.isAlive()) return false;
		if (!c.hold(Items.SHULKER_BOX)) return false;
		c.pack.look(c.bot.getYRot(), 62f);              // down at the floor just ahead
		if (t == 3) c.useOnce();                         // place it; the match takes it from here
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 60);
	}
}
