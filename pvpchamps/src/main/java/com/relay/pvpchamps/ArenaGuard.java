package com.relay.pvpchamps;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers the blocks around an arena before a match and puts them back afterwards (craters, webs, walls, fire,
 * water). The big version covers a 500 by 500 square, but only the chunks that are loaded when the snapshot is
 * taken: nothing a match does reaches the chunks nobody has loaded, and forcing 1,000 chunks to generate would
 * take minutes.
 */
final class ArenaGuard {
	private final ServerLevel level;
	private final int x0, y0, z0, sx, sy, sz;
	private final BlockState[] saved;
	private final boolean[] chunkSaved; // per chunk of the box: was it loaded (and so snapshotted)?
	private final int cx0, cz0, ncx;
	private final java.util.Set<java.util.UUID> before = new java.util.HashSet<>(); // entities that were already lying there: not ours to remove

	/** The small per-match box (73 x 73) used when there is no arena. */
	ArenaGuard(ServerLevel level, Vec3 centre) { this(level, centre, 36, 10, 28, false); }

	/** A square of side {@code 2 * radius + 1} around the centre, {@code down} below and {@code up} above it. */
	ArenaGuard(ServerLevel level, Vec3 centre, int radius, int down, int up, boolean loadedOnly) {
		this.level = level;
		x0 = (int) Math.floor(centre.x) - radius;
		z0 = (int) Math.floor(centre.z) - radius;
		y0 = Math.max(level.getMinY(), (int) Math.floor(centre.y) - down);
		int y1 = Math.min(level.getMaxY(), (int) Math.floor(centre.y) + up);
		sx = sz = radius * 2 + 1;
		sy = y1 - y0 + 1;
		saved = new BlockState[sx * sy * sz];
		cx0 = x0 >> 4;
		cz0 = z0 >> 4;
		ncx = ((x0 + sx - 1) >> 4) - cx0 + 1;
		int ncz = ((z0 + sz - 1) >> 4) - cz0 + 1;
		chunkSaved = new boolean[ncx * ncz];
		for (int cz = 0; cz < ncz; cz++)
			for (int cx = 0; cx < ncx; cx++)
				chunkSaved[cz * ncx + cx] = !loadedOnly || level.getChunkSource().getChunkNow(cx0 + cx, cz0 + cz) != null;
		AABB box = new AABB(x0, y0, z0, x0 + sx, y0 + sy, z0 + sz);
		for (Entity e : level.getEntities((Entity) null, box, ArenaGuard::leftover)) before.add(e.getUUID());
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int z = 0; z < sz; z++)
			for (int x = 0; x < sx; x++) {
				if (!chunkSaved[chunkIndex(x, z)]) continue;
				for (int y = 0; y < sy; y++) saved[(y * sz + z) * sx + x] = level.getBlockState(p.set(x0 + x, y0 + y, z0 + z));
			}
	}

	private int chunkIndex(int x, int z) {
		return (((z0 + z) >> 4) - cz0) * ncx + (((x0 + x) >> 4) - cx0);
	}

	/** Puts every changed block back and clears what the fight left lying around. */
	void restore() {
		AABB box = new AABB(x0, y0, z0, x0 + sx, y0 + sy, z0 + sz);
		for (Entity e : level.getEntities((Entity) null, box, e -> leftover(e) && !before.contains(e.getUUID()))) e.discard();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int fixed = 0;
		for (int z = 0; z < sz; z++)
			for (int x = 0; x < sx; x++) {
				if (!chunkSaved[chunkIndex(x, z)]) continue;
				if (level.getChunkSource().getChunkNow((x0 + x) >> 4, (z0 + z) >> 4) == null) continue; // unloaded since: nothing touched it
				for (int y = 0; y < sy; y++) {
					BlockState want = saved[(y * sz + z) * sx + x];
					p.set(x0 + x, y0 + y, z0 + z);
					if (want != null && level.getBlockState(p) != want) {
						level.setBlock(p, want, 18); // 2: tell clients, 16: no neighbour reactions or drops
						fixed++;
					}
				}
			}
		System.err.println("[pvpchamps] arena restored (" + fixed + " blocks)");
	}

	private static boolean leftover(Entity e) {
		return e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof PrimedTnt
			|| e instanceof AbstractMinecart || e instanceof Projectile;
	}
}
