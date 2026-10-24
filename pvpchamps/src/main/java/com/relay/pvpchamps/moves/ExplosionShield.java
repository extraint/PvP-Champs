package com.relay.pvpchamps.moves;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import vexbot.fakeplayer.bot.BotPlayerActionPack;

/**
 * Crystal and anchor fights: the totem sits in the offhand, so the shield is a hotbar item, and the bot raises it facing
 * any end crystal or charged respawn anchor that is about to go off close by. A shield facing the blast takes the hit.
 */
public final class ExplosionShield implements Move {
	private int t, gone;
	private Vec3 where;

	@Override public String id() { return "explosion_shield"; }

	/** The nearest end crystal or charged anchor within reach of a blast, or null. */
	private static Vec3 explosive(Ctx c) {
		var level = c.bot.level();
		Vec3 best = null;
		double bd = 1e9;
		for (EndCrystal e : level.getEntitiesOfClass(EndCrystal.class, c.bot.getBoundingBox().inflate(6.0))) {
			double d = e.position().distanceToSqr(c.bot.position());
			if (d < bd) { bd = d; best = e.position(); }
		}
		BlockPos at = c.bot.blockPosition();
		for (int dx = -5; dx <= 5; dx++)
			for (int dy = -2; dy <= 3; dy++)
				for (int dz = -5; dz <= 5; dz++) {
					BlockPos p = at.offset(dx, dy, dz);
					var st = level.getBlockState(p);
					if (st.is(Blocks.RESPAWN_ANCHOR) && st.getValue(RespawnAnchorBlock.CHARGE) > 0) {
						Vec3 v = Vec3.atCenterOf(p);
						double d = v.distanceToSqr(c.bot.position());
						if (d < bd) { bd = d; best = v; }
					}
				}
		return best;
	}

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.find(Items.SHIELD) < 0) return MoveEngine.reject(id(), "no_shield");
		if (c.dist > 16) return MoveEngine.reject(id(), "far");
		where = explosive(c);
		if (where == null) return MoveEngine.reject(id(), "nothing_to_block");
		t = 0;
		gone = 0;
		MoveEngine.reject(id(), "start");
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 16 || !c.target.isAlive()) return false;
		Vec3 now = explosive(c);
		if (now == null) { if (++gone > 3) return false; } else { where = now; gone = 0; }
		if (!c.hold(Items.SHIELD)) return false;
		c.face(where);                                   // the shield only blocks what it faces
		c.pack.start(BotPlayerActionPack.ActionType.USE, BotPlayerActionPack.Action.continuous());
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stop(BotPlayerActionPack.ActionType.USE);
		c.restoreSlot();
		c.st.setCooldown(id(), 8);
	}
}
