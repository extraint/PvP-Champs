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
 * The PvP Champs colosseum: a dirt-and-mud cracked floor, a dark blue wall with a hazard stripe, rising mud-brick
 * stands, a tall outer wall, the event logo hung over the north stands, and a shell of barrier blocks around all of it. Built
 * with /pvpchamps arena build, remembered in config/pvpchamps/arena.json, and every match is staged inside it.
 * After each round the surrounding 500 by 500 area is put back as it was.
 */
public final class Arena {
	private Arena() {}

	public static final int FLOOR_R = 30, STANDS_R = 49, WALL_R = 54, BARRIER_R = 58, HEIGHT = 70;
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

		BlockState dirt = b("dirt"), coarse = b("coarse_dirt"), packed = b("packed_mud"), crack = b("mud"),
			blue = b("blue_terracotta"), base = b("deepslate_bricks"), pil = b("polished_blackstone_bricks"), cap = b("polished_blackstone"),
			black = b("black_concrete"), yellow = b("yellow_concrete"), air = b("air"), stone = b("stone"),
			mudb = b("mud_bricks"), aisle = b("packed_mud"), walk = b("smooth_stone"), dslate = b("deepslate_bricks"),
			chiseled = b("chiseled_deepslate"), tiles = b("deepslate_tiles"), barrier = b("barrier");
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int R = BARRIER_R + 1;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > BARRIER_R + 0.5) continue;
				int x = cx + dx, z = cz + dz;
				double ang = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
				boolean aisleCol = d > FLOOR_R + 1 && d < STANDS_R + 1 && (ang % 22.5) < 2.0;
				for (int y = cy - 3; y <= cy - 2; y++) set(level, p.set(x, y, z), stone);
				if (d <= FLOOR_R + 0.5) {
					BlockState f;
					int cell = cellOf(dx, dz, seeds);
					if (isCrack(dx, dz, seeds)) f = crack;
					else { int h = (cell * 7 + 3) % 5; f = h < 2 ? dirt : h < 4 ? coarse : packed; }
					set(level, p.set(x, cy - 1, z), f);
					clearUp(level, p, x, cz, z, cy, HEIGHT, air);
				} else if (d <= FLOOR_R + 1.5) { // the arena wall: tall, pilasters, hazard stripe
					boolean pilaster = (ang % 7.5) < 1.4;
					for (int y = cy - 1; y <= cy + 6; y++) {
						BlockState w = y <= cy ? base : (pilaster ? pil : (y == cy + 1 ? dslate : blue));
						set(level, p.set(x, y, z), w);
					}
					set(level, p.set(x, cy + 7, z), (int) (ang / 2.5) % 2 == 0 ? yellow : black);
					set(level, p.set(x, cy + 8, z), cap);
					clearUp(level, p, x, cz, z, cy + 9, HEIGHT - 9, air);
				} else if (d <= STANDS_R + 0.5) { // the stands, mud-brick seating
					int k = (int) Math.floor(d - FLOOR_R - 1.5);
					int h = 9 + k;
					for (int y = cy - 1; y < cy + h; y++) set(level, p.set(x, y, z), aisleCol ? aisle : mudb);
					BlockState seat = aisleCol ? aisle : (k % 6 == 5 ? walk : stairs("mud_brick_stairs", inward(dx, dz)));
					set(level, p.set(x, cy + h, z), seat);
					clearUp(level, p, x, cz, z, cy + h + 1, HEIGHT - h - 1, air);
				} else if (d <= WALL_R + 0.5) { // the huge outer wall: pillars, bands, arched windows, battlements
					boolean pilCol = (ang % 15.0) < 2.5;
					double a15 = ang % 15.0;
					int top = cy + 59;
					for (int y = cy - 1; y <= top; y++) {
						int rel = y - cy;
						boolean band = rel % 12 == 11;
						boolean open = false;
						if (!pilCol) {
							for (int t = 0; t < 2; t++) {
								int y0 = 24 + t * 17;
								if (rel >= y0 && rel < y0 + 8 && (rel < y0 + 6 || Math.abs(a15 - 8.75) < 2.0) && Math.abs(a15 - 8.75) < 3.4) open = true;
							}
						}
						BlockState w;
						if (open) w = air;
						else if (pilCol) w = band ? chiseled : pil;
						else if (band) w = tiles;
						else if (rel < 6) w = dslate;
						else w = ((int) (d * 3 + y * 5 + ang) % 9 == 0) ? packed : mudb;
						set(level, p.set(x, y, z), w);
					}
					if ((ang % 4.0) < 2.0 || pilCol) { set(level, p.set(x, top + 1, z), pilCol ? pil : mudb); set(level, p.set(x, top + 2, z), pilCol ? chiseled : mudb); }
					clearUp(level, p, x, cz, z, top + 3, HEIGHT - 61, air);
				} else if (d <= BARRIER_R - 1.5 && d > WALL_R + 0.5) {
					clearUp(level, p, x, cz, z, cy - 1, HEIGHT + 1, air);
				}
				if (d > BARRIER_R - 1.5) {
					for (int y = cy - 3; y <= cy + HEIGHT; y++) set(level, p.set(x, y, z), barrier);
				} else {
					set(level, p.set(x, cy + HEIGHT, z), barrier);
				}
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
			'O', b("orange_concrete"), 'o', b("orange_terracotta"), 'h', b("honeycomb_block"), 'K', b("black_concrete"),
			'B', b("gray_concrete"), 'D', b("black_concrete"), 'W', b("white_concrete"), 'S', b("light_gray_concrete"));
		int w = rows.get(0).length(), h = rows.size();
		int z = cz - 40, x0 = cx - w / 2, bottom = cy + 18;
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
