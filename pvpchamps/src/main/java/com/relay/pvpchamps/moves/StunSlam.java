package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The real stun slam (as in Vexbot's own StunSlam): during a mace dive onto an opponent with their shield up, swing the
 * axe first, which disables the shield, then swap to the mace and slam on the very next tick, still falling. The bot
 * leads the opponent's movement like mace_smash does, so the combo lands inside the smash range with a real fall
 * behind it. mace_smash steps aside while the target is blocking and this takes the dive.
 */
public final class StunSlam implements Move {
	private enum Step { DIVE, AXE_DONE }

	private Step step;
	private int left;

	@Override public String id() { return "stun_slam"; }

	static boolean hasAxe(Ctx c) { return c.find(Items.NETHERITE_AXE) >= 0 || c.find(Items.DIAMOND_AXE) >= 0; }

	@Override
	public boolean start(Ctx c) {
		if (!c.carriesMace()) return MoveEngine.reject(id(), "no_mace");
		if (!hasAxe(c)) return MoveEngine.reject(id(), "no_axe");
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (!c.target.isBlocking()) return MoveEngine.reject(id(), "not_blocking");
		if (c.bot.onGround() || c.bot.isFallFlying() || c.bot.getDeltaMovement().y > -0.25) return MoveEngine.reject(id(), "not_diving");
		if (c.bot.fallDistance < 1.5) return MoveEngine.reject(id(), "short_fall");
		double horiz = Math.hypot(c.target.getX() - c.bot.getX(), c.target.getZ() - c.bot.getZ());
		if (horiz > 7 || c.target.getY() > c.bot.getY()) return MoveEngine.reject(id(), "geometry");
		step = Step.DIVE;
		left = 30;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive() || c.bot.onGround()) return false;
		// Aim at where the opponent will be when the bot arrives, not where they are now.
		double drop = Math.max(0.1, c.bot.getY() - c.target.getY());
		double speed = Math.max(0.3, -c.bot.getDeltaMovement().y);
		double ticks = Math.min(12, drop / speed);
		Vec3 vel = c.target.getDeltaMovement();
		Vec3 aim = c.target.position().add(vel.x * ticks, 0.9, vel.z * ticks);
		c.face(aim);
		double horiz = Math.hypot(aim.x - c.bot.getX(), aim.z - c.bot.getZ());
		c.pack.setForward(horiz > 0.9 ? 1f : 0f);
		c.pack.setSprinting(horiz > 0.9);
		switch (step) {
			case DIVE -> {
				boolean inRange = c.bot.position().distanceTo(c.target.position()) <= 3.2;
				if (!(c.hold(Items.NETHERITE_AXE) || c.hold(Items.DIAMOND_AXE))) return false; // axe in hand while falling in
				if (inRange && c.bot.fallDistance >= 1.5) {
					c.attackOnce();            // the axe disables the shield
					step = Step.AXE_DONE;
					MoveEngine.reject(id(), "axe_stun");
				}
			}
			case AXE_DONE -> {
				if (!c.hold(Items.MACE)) return false;
				c.attackOnce();                // the slam, one tick after the stun, with the fall still behind it
				MoveEngine.reject(id(), "mace_slam");
				c.st.forceMove(new MaceFollowUp());
				return false;
			}
		}
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.restoreSlot();
		c.st.setCooldown(id(), 12);
	}
}
