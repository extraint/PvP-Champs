package com.relay.pvphub;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One arena: the PvP Champs colosseum at twice the size. A cracked mud floor eighty blocks across the radius, a dark
 * deepslate wall with a gold band, spruce stands with planted aisles, a railed walkway, a stone arcade of round arches,
 * black truss towers holding a glass roof, the PVPHQ letters over the north stands, a hundred blocks of stone under the
 * floor and a barrier shell around it all. It is built in the background a few columns a tick (it is big), and between
 * rounds only the floor and the air over it are put back.
 */
public final class Arena {
	public static final int FLOOR_R = 80, STANDS_R = 104, WALL_R = 118, BARRIER_R = 126, HEIGHT = 110, DEPTH = 100;

	public final String name;
	public final BlockPos centre;      // the floor's top surface, middle of the arena
	public final int radius = FLOOR_R;
	public boolean busy;
	private boolean built;
	private int nextColumn = -1;       // building progress (columns done), -1 = not started
	private final double[][] seeds = new double[120][2];
	private static final int SPAN = BARRIER_R + 1, SIDE = 2 * SPAN + 1;

	Arena(String name, BlockPos centre) {
		this.name = name;
		this.centre = centre;
		java.util.Random rng = new java.util.Random(26);
		for (double[] s : seeds) { double a = rng.nextDouble() * 6.2832, r = Math.sqrt(rng.nextDouble()) * FLOOR_R; s[0] = Math.cos(a) * r; s[1] = Math.sin(a) * r; }
	}

	public boolean isBuilt() { return built; }

	/** 0-100. */
	public int progress() { return built ? 100 : Math.max(0, nextColumn) * 100 / (SIDE * SIDE); }

	/** Where the n-th fighter of a duel starts: either side of the middle, twelve blocks apart from it, facing each other. */
	public Vec3 spawn(int n) {
		return new Vec3(centre.getX() + 0.5 + (n == 0 ? -12 : 12), centre.getY(), centre.getZ() + 0.5);
	}

	public float spawnYaw(int n) { return n == 0 ? -90f : 90f; }

	public Vec3 randomSpawn(java.util.Random rng) {
		double a = rng.nextDouble() * Math.PI * 2, r = Math.sqrt(rng.nextDouble()) * (FLOOR_R - 10);
		return new Vec3(centre.getX() + 0.5 + Math.cos(a) * r, centre.getY(), centre.getZ() + 0.5 + Math.sin(a) * r);
	}

	public boolean inside(BlockPos p) {
		double dx = p.getX() - centre.getX(), dz = p.getZ() - centre.getZ();
		return dx * dx + dz * dz <= (FLOOR_R + 0.5) * (FLOOR_R + 0.5) && p.getY() >= centre.getY() - 6 && p.getY() <= centre.getY() + HEIGHT;
	}

	public boolean contains(Entity e) {
		double dx = e.getX() - centre.getX(), dz = e.getZ() - centre.getZ();
		return dx * dx + dz * dz <= (BARRIER_R + 2) * (BARRIER_R + 2) && e.getY() >= centre.getY() - 20 && e.getY() <= centre.getY() + HEIGHT + 5;
	}

	// ------------------------------------------------------------------ building

	/** Has it been built in this world already? (The gold band on the wall says so.) */
	public boolean detect(ServerLevel level) {
		if (built) return true;
		BlockPos probe = new BlockPos(centre.getX() + FLOOR_R + 1, centre.getY() + 7, centre.getZ());
		if (level.getBlockState(probe).is(Builder.b("gold_block").getBlock()) && level.getBlockState(centre.below()).is(Builder.b("packed_mud").getBlock())
			|| level.getBlockState(probe).is(Builder.b("raw_gold_block").getBlock())) built = true;
		return built;
	}

