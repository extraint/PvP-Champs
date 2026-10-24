package com.relay.pvpchamps.moves;

import com.relay.pvpchamps.MatchManager;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Crystal and anchor fights dig holes. A bot that ends up several blocks below the arena floor throws an ender pearl up
 * and out toward the opponent's side to get back onto the floor, and tries again if the first one is not enough.
 */
public final class PearlOut implements Move {
	private int t;

	@Override public String id() { return "pearl_out"; }

	@Override
	public boolean start(Ctx c) {
		Vec3 o = MatchManager.origin();
		if (o == null) return MoveEngine.reject(id(), "no_origin");
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.bot.getY() > o.y - 2.5) return MoveEngine.reject(id(), "not_in_hole");
		if (!c.bot.onGround() || c.bot.isUsingItem()) return MoveEngine.reject(id(), "busy");
		if (c.find(Items.ENDER_PEARL) < 0) return MoveEngine.reject(id(), "no_pearl");
		t = 0;
		MoveEngine.reject(id(), "start");
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		Vec3 o = MatchManager.origin();
		if (o == null || ++t > 30) return false;
		if (!c.hold(Items.ENDER_PEARL)) return false;
		Vec3 to = c.target.position().subtract(c.bot.position()).multiply(1, 0, 1);
		if (to.lengthSqr() < 1.0) to = o.subtract(c.bot.position()).multiply(1, 0, 1);
		Vec3 dir = to.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : to.normalize();
		// Throw out past the rim, as far as the hole is deep (plus a margin), and a little high: the pearl arcs down onto the floor.
		double depth = Math.max(0, o.y - c.bot.getY());
		double reach = Math.max(5.0, Math.min(16.0, depth + 3.0));
		c.face(c.bot.position().add(dir.scale(reach)).with(net.minecraft.core.Direction.Axis.Y, o.y + 3.0));
		if (t == 3) c.useOnce();
		return t < 14;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 40);
	}
}
