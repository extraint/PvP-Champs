package com.relay.pvpchamps.moves;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Pros wall off with blocks to separate themselves from the opponent: when the enemy closes in while the bot
 * is hurt, place a block on the floor between them, step back behind it, and give the wall time to hold.
 */
public final class WallOff implements Move {
	private int t, placed;

	@Override public String id() { return "wall_off"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0 || c.dist < 2.5 || c.dist > 5.5) return false;
		if (c.hp() > 10f || c.target.getHealth() < c.bot.getHealth()) return false;
		if (blockSlot(c) < 0) return false;
		t = 0;
		placed = 0;
		return true;
	}

	/** Hotbar or inventory slot with a solid block to build with, or -1. */
	private int blockSlot(Ctx c) {
		var inv = c.bot.getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() instanceof BlockItem && s.getItem() != Items.TNT && s.getItem() != Items.OAK_LOG) return i;
		}
		return -1;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 40 || placed >= 2 || !c.target.isAlive()) return false;
		// Aim at the floor one block ahead of the bot, toward the opponent, and place the block there.
		Vec3 toward = c.target.position().subtract(c.bot.position());
		Vec3 spot = c.bot.position().add(toward.normalize().scale(1.5));
		c.face(new Vec3(spot.x, c.bot.getY() - 0.5, spot.z));
		if (!c.hold(c.bot.getInventory().getItem(blockSlot(c)).getItem())) return false;
		if (t % 6 == 0) {
			c.useOnce();
			placed++;
		}
		c.run(-1f, false); // back away from the new wall
		return placed < 2;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 200);
	}
}
