package com.relay.pvpchamps.moves;

import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Cart fighters leave TNT minecarts lying around after a fight. When no enemy is close, the bot walks to a stray
 * one and hits it with its sword: breaking a minecart drops it as an item the bot picks up.
 */
public final class TntMinecart implements Move {
	private MinecartTNT cart;
	private int t;

	@Override public String id() { return "tnt_minecart"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0 || c.dist < 8) return false;
		if (c.find(Items.DIAMOND_SWORD) < 0 && c.find(Items.NETHERITE_SWORD) < 0) return false;
		AABB box = c.bot.getBoundingBox().inflate(14);
		cart = c.bot.level().getEntitiesOfClass(MinecartTNT.class, box).stream().findFirst().orElse(null);
		if (cart == null) return false;
		t = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 120 || cart == null || !cart.isAlive() || c.dist < 6) return false;
		c.face(cart.position().add(0, 0.5, 0));
		double d = c.bot.position().distanceTo(cart.position());
		c.run(d > 2.5 ? 1f : 0f, false);
		if (d <= 3.0) {
			if (c.hold(Items.DIAMOND_SWORD) || c.hold(Items.NETHERITE_SWORD)) c.attackOnce();
			if (!cart.isAlive()) return false;
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 100);
	}
}
