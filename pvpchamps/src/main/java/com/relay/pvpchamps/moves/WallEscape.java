package com.relay.pvpchamps.moves;

import com.relay.pvpchamps.Arena;
import net.minecraft.world.phys.Vec3;

/**
 * A bot pressed against the arena wall that has stopped moving (its own AI keeps backing away from, or circling, an opponent
 * with a wall behind it) runs back toward the middle of the fight until it is clear of the wall.
 */
public final class WallEscape implements Move {
	private int left;
	private Vec3 aim;

	@Override public String id() { return "wall_escape"; }

	@Override
	public boolean start(Ctx c) {
		if (!Arena.exists()) return MoveEngine.reject(id(), "no_arena");
		Vec3 mid = Arena.spot();
		double d = Math.hypot(c.bot.getX() - mid.x, c.bot.getZ() - mid.z);
		if (d < Arena.FLOOR_R - 3.0) return MoveEngine.reject(id(), "not_at_wall");
		if (c.st.stillTicks < 20) return MoveEngine.reject(id(), "moving");
		boolean shield = c.bot.isUsingItem() && c.bot.getUseItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
		if ((c.bot.isUsingItem() && !shield) || c.bot.isFallFlying()) return MoveEngine.reject(id(), "busy");   // eating or drinking is left alone; a held shield is not
		if (c.hp() <= 8f) return MoveEngine.reject(id(), "low_hp"); // let it heal first
		// Head for the point between the opponent and the middle of the floor: away from the wall, toward the fight.
		aim = new Vec3((c.target.getX() + mid.x) / 2, c.target.getY(), (c.target.getZ() + mid.z) / 2);
		left = 30;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (--left <= 0 || !c.target.isAlive()) return false;
		Vec3 mid = Arena.spot();
		if (Math.hypot(c.bot.getX() - mid.x, c.bot.getZ() - mid.z) < Arena.FLOOR_R - 6.0) return false;   // clear of the wall
		if (c.bot.isUsingItem()) { c.pack.stop(vexbot.fakeplayer.bot.BotPlayerActionPack.ActionType.USE); c.bot.stopUsingItem(); }   // lower the shield to move
		c.face(aim.add(0, 0.9, 0));
		c.run(1f, true);
		if (c.bot.onGround() && left % 8 == 0) c.jumpOnce();                       // hop over anything it is snagged on
		if (c.dist <= 3.05 && c.bot.getAttackStrengthScale(0.5f) >= 0.95f) c.attackOnce();
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.st.stillTicks = 0;
		c.st.setCooldown(id(), 40);
	}
}