	private void forceChunks(ServerLevel level, boolean on) {
		int cx = centre.getX(), cz = centre.getZ();
		for (int chx = (cx - SPAN) >> 4; chx <= (cx + SPAN) >> 4; chx++)
			for (int chz = (cz - SPAN) >> 4; chz <= (cz + SPAN) >> 4; chz++) level.setChunkForced(chx, chz, on);
	}

	/** Builds up to {@code columns} more columns. Returns true once the arena is finished. */
	public boolean buildStep(ServerLevel level, int columns) {
		if (built) return true;
		if (nextColumn < 0) { forceChunks(level, true); nextColumn = 0; }
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int end = Math.min(SIDE * SIDE, nextColumn + columns);
		for (; nextColumn < end; nextColumn++) column(level, p, nextColumn / SIDE - SPAN, nextColumn % SIDE - SPAN);
		if (nextColumn >= SIDE * SIDE) {
			towers(level);
			letters(level);
			built = true;
			forceChunks(level, false);
			System.out.println("[pvphub] " + name + " is built");
		}
		return built;
	}

	private static BlockState stairs(String id, Direction facing) { return Builder.b(id).setValue(BlockStateProperties.HORIZONTAL_FACING, facing); }

	private static void set(ServerLevel level, BlockPos pos, BlockState s) {
		if (level.getBlockState(pos) != s) level.setBlock(pos, s, 2 | 16);
	}

	private static final BlockState PACKED = Builder.b("packed_mud"), DIRT = Builder.b("dirt"), COARSE = Builder.b("coarse_dirt"), AIR = Builder.b("air"), STONE = Builder.b("stone"),
		DBRICK = Builder.b("deepslate_bricks"), DTILES = Builder.b("deepslate_tiles"), DPOL = Builder.b("polished_deepslate"),
		GOLD = Builder.b("gold_block"), RAW_GOLD = Builder.b("raw_gold_block"), SMOOTH = Builder.b("smooth_stone"), SBRICK = Builder.b("stone_bricks"),
		SPOL = Builder.b("polished_andesite"), SCHIS = Builder.b("chiseled_stone_bricks"), MOSS = Builder.b("mossy_stone_bricks"),
		RAIL = Builder.b("stone_brick_wall"), GLASS = Builder.b("glass"), BEAM = Builder.b("polished_blackstone"), BARRIER = Builder.b("barrier"),
		BUSH = Builder.b("azalea_leaves").setValue(BlockStateProperties.PERSISTENT, true),
		BLOOM = Builder.b("flowering_azalea_leaves").setValue(BlockStateProperties.PERSISTENT, true);

	private BlockState floor(int dx, int dz) {
		int cell = cellOf(dx, dz);
		return isCrack(dx, dz) ? DIRT : (cell % 7 == 0 ? COARSE : cell % 5 == 0 ? DIRT : PACKED);
	}

