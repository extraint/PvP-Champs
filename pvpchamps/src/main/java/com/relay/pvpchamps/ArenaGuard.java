package com.relay.pvpchamps;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Remembers the blocks around an arena before a match and puts them back afterwards (craters, webs, walls, fire, water). */
final class ArenaGuard {
	private static final int RADIUS = 36, DOWN = 10, UP = 28;

	private final ServerLevel level;
	private final int x0, y0, z0, sx, sy, sz;
	private final BlockState[] saved;
	private final java.util.Set<java.util.UUID> before = new java.util.HashSet<>(); // entities that were already lying there: not ours to remove

	ArenaGuard(ServerLevel level, Vec3 centre) {
		this.level = level;
		x0 = (int) Math.floor(centre.x) - RADIUS;
		z0 = (int) Math.floor(centre.z) - RADIUS;
		y0 = Math.max(level.getMinY(), (int) Math.floor(centre.y) - DOWN);
		int y1 = Math.min(level.getMaxY(), (int) Math.floor(centre.y) + UP);
		sx = sz = RADIUS * 2 + 1;
		sy = y1 - y0 + 1;
		saved = new BlockState[sx * sy * sz];
		for (Entity e : level.getEntities((Entity) null, new AABB(x0, y0, z0, x0 + sx, y0 + sy, z0 + sz), ArenaGuard::leftover)) before.add(e.getUUID());
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int i = 0;
		for (int y = 0; y < sy; y++)
			for (int z = 0; z < sz; z++)
				for (int x = 0; x < sx; x++) saved[i++] = level.getBlockState(p.set(x0 + x, y0 + y, z0 + z));
	}

	/** Puts every changed block back and clears what the fight left lying around. */
	void restore() {
		AABB box = new AABB(x0, y0, z0, x0 + sx, y0 + sy, z0 + sz);
		for (Entity e : level.getEntities((Entity) null, box, e -> leftover(e) && !before.contains(e.getUUID()))) e.discard();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int i = 0, fixed = 0;
		for (int y = 0; y < sy; y++)
			for (int z = 0; z < sz; z++)
				for (int x = 0; x < sx; x++, i++) {
					p.set(x0 + x, y0 + y, z0 + z);
					if (level.getBlockState(p) != saved[i]) {
						level.setBlock(p, saved[i], 18); // 2: tell clients, 16: no neighbour reactions or drops
						fixed++;
					}
				}
		System.err.println("[pvpchamps] arena restored (" + fixed + " blocks)");
	}

	private static boolean leftover(Entity e) {
		return e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof PrimedTnt
			|| e instanceof AbstractMinecart || e instanceof Projectile;
	}
}
