package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Mace accuracy: when a mace bot is falling toward its opponent, take over the last few ticks of the dive. It
 * holds the mace, aims at where the opponent will be when it arrives (not where they are now), steers over them in
 * the air, and swings only once they are inside the smash range with a real fall behind the swing.
 */
public final class MaceSmash implements Move {
	private int left;

	@Override public String id() { return "mace_smash"; }

	@Override
	public boolean start(Ctx c) {
		if (!c.carriesMace()) return MoveEngine.reject(id(), "no_mace");
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.bot.onGround() || c.bot.isFallFlying() || c.bot.getDeltaMovement().y > -0.25) return MoveEngine.reject(id(), "not_diving");
		if (c.bot.fallDistance < 1.5) return MoveEngine.reject(id(), "short_fall");
		double horiz = Math.hypot(c.target.getX() - c.bot.getX(), c.target.getZ() - c.bot.getZ());
		if (horiz > 7 || c.target.getY() > c.bot.getY()) return MoveEngine.reject(id(), "geometry");
		left = 30;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.bot.onGround()) return false;
		if (!c.hold(Items.MACE)) return false;
		// Time to arrive: how long until the bot's feet reach the opponent's height at its current fall speed.
		double drop = Math.max(0.1, c.bot.getY() - c.target.getY());
		double speed = Math.max(0.3, -c.bot.getDeltaMovement().y);
		double ticks = Math.min(12, drop / speed);
		Vec3 vel = c.target.getDeltaMovement();
		Vec3 aim = c.target.position().add(vel.x * ticks, 0.9, vel.z * ticks);
		c.face(aim);
		double horiz = Math.hypot(aim.x - c.bot.getX(), aim.z - c.bot.getZ());
		c.pack.setForward(horiz > 0.9 ? 1f : 0f); // steer over them; do not overshoot
		c.pack.setSprinting(horiz > 0.9);
		boolean inRange = c.bot.position().distanceTo(c.target.position()) <= 3.0;
		if (inRange && c.bot.fallDistance >= 1.5) {
			c.attackOnce();
			MoveEngine.reject(id(), "smash_swing");
			return false;
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 40);
	}
}
