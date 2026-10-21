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
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.dist > 8) return MoveEngine.reject(id(), "too_far");
		if (c.hp() > 16f) return MoveEngine.reject(id(), "healthy");
		if (c.target.getHealth() < c.bot.getHealth()) return MoveEngine.reject(id(), "winning");
		if (blockSlot(c) < 0) return MoveEngine.reject(id(), "no_block");
		t = 0;
		placed = 0;
		return true;
	}

	/** Hotbar or inventory slot with a solid block to build with, or -1. */
	private int blockSlot(Ctx c) {
		var inv = c.bot.getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() instanceof BlockItem bi && s.getItem() != Items.TNT && !(bi.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock)
				&& bi.getBlock().defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)) return i;
		}
		return -1;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 60 || placed >= 5 || !c.target.isAlive()) return false;
		if (placed == 0 && c.dist < 2.5) {
			// Too close to wall off: back away first so the wall can go between the two of them.
			c.face(c.targetEye());
			c.run(-1f, false);
			return t <= 60;
		}
		// Aim at the floor one block ahead of the bot, toward the opponent, and place the block there.
		Vec3 toward = c.target.position().subtract(c.bot.position());
		Vec3 spot = c.bot.position().add(toward.normalize().scale(1.5 + placed * 0.5));
		c.face(new Vec3(spot.x, c.bot.getY() - 0.5, spot.z));
		if (!c.hold(c.bot.getInventory().getItem(blockSlot(c)).getItem())) return false;
		if (t % 4 == 0) {
			c.useOnce();
			placed++;
		}
		c.run(-1f, false); // back away from the new wall
		return placed < 5;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 200);
	}
}
