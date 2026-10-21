package com.relay.pvpchamps.moves;

import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Cart PvP: lay a rail between the two fighters, set a TNT minecart on it, light it, and back off so the blast
 * lands on the opponent and not on the bot.
 */
public final class CartBomb implements Move {
	private int phase, t;
	private Vec3 spot;
	private MinecartTNT cart;

	@Override public String id() { return "cart_bomb"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.count(Items.TNT_MINECART) == 0 || c.count(Items.RAIL) == 0) return MoveEngine.reject(id(), "no_cart");
		if (c.find(Items.FLINT_AND_STEEL) < 0) return MoveEngine.reject(id(), "no_flint");
		if (c.dist < 6 || c.dist > 14) return MoveEngine.reject(id(), "range");
		if (!c.bot.onGround()) return MoveEngine.reject(id(), "airborne");
		if (c.hp() < 8f) return MoveEngine.reject(id(), "low_hp");
		Vec3 toward = c.target.position().subtract(c.bot.position()).multiply(1, 0, 1).normalize();
		spot = c.bot.position().add(toward.scale(Math.min(4.0, c.dist * 0.45)));
		phase = 0;
		t = 0;
		cart = null;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (!c.target.isAlive() || ++t > 80) return false;
		Vec3 floor = new Vec3(spot.x, c.bot.getY() - 0.5, spot.z);
		switch (phase) {
			case 0 -> {
				c.pack.stopMovement();
				if (!c.hold(Items.RAIL)) return false;
				c.face(floor);
				if (t >= 3) { c.useOnce(); phase = 1; t = 0; }
			}
			case 1 -> {
				if (!c.hold(Items.TNT_MINECART)) return false;
				c.face(new Vec3(spot.x, c.bot.getY() + 0.1, spot.z));
				if (t >= 3) { c.useOnce(); phase = 2; t = 0; }
			}
			case 2 -> {
				if (cart == null) cart = c.bot.level().getEntitiesOfClass(MinecartTNT.class,
					new net.minecraft.world.phys.AABB(spot, spot).inflate(2.5)).stream().findFirst().orElse(null);
				if (cart == null) return t < 10;
				if (!c.hold(Items.FLINT_AND_STEEL)) return false;
				c.face(cart.position().add(0, 0.4, 0));
				if (t >= 3) { c.useOnce(); phase = 3; t = 0; }
			}
			default -> { // it is lit: get clear of the blast
				c.face(c.targetEye());
				c.runBack(true);
				return t < 30;
			}
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 500);
	}
}
