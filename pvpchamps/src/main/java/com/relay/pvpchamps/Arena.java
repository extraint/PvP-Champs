package com.relay.pvpchamps;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The PvP Champs colosseum: a sandy mud floor, a dark deepslate wall with a gold band, spruce seating with planted
 * aisles and a railed walkway, a stone arcade of tall round arches, black truss towers carrying a glass roof, the
 * event logo hung over the north stands, and a shell of barrier blocks around all of it. No terracotta or concrete. Built
 * with /pvpchamps arena build, remembered in config/pvpchamps/arena.json, and every match is staged inside it.
 * After each round the surrounding 500 by 500 area is put back as it was.
 */
public final class Arena {
	private Arena() {}

	public static final int FLOOR_R = 30, STANDS_R = 44, WALL_R = 50, BARRIER_R = 56, HEIGHT = 70;
	private static BlockPos centre;      // the floor's top surface, middle of the arena
	private static ArenaGuard guard;

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("pvpchamps/arena.json"); }

	public static void load() {
		try {
			if (!Files.exists(file())) return;
			JsonObject o = new com.google.gson.Gson().fromJson(Files.readString(file()), JsonObject.class);
			centre = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
		} catch (Exception e) {
			System.err.println("[pvpchamps] could not read arena.json: " + e);
		}
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), "{\n  \"x\": " + centre.getX() + ",\n  \"y\": " + centre.getY() + ",\n  \"z\": " + centre.getZ() + "\n}\n");
		} catch (Exception e) {
			System.err.println("[pvpchamps] could not write arena.json: " + e);
		}
	}

	public static boolean exists() { return centre != null; }

	/** Where matches are staged: the middle of the floor. */
	public static Vec3 spot() { return centre == null ? null : new Vec3(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5); }

	public static void forget() { centre = null; guard = null; try { Files.deleteIfExists(file()); } catch (Exception ignored) {} }

	/** The shared 500 x 500 reset area, snapshotted the first time it is needed (or right after a build). */
	public static synchronized ArenaGuard guard(ServerLevel level) {
		if (centre == null) return null;
		if (guard == null) guard = new ArenaGuard(level, spot(), 250, 14, 50, true);
		return guard;
	}

	private static BlockState b(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	private static BlockState stairs(String id, Direction facing) {
		return b(id).setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	// ------------------------------------------------------------------ build

	public static void build(ServerLevel level, BlockPos at) {
		centre = at;
		save();
		guard = null;
		int cx = at.getX(), cy = at.getY(), cz = at.getZ();
		java.util.Random rng = new java.util.Random(26);
		double[][] seeds = new double[40][2];
		for (double[] s : seeds) { double a = rng.nextDouble() * 6.2832, r = Math.sqrt(rng.nextDouble()) * FLOOR_R; s[0] = Math.cos(a) * r; s[1] = Math.sin(a) * r; }

		BlockState packed = b("packed_mud"), dirt = b("dirt"), coarse = b("coarse_dirt"), air = b("air"), stone = b("stone"),
			dbrick = b("deepslate_bricks"), dtiles = b("deepslate_tiles"), dpol = b("polished_deepslate"), dchis = b("chiseled_deepslate"),
			gold = b("gold_block"), rawGold = b("raw_gold_block"), smooth = b("smooth_stone"), sbrick = b("stone_bricks"),
			spol = b("polished_andesite"), schis = b("chiseled_stone_bricks"), moss = b("mossy_stone_bricks"),
			rail = b("stone_brick_wall"), glass = b("glass"), beam = b("polished_blackstone"), barrier = b("barrier"),
			bush = b("azalea_leaves").setValue(BlockStateProperties.PERSISTENT, true),
			bloom = b("flowering_azalea_leaves").setValue(BlockStateProperties.PERSISTENT, true);
		int wallTop = cy + 8, walkY = cy + 9 + (STANDS_R - FLOOR_R - 2), arcTop = walkY + 34, roofY = cy + 67;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int R = BARRIER_R + 1;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > BARRIER_R + 0.5) continue;
				int x = cx + dx, z = cz + dz;
				double ang = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
				for (int y = cy - 3; y <= cy - 2; y++) set(level, p.set(x, y, z), stone);
				int solidTo; // highest y this column fills; everything above it up to the roof is cleared
				if (d <= FLOOR_R + 0.5) { // sandy mud floor with soft darker patches and fine cracks
					int cell = cellOf(dx, dz, seeds);
					BlockState f = isCrack(dx, dz, seeds) ? dirt : (cell % 7 == 0 ? coarse : cell % 5 == 0 ? dirt : packed);
					set(level, p.set(x, cy - 1, z), f);
					solidTo = cy - 1;
				} else if (d <= FLOOR_R + 2.5) { // the arena wall: dark tiled panels, pilasters, gold band, cap
					boolean pilaster = (ang % 6.0) < 1.0;
					boolean inner = d <= FLOOR_R + 1.5;
					for (int y = cy - 1; y < wallTop; y++) {
						BlockState w;
						if (y <= cy) w = dpol;
						else if (y >= cy + 6) w = pilaster ? dpol : (y == cy + 7 ? gold : (((int) (ang / 3)) % 2 == 0 ? rawGold : gold));
						else w = pilaster || !inner ? dpol : dtiles;
						set(level, p.set(x, y, z), w);
					}
					set(level, p.set(x, wallTop, z), dpol);
					solidTo = wallTop;
				} else if (d <= STANDS_R + 0.5) { // wooden seating rising from the wall top, stone aisles with planters
					int k = (int) Math.floor(d - FLOOR_R - 2.5);
					int h = wallTop + 1 + k;
					boolean aisle = (ang % 30.0) < 1.6;
					for (int y = cy - 1; y < h; y++) set(level, p.set(x, y, z), dbrick);
					if (aisle) {
						set(level, p.set(x, h, z), stairs("stone_brick_stairs", inward(dx, dz)));
						solidTo = h;
					} else {
						set(level, p.set(x, h, z), stairs("spruce_stairs", inward(dx, dz)));
						solidTo = h;
					}
					// a planter at the foot of every aisle, on both sides of it
					double a30 = ang % 30.0;
					if (k == 0 && (a30 > 28.0 || (a30 >= 1.6 && a30 < 3.4))) {
						set(level, p.set(x, h, z), moss);
						set(level, p.set(x, h + 1, z), bush);
						set(level, p.set(x, h + 2, z), bloom);
						solidTo = h + 2;
					}
				} else if (d <= STANDS_R + 3.5) { // the walkway along the top, with a stone railing on its inner edge
					for (int y = cy - 1; y < walkY; y++) set(level, p.set(x, y, z), dbrick);
					set(level, p.set(x, walkY, z), smooth);
					solidTo = walkY;
					if (d <= STANDS_R + 1.5) { set(level, p.set(x, walkY + 1, z), rail); solidTo = walkY + 1; }
				} else if (d <= WALL_R + 0.5) { // the arcade: tall round arches on columns, gold band, cornice, merlons
					double arc = Math.toRadians(ang % 15.0 - 7.5) * d; // blocks from the bay's centre
					double half = 4.6;
					for (int y = cy - 1; y <= walkY; y++) set(level, p.set(x, y, z), dbrick);
					for (int y = walkY + 1; y <= arcTop; y++) {
						int rel = y - walkY - 1;
						boolean col = Math.abs(arc) > half;
						double rr = rel - 17;
						boolean open = !col && (rel < 17 || rr * rr + arc * arc <= half * half);
						boolean rim = !col && !open && (rel < 17 ? false : rr * rr + arc * arc <= (half + 1.2) * (half + 1.2));
						BlockState w;
						if (open) w = air;
						else if (rel == 27) w = gold;
						else if (rel == 26 || rel == 28) w = dpol;
						else if (col && (rel == 0 || rel == 16)) w = schis;
						else if (col) w = spol;
						else if (rim) w = dtiles;
						else w = sbrick;
						set(level, p.set(x, y, z), w);
					}
					boolean merlon = ((int) Math.floor(Math.toRadians(ang) * d / 2)) % 2 == 0;
					if (merlon) set(level, p.set(x, arcTop + 1, z), sbrick);
					solidTo = merlon ? arcTop + 1 : arcTop;
				} else {
					solidTo = cy - 2;
				}
				int clearTo = d > BARRIER_R - 1.5 ? cy - 3 : cy + HEIGHT - 1;
				for (int y = solidTo + 1; y <= clearTo; y++) set(level, p.set(x, y, z), air);
				// the glass roof on a black grid of beams, over everything inside the arcade
				if (d <= WALL_R + 0.5) set(level, p.set(x, roofY, z), (Math.floorMod(dx, 9) == 0 || Math.floorMod(dz, 9) == 0) ? beam : glass);
				if (d > BARRIER_R - 1.5) {
					for (int y = cy - 3; y <= cy + HEIGHT; y++) set(level, p.set(x, y, z), barrier);
				} else {
					set(level, p.set(x, cy + HEIGHT, z), barrier);
				}
			}
		}
		// black lattice truss towers on the walkway, holding up the roof
		BlockState post = b("polished_blackstone_bricks"), lattice = b("polished_blackstone_wall");
		for (int i = 0; i < 8; i++) {
			double a = Math.toRadians(22.5 + 45 * i);
			int tx = cx + (int) Math.round(Math.cos(a) * (STANDS_R + 2.5)), tz = cz + (int) Math.round(Math.sin(a) * (STANDS_R + 2.5));
			for (int y = walkY + 1; y < roofY; y++)
				for (int ox = -1; ox <= 1; ox++)
					for (int oz = -1; oz <= 1; oz++) {
						boolean corner = ox != 0 && oz != 0, centreCol = ox == 0 && oz == 0;
						BlockState s = corner ? post : centreCol ? air : ((y - walkY) % 4 == 0 ? post : lattice);
						set(level, p.set(tx + ox, y, tz + oz), s);
					}
		}
		banner(level, cx, cy, cz);
		guard(level);
	}

	private static void clearUp(ServerLevel level, BlockPos.MutableBlockPos p, int x, int cz, int z, int fromY, int count, BlockState air) {
		for (int y = fromY; y < fromY + count; y++) set(level, p.set(x, y, z), air);
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState s) {
		if (level.getBlockState(pos) != s) level.setBlock(pos, s, 18);
	}

	private static Direction inward(int dx, int dz) {
		if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.WEST : Direction.EAST;
		return dz > 0 ? Direction.NORTH : Direction.SOUTH;
	}

	/** Cracks where the two nearest of the seed points are almost equally far: a cracked-clay look. */
	private static int cellOf(int dx, int dz, double[][] seeds) {
		double best = 1e9; int bi = 0;
		for (int i = 0; i < seeds.length; i++) { double d = Math.hypot(dx - seeds[i][0], dz - seeds[i][1]); if (d < best) { best = d; bi = i; } }
		return bi;
	}

	private static boolean isCrack(int dx, int dz, double[][] seeds) {
		double d1 = 1e9, d2 = 1e9;
		for (double[] s : seeds) {
			double d = Math.hypot(dx - s[0], dz - s[1]);
			if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
		}
		return d2 - d1 < 0.9;
	}

	// ------------------------------------------------------------------ the logo

	/**
	 * The PvP Champs logo (orange emblem, "PVP" over a dark "CHAMPS" board, ribbon tails, tilted up to the right),
	 * read from pvpchamps_logo.txt one block per character and hung over the north stands facing the floor, held up
	 * by two black truss posts.
	 */
	private static void banner(ServerLevel level, int cx, int cy, int cz) {
		java.util.List<String> rows = new java.util.ArrayList<>();
		try (var in = Arena.class.getResourceAsStream("/pvpchamps_logo.txt")) {
			if (in == null) return;
			for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) if (!line.isBlank()) rows.add(line);
		} catch (Exception e) {
			System.err.println("[pvpchamps] could not read the logo: " + e);
			return;
		}
		java.util.Map<Character, BlockState> pal = java.util.Map.of(
			'O', b("orange_wool"), 'o', b("raw_copper_block"), 'h', b("honeycomb_block"), 'K', b("black_wool"),
			'B', b("polished_deepslate"), 'D', b("black_wool"), 'W', b("smooth_quartz"), 'S', b("light_gray_wool"));
		int w = rows.get(0).length(), h = rows.size();
		int z = cz - 34, x0 = cx - w / 2, bottom = cy + 15;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		// truss posts from the seats up to the sign
		BlockState post = b("polished_blackstone_bricks"), rail = b("polished_blackstone_wall");
		for (int px : new int[] { cx - 18, cx - 17, cx + 17, cx + 18 })
			for (int y = cy + 8; y < bottom + h / 2; y++) set(level, p.set(px, y, z - 1), y % 3 == 0 ? post : rail);
		for (int r = 0; r < h; r++) {
			String row = rows.get(r);
			for (int c = 0; c < row.length(); c++) {
				BlockState s = pal.get(row.charAt(c));
				if (s != null) set(level, p.set(x0 + c, bottom + (h - 1 - r), z), s);
			}
		}
	}
}
