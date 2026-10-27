package com.relay.pvphub;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One arena: a square floor with walls, an invisible roof, and bedrock under a stone floor. It is built once and, between
 * rounds, only its floor and the air above it are put back (and stray items, arrows and crystals removed).
 */
public final class Arena {
	public static final int HEIGHT = 40;

	public final String name;
	public final BlockPos centre;      // the floor's top surface, middle of the arena
	public final int radius;           // half the interior's width
	public boolean busy;
	private boolean built;

	Arena(String name, BlockPos centre, int radius) {
		this.name = name;
		this.centre = centre;
		this.radius = radius;
	}

	/** Where the n-th fighter of a duel starts: opposite ends of the middle line, looking at each other. */
	public Vec3 spawn(int n) {
		double off = Math.max(8, radius - 8) * (n == 0 ? -1 : 1);
		return new Vec3(centre.getX() + 0.5 + off, centre.getY(), centre.getZ() + 0.5);
	}

	public float spawnYaw(int n) { return n == 0 ? -90f : 90f; }

	/** A random standing place for the free-for-all. */
	public Vec3 randomSpawn(java.util.Random rng) {
		return new Vec3(centre.getX() + 0.5 + (rng.nextDouble() * 2 - 1) * (radius - 4), centre.getY(), centre.getZ() + 0.5 + (rng.nextDouble() * 2 - 1) * (radius - 4));
	}

	/** Inside the walls, from the floor up to the roof. */
	public boolean inside(BlockPos p) {
		return Math.abs(p.getX() - centre.getX()) <= radius && Math.abs(p.getZ() - centre.getZ()) <= radius
			&& p.getY() >= centre.getY() && p.getY() <= centre.getY() + HEIGHT;
	}

	public boolean contains(Entity e) {
		return Math.abs(e.getX() - centre.getX()) <= radius + 2 && Math.abs(e.getZ() - centre.getZ()) <= radius + 2
			&& e.getY() >= centre.getY() - 10 && e.getY() <= centre.getY() + HEIGHT + 5;
	}

	private BlockState floor(int x, int z) {
		return ((x + z) & 1) == 0 ? Builder.b("smooth_stone") : Builder.b("stone_bricks");
	}

	private void buildStatic(ServerLevel level) {
		int cx = centre.getX(), cy = centre.getY(), cz = centre.getZ(), r = radius;
		for (int chx = (cx - r - 2) >> 4; chx <= (cx + r + 2) >> 4; chx++)
			for (int chz = (cz - r - 2) >> 4; chz <= (cz + r + 2) >> 4; chz++) level.setChunkForced(chx, chz, true);   // keep it loaded
		BlockState stone = Builder.b("stone"), bedrock = Builder.b("bedrock"), wall = Builder.b("deepslate_bricks"), barrier = Builder.b("barrier");
		Builder.fill(level, cx - r - 1, cy - 7, cz - r - 1, cx + r + 1, cy - 7, cz + r + 1, bedrock);
		Builder.fill(level, cx - r - 1, cy - 6, cz - r - 1, cx + r + 1, cy - 2, cz + r + 1, stone);
		for (int x = cx - r - 1; x <= cx + r + 1; x++) {            // the walls: a ring of deepslate bricks, then barriers up to the roof
			for (int z : new int[] {cz - r - 1, cz + r + 1}) wallColumn(level, x, z, cy, wall, barrier);
		}
		for (int z = cz - r; z <= cz + r; z++) {
			for (int x : new int[] {cx - r - 1, cx + r + 1}) wallColumn(level, x, z, cy, wall, barrier);
		}
		Builder.fill(level, cx - r - 1, cy + HEIGHT + 1, cz - r - 1, cx + r + 1, cy + HEIGHT + 1, cz + r + 1, barrier);   // the roof
		built = true;
		reset(level);
	}

	private void wallColumn(ServerLevel level, int x, int z, int cy, BlockState wall, BlockState barrier) {
		Builder.fill(level, x, cy - 1, z, x, cy + 7, z, wall);
		Builder.fill(level, x, cy + 8, z, x, cy + HEIGHT, z, barrier);
	}

	/** Back to a clean arena: the floor restored, everything above it cleared, loose entities gone. */
	public void reset(ServerLevel level) {
		if (!built) { buildStatic(level); return; }
		int cx = centre.getX(), cy = centre.getY(), cz = centre.getZ(), r = radius;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockState air = Builder.b("air"), stone = Builder.b("stone");
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				Builder.set(level, p.set(x, cy - 1, z), floor(x, z));
				for (int y = cy - 5; y < cy - 1; y++) if (!level.getBlockState(p.set(x, y, z)).is(stone.getBlock())) Builder.set(level, p, stone);
				for (int y = cy; y <= cy + 14; y++) if (!level.getBlockState(p.set(x, y, z)).isAir()) Builder.set(level, p, air);
			}
		}
		clearEntities(level);
	}

	private void clearEntities(ServerLevel level) {
		AABB box = new AABB(centre.getX() - radius - 1, centre.getY() - 8, centre.getZ() - radius - 1, centre.getX() + radius + 2, centre.getY() + HEIGHT + 2, centre.getZ() + radius + 2);
		List<Entity> gone = new ArrayList<>();
		for (Entity e : level.getEntities((Entity) null, box, x -> !(x instanceof Player))) gone.add(e);
		for (Entity e : gone) e.discard();
	}
}