	private void column(ServerLevel level, BlockPos.MutableBlockPos p, int dx, int dz) {
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d > BARRIER_R + 0.5) return;
		int cx = centre.getX(), cy = centre.getY(), cz = centre.getZ();
		int x = cx + dx, z = cz + dz;
		double ang = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
		int wallTop = cy + 8, walkY = cy + 9 + (STANDS_R - FLOOR_R - 2), arcTop = walkY + 34, roofY = cy + HEIGHT - 3;
		for (int y = cy - 3; y <= cy - 2; y++) set(level, p.set(x, y, z), STONE);
		if (d <= FLOOR_R + 3) for (int y = cy - 3 - DEPTH; y < cy - 3; y++) set(level, p.set(x, y, z), STONE);   // a hundred blocks of stone under the fight
		int solidTo;
		if (d <= FLOOR_R + 0.5) {
			set(level, p.set(x, cy - 1, z), floor(dx, dz));
			solidTo = cy - 1;
		} else if (d <= FLOOR_R + 2.5) {
			boolean pilaster = (ang % 3.0) < 0.6;
			boolean inner = d <= FLOOR_R + 1.5;
			for (int y = cy - 1; y < wallTop; y++) {
				BlockState w;
				if (y <= cy) w = DPOL;
				else if (y >= cy + 6) w = pilaster ? DPOL : (y == cy + 7 ? GOLD : (((int) (ang / 1.5)) % 2 == 0 ? RAW_GOLD : GOLD));
				else w = pilaster || !inner ? DPOL : DTILES;
				set(level, p.set(x, y, z), w);
			}
			set(level, p.set(x, wallTop, z), DPOL);
			solidTo = wallTop;
			if (inner) {
				for (int y = wallTop + 1; y <= cy + HEIGHT - 1; y++) set(level, p.set(x, y, z), BARRIER);
				solidTo = cy + HEIGHT - 1;
			}
		} else if (d <= STANDS_R + 0.5) {
			int k = (int) Math.floor(d - FLOOR_R - 2.5);
			int h = wallTop + 1 + k;
			boolean aisle = (ang % 20.0) < 1.0;
			for (int y = cy - 1; y < h; y++) set(level, p.set(x, y, z), DBRICK);
			set(level, p.set(x, h, z), stairs(aisle ? "stone_brick_stairs" : "spruce_stairs", inward(dx, dz)));
			solidTo = h;
			double a20 = ang % 20.0;
			if (k == 0 && (a20 > 19.0 || (a20 >= 1.0 && a20 < 2.0))) {
				set(level, p.set(x, h, z), MOSS);
				set(level, p.set(x, h + 1, z), BUSH);
				set(level, p.set(x, h + 2, z), BLOOM);
				solidTo = h + 2;
			}
		} else if (d <= STANDS_R + 3.5) {
			for (int y = cy - 1; y < walkY; y++) set(level, p.set(x, y, z), DBRICK);
			set(level, p.set(x, walkY, z), SMOOTH);
			solidTo = walkY;
			if (d <= STANDS_R + 1.5) { set(level, p.set(x, walkY + 1, z), RAIL); solidTo = walkY + 1; }
		} else if (d <= WALL_R + 0.5) {
			double arc = Math.toRadians(ang % 7.5 - 3.75) * d;
			double half = 4.6;
			for (int y = cy - 1; y <= walkY; y++) set(level, p.set(x, y, z), DBRICK);
			for (int y = walkY + 1; y <= arcTop; y++) {
				int rel = y - walkY - 1;
				boolean col = Math.abs(arc) > half;
				double rr = rel - 17;
				boolean open = !col && (rel < 17 || rr * rr + arc * arc <= half * half);
				boolean rim = !col && !open && rel >= 17 && rr * rr + arc * arc <= (half + 1.2) * (half + 1.2);
				BlockState w;
				if (open) w = AIR;
				else if (rel == 27) w = GOLD;
				else if (rel == 26 || rel == 28) w = DPOL;
				else if (col && (rel == 0 || rel == 16)) w = SCHIS;
				else if (col) w = SPOL;
				else if (rim) w = DTILES;
				else w = SBRICK;
				set(level, p.set(x, y, z), w);
			}
			boolean merlon = ((int) Math.floor(Math.toRadians(ang) * d / 2)) % 2 == 0;
			if (merlon) set(level, p.set(x, arcTop + 1, z), SBRICK);
			solidTo = merlon ? arcTop + 1 : arcTop;
		} else {
			solidTo = cy - 2;
		}
		int clearTo = d > BARRIER_R - 1.5 ? cy - 3 : cy + HEIGHT - 1;
		for (int y = solidTo + 1; y <= clearTo; y++) set(level, p.set(x, y, z), AIR);
		if (d <= WALL_R + 0.5) set(level, p.set(x, roofY, z), (Math.floorMod(dx, 9) == 0 || Math.floorMod(dz, 9) == 0) ? BEAM : GLASS);
		if (d > BARRIER_R - 1.5) for (int y = cy - 3; y <= cy + HEIGHT; y++) set(level, p.set(x, y, z), BARRIER);
		else set(level, p.set(x, cy + HEIGHT, z), BARRIER);
	}

	/** Black lattice truss towers on the walkway, up to the roof. */
	private void towers(ServerLevel level) {
		int cx = centre.getX(), cy = centre.getY(), cz = centre.getZ();
		int walkY = cy + 9 + (STANDS_R - FLOOR_R - 2), roofY = cy + HEIGHT - 3;
		BlockState post = Builder.b("polished_blackstone_bricks"), lattice = Builder.b("polished_blackstone_wall");
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 12; i++) {
			double a = Math.toRadians(15 + 30 * i);
			int tx = cx + (int) Math.round(Math.cos(a) * (STANDS_R + 2.5)), tz = cz + (int) Math.round(Math.sin(a) * (STANDS_R + 2.5));
			for (int y = walkY + 1; y < roofY; y++)
				for (int ox = -1; ox <= 1; ox++)
					for (int oz = -1; oz <= 1; oz++) {
						boolean corner = ox != 0 && oz != 0, mid = ox == 0 && oz == 0;
						set(level, p.set(tx + ox, y, tz + oz), corner ? post : mid ? AIR : ((y - walkY) % 4 == 0 ? post : lattice));
					}
		}
	}

	/** The PVPHQ letters, three blocks a pixel, hung over the north stands and facing the floor. */
	private void letters(ServerLevel level) {
		int cx = centre.getX(), cy = centre.getY(), cz = centre.getZ();
		Font.write(level, "PVPHQ", cx, cy + 34, cz - (STANDS_R - 6), 3, Builder.b("orange_wool"), Builder.b("black_wool"), true);
	}

	private static Direction inward(int dx, int dz) {
		if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.WEST : Direction.EAST;
		return dz > 0 ? Direction.NORTH : Direction.SOUTH;
	}

	private int cellOf(int dx, int dz) {
		double best = 1e9; int bi = 0;
		for (int i = 0; i < seeds.length; i++) { double d = Math.hypot(dx - seeds[i][0], dz - seeds[i][1]); if (d < best) { best = d; bi = i; } }
		return bi;
	}

	private boolean isCrack(int dx, int dz) {
		double d1 = 1e9, d2 = 1e9;
		for (double[] s : seeds) {
			double d = Math.hypot(dx - s[0], dz - s[1]);
			if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
		}
		return d2 - d1 < 0.9;
	}

	// ------------------------------------------------------------------ between rounds

	/** The floor put back, the air over it cleared, stray entities gone. Only the part near the fight is touched. */
	public void reset(ServerLevel level) {
		if (!built) { while (!buildStep(level, 4000)) {} return; }
		int cx = centre.getX(), cy = centre.getY(), cz = centre.getZ();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -FLOOR_R; dx <= FLOOR_R; dx++) {
			for (int dz = -FLOOR_R; dz <= FLOOR_R; dz++) {
				if (dx * dx + dz * dz > (FLOOR_R + 0.5) * (FLOOR_R + 0.5)) continue;
				int x = cx + dx, z = cz + dz;
				set(level, p.set(x, cy - 1, z), floor(dx, dz));
				for (int y = cy - 6; y < cy - 1; y++) set(level, p.set(x, y, z), STONE);
				for (int y = cy; y <= cy + 14; y++) if (!level.getBlockState(p.set(x, y, z)).isAir()) level.setBlock(p, AIR, 2 | 16);
			}
		}
		AABB box = new AABB(cx - BARRIER_R, cy - 10, cz - BARRIER_R, cx + BARRIER_R + 1, cy + HEIGHT, cz + BARRIER_R + 1);
		List<Entity> gone = new ArrayList<>();
		for (Entity e : level.getEntities((Entity) null, box, x -> !(x instanceof Player))) gone.add(e);
		for (Entity e : gone) e.discard();
	}
}
