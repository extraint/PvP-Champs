package com.relay.pvphub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The lobby, after PvPHQ's: a floating island in a night sky. A red-carpet plaza with a stone plinth in the middle (you
 * spawn on it facing north), a wooden walk round it, low stone walls with end-stone gateposts, flower beds, and four
 * plank paths out to the KIT hall (west), the FFA hall (east), the pumpkin stage with the Ranked Fights board under the
 * TOURS letters (north) and the big PVPHQ letters (south). Houses with steep roofs fill the corners, lanterns float
 * overhead, and NPCs stand round the plaza to open the queues, the kit editor and the rest.
 */
public final class Lobby {
	private Lobby() {}

	private static final int R = 76;                 // island radius
	private static int entitiesAt = -1;              // tick on which the NPCs and boards get (re)made
	private static int ticks;

	private static BlockState b(String id) { return Builder.b(id); }

	private static BlockState facing(String id, Direction d) { return b(id).setValue(BlockStateProperties.HORIZONTAL_FACING, d); }

	public static void reset() { entitiesAt = -1; ticks = 0; }

	static int ground() { return (int) Math.floor(Config.get().lobbyY) - 3; }

	/** Builds the lobby once per world (a lodestone inside the island marks it as done). */
	public static void build(MinecraftServer server) {
		Config c = Config.get();
		ServerLevel level = Hub.world(server);
		int cx = (int) Math.floor(c.lobbyX), cz = (int) Math.floor(c.lobbyZ), g = ground();
		for (int chx = (cx - R - 8) >> 4; chx <= (cx + R + 8) >> 4; chx++) for (int chz = (cz - R - 8) >> 4; chz <= (cz + R + 8) >> 4; chz++) level.setChunkForced(chx, chz, true);
		entitiesAt = 40;
		BlockPos marker = new BlockPos(cx, g - 12, cz);
		if (level.getBlockState(marker).is(b("gilded_blackstone").getBlock())) return;
		long t0 = System.currentTimeMillis();
		Builder.fill(level, cx - 22, 140, cz - 22, cx + 22, 178, cz + 22, b("air"));            // the small lobby of older versions, if any
		island(level, cx, g, cz);
		plaza(level, cx, g, cz);
		paths(level, cx, g, cz);
		houses(level, cx, g, cz);
		stage(level, cx, g, cz);
		signs(level, cx, g, cz);
		lanterns(level, cx, g, cz);
		lights(level, cx, g, cz);
		Builder.set(level, marker, b("gilded_blackstone"));
		System.out.println("[pvphub] built the lobby at " + cx + " " + (g + 3) + " " + cz + " in " + (System.currentTimeMillis() - t0) + " ms");
	}

	// ---------------------------------------------------------------- the island

	private static void island(ServerLevel level, int cx, int g, int cz) {
		Random rng = new Random(7);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockState grass = b("grass_block"), dirt = b("dirt"), stone = b("stone"), andesite = b("andesite"), tuff = b("tuff"), air = b("air");
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				double edge = R - 3 + 3 * Math.sin(Math.atan2(dz, dx) * 5) + 2 * Math.cos(Math.atan2(dz, dx) * 11);
				if (d > edge) continue;
				int x = cx + dx, z = cz + dz;
				for (int y = g + 1; y <= g + 45; y++) if (!level.getBlockState(p.set(x, y, z)).isAir()) Builder.set(level, p, air);
				Builder.set(level, p.set(x, g, z), grass);
				for (int y = g - 3; y < g; y++) Builder.set(level, p.set(x, y, z), dirt);
				int depth = (int) (4 + (edge - d) * 0.45 + rng.nextInt(3));
				for (int y = g - 3 - depth; y < g - 3; y++) Builder.set(level, p.set(x, y, z), (y + dx * 3 + dz) % 7 == 0 ? andesite : (y % 5 == 0 ? tuff : stone));
				// flowers and grass on the open ground
				if (d > 24 && rng.nextInt(9) == 0) {
					String[] f = {"short_grass", "short_grass", "short_grass", "oxeye_daisy", "pink_tulip", "azure_bluet", "lily_of_the_valley", "cornflower", "poppy"};
					Builder.set(level, p.set(x, g + 1, z), b(f[rng.nextInt(f.length)]));
				}
			}
		}
	}

	// ---------------------------------------------------------------- the plaza

	private static void plaza(ServerLevel level, int cx, int g, int cz) {
		Random rng = new Random(3);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockState wool = b("red_wool"), line = b("red_concrete"), oak = b("oak_planks"), spruce = b("spruce_planks"), air = b("air"),
			wall = b("polished_andesite"), cap = b("smooth_stone_slab"), post = b("end_stone_bricks"), moss = b("moss_block"),
			sbrick = b("stone_bricks"), cobble = b("cobbled_deepslate"), pand = b("polished_andesite"), tiles = b("deepslate_tiles");
		for (int dx = -24; dx <= 24; dx++) {
			for (int dz = -24; dz <= 24; dz++) {
				int x = cx + dx, z = cz + dz, ch = Math.max(Math.abs(dx), Math.abs(dz));
				boolean gate = Math.min(Math.abs(dx), Math.abs(dz)) <= 4;      // the four ways out
				if (ch <= 14) Builder.set(level, p.set(x, g, z), (Math.floorMod(dx, 5) == 0 || Math.floorMod(dz, 5) == 0) ? line : wool);
				else if (ch <= 19) Builder.set(level, p.set(x, g, z), ch == 19 ? spruce : oak);
				else if (ch == 20) {
					Builder.set(level, p.set(x, g, z), sbrick);
					if (!gate) { Builder.set(level, p.set(x, g + 1, z), wall); Builder.set(level, p.set(x, g + 2, z), cap); }
					else Builder.set(level, p.set(x, g, z), oak);
					if (Math.min(Math.abs(dx), Math.abs(dz)) == 5) { Builder.set(level, p.set(x, g + 1, z), post); Builder.set(level, p.set(x, g + 2, z), post); Builder.set(level, p.set(x, g + 3, z), b("end_stone_brick_slab")); }
				} else if (!gate) {                                              // flower beds outside the wall
					Builder.set(level, p.set(x, g, z), moss);
					String[] f = {"oxeye_daisy", "pink_tulip", "azure_bluet", "lily_of_the_valley", "azalea", "flowering_azalea", "short_grass"};
					Builder.set(level, p.set(x, g + 1, z), rng.nextInt(3) == 0 ? b(f[rng.nextInt(f.length)]) : air);
				}
			}
		}
		// the plinth you spawn on
		for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
			int ch = Math.max(Math.abs(dx), Math.abs(dz));
			Builder.set(level, p.set(cx + dx, g + 1, cz + dz), ch == 4 ? sbrick : cobble);
			if (ch <= 2) Builder.set(level, p.set(cx + dx, g + 2, cz + dz), ch == 2 ? pand : tiles);
		}
		// benches: slabs along the inside of the wall
		for (int i = -16; i <= 16; i++) {
			if (Math.abs(i) <= 6) continue;
			for (int[] s : new int[][] {{i, -18}, {i, 18}, {-18, i}, {18, i}}) Builder.set(level, p.set(cx + s[0], g + 1, cz + s[1]), b("spruce_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
		}
	}

	// ---------------------------------------------------------------- the paths

	private static void paths(ServerLevel level, int cx, int g, int cz) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockState oak = b("oak_planks"), spruce = b("spruce_planks"), fence = b("spruce_fence"), lantern = b("lantern");
		int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
		for (int[] d : dirs) {
			for (int s = 21; s <= 64; s++) {
				for (int lat = -4; lat <= 4; lat++) {
					int x = cx + d[0] * s + (d[0] == 0 ? lat : 0), z = cz + d[1] * s + (d[1] == 0 ? lat : 0);
					Builder.set(level, p.set(x, g, z), Math.abs(lat) == 4 ? spruce : oak);
					Builder.set(level, p.set(x, g + 1, z), b("air"));
				}
				if (s % 8 == 0) for (int lat : new int[] {-5, 5}) {           // lamp posts along both sides
					int x = cx + d[0] * s + (d[0] == 0 ? lat : 0), z = cz + d[1] * s + (d[1] == 0 ? lat : 0);
					for (int y = g + 1; y <= g + 3; y++) Builder.set(level, p.set(x, y, z), fence);
					Builder.set(level, p.set(x, g + 4, z), lantern);
				}
			}
		}
	}

	// ---------------------------------------------------------------- houses

	private static void houses(ServerLevel level, int cx, int g, int cz) {
		String[][] looks = {{"spruce_planks", "stripped_dark_oak_log", "brick_stairs", "bricks"}, {"birch_planks", "stripped_spruce_log", "dark_oak_stairs", "dark_oak_planks"},
			{"spruce_planks", "stripped_dark_oak_log", "red_nether_brick_stairs", "red_nether_bricks"}, {"oak_planks", "stripped_spruce_log", "spruce_stairs", "spruce_planks"}};
		int[][] at = {{-44, -42}, {44, -42}, {-44, 42}, {44, 42}, {-32, -66}, {32, -66}, {-64, -24}, {64, -24}, {-64, 26}, {64, 26}};
		for (int i = 0; i < at.length; i++) {
			String[] l = looks[i % looks.length];
			house(level, cx + at[i][0], g, cz + at[i][1], 14 + (i % 3) * 2, 10 + (i % 2) * 2, 7 + (i % 3), l);
		}
		// the two halls at the ends of the west and east paths
		house(level, cx - 72, g, cz, 12, 18, 10, looks[0]);
		house(level, cx + 72, g, cz, 12, 18, 10, looks[2]);
	}

	/** A timber-framed house: stone base, framed walls with windows, a steep gable roof along x. */
	private static void house(ServerLevel level, int hx, int g, int hz, int w, int d, int h, String[] look) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockState wall = b(look[0]), frame = b(look[1]), base = b("stone_bricks"), glass = b("glass"), roofFull = b(look[3]), air = b("air");
		int x0 = hx - w / 2, x1 = hx + w / 2, z0 = hz - d / 2, z1 = hz + d / 2;
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			boolean edgeX = x == x0 || x == x1, edgeZ = z == z0 || z == z1;
			Builder.set(level, p.set(x, g, z), base);
			for (int y = g + 1; y <= g + h; y++) {
				BlockState s;
				if (!edgeX && !edgeZ) s = air;
				else if ((edgeX && edgeZ) || y == g + h || y == g + 1) s = frame;
				else if (y >= g + 3 && y <= g + h - 2 && ((edgeX ? z : x) % 3 == 0)) s = glass;
				else s = wall;
				if (y == g + 1 && !(edgeX && edgeZ)) s = base;
				Builder.set(level, p.set(x, y, z), s);
			}
		}
		// roof: stairs stepping in from both long sides up to a ridge along x
		int layers = d / 2 + 1;
		for (int k = 0; k <= layers; k++) {
			int y = g + h + 1 + k, zn = z0 - 1 + k, zs = z1 + 1 - k;
			if (zn > zs) break;
			for (int x = x0 - 1; x <= x1 + 1; x++) {
				if (zn == zs) { Builder.set(level, p.set(x, y, zn), roofFull); continue; }
				Builder.set(level, p.set(x, y, zn), facing(look[2], Direction.SOUTH));
				Builder.set(level, p.set(x, y, zs), facing(look[2], Direction.NORTH));
				if (x == x0 - 1 || x == x1 + 1) continue;
				for (int z = zn + 1; z < zs; z++) Builder.set(level, p.set(x, y, z), (x == x0 || x == x1) ? wall : air);    // gable ends
			}
		}
	}

	// ---------------------------------------------------------------- the north stage

	private static void stage(ServerLevel level, int cx, int g, int cz) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int px = cx, py = g + 9, pz = cz - 72, r = 9;
		BlockState pumpkin = b("pumpkin"), stem = b("dark_oak_log"), leaves = b("dark_oak_leaves").setValue(BlockStateProperties.PERSISTENT, true);
		for (int dx = -r - 2; dx <= r + 2; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
			double e = (dx * dx) / (double) ((r + 2) * (r + 2)) + (dy * dy) / (double) ((r - 1) * (r - 1)) + (dz * dz) / (double) (r * r);
			if (e > 1.0) continue;
			boolean shell = e > 0.72;
			BlockState s = shell ? pumpkin : b("air");
			if (shell && dz > 0 && isFace(dx, dy)) s = facing("jack_o_lantern", Direction.SOUTH);
			Builder.set(level, p.set(px + dx, py + dy, pz + dz), s);
		}
		for (int y = py + r - 1; y <= py + r + 2; y++) Builder.set(level, p.set(px, y, pz), stem);
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) + Math.abs(dz) <= 3) Builder.set(level, p.set(px + dx, py + r + 2, pz + dz), leaves);
		// a stone stage in front of it
		for (int dx = -12; dx <= 12; dx++) for (int dz = -60; dz <= -58; dz++) Builder.set(level, p.set(cx + dx, g + 1, cz + dz), b("polished_blackstone_bricks"));
	}

	/** Two triangle eyes and a jagged grin. */
	private static boolean isFace(int dx, int dy) {
		boolean eye = dy >= 2 && dy <= 4 && (Math.abs(dx - 4) <= 4 - dy + 1 || Math.abs(dx + 4) <= 4 - dy + 1);
		boolean mouth = dy >= -4 && dy <= -2 && Math.abs(dx) <= 6 && !(dy == -2 && Math.floorMod(dx, 3) == 0) && !(dy == -4 && Math.floorMod(dx + 1, 3) == 0);
		return eye || mouth;
	}

	// ---------------------------------------------------------------- the letters

	private static void signs(ServerLevel level, int cx, int g, int cz) {
		BlockState orange = b("orange_wool"), cream = b("smooth_sandstone"), dark = b("spruce_planks"), black = b("black_wool");
		// north: TOURS over the stage, facing the plaza
		Font.write(level, "TOURS", cx, g + 22, cz - 80, 2, orange, black, true);
		// south: the big logo behind you as you spawn (facing north, so it reads from the plaza)
		writeFacingNorth(level, "PVPHQ", cx, g + 4, cz + 72, 3, orange, black);
		// west and east halls
		writeAlongZ(level, "KIT", cx - 64, g + 14, cz, 2, cream, dark, true);
		writeAlongZ(level, "FFA", cx + 64, g + 14, cz, 2, cream, dark, false);
		// the corners
		writeAlongZ(level, "SUMO", cx + 52, g + 16, cz - 42, 2, cream, dark, false);
		writeAlongZ(level, "COSMETIC", cx - 52, g + 16, cz - 42, 1, cream, dark, true);
	}

	/** Letters at z read from the north (someone on the plaza looking south): they run towards -x, the outline behind at z+1. */
	private static void writeFacingNorth(ServerLevel level, String text, int cx, int y, int z, int scale, BlockState face, BlockState back) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int[] q : Font.pixels(text, scale)) {
			Builder.set(level, p.set(cx - q[0], y + q[1], z), face);
			Builder.set(level, p.set(cx - q[0], y + q[1], z + 1), back);
		}
	}

	/** Letters running along z on a wall that faces +x (read by someone east of it) or -x. */
	private static void writeAlongZ(ServerLevel level, String text, int x, int y, int cz, int scale, BlockState face, BlockState back, boolean facePlusX) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int[] q : Font.pixels(text, scale)) {
			int along = facePlusX ? -q[0] : q[0];          // facing +x the reader looks west, so their right is north (-z)
			Builder.set(level, p.set(x, y + q[1], cz + along), face);
			Builder.set(level, p.set(facePlusX ? x - 1 : x + 1, y + q[1], cz + along), back);
		}
	}

	// ---------------------------------------------------------------- floating lights

	private static void lanterns(ServerLevel level, int cx, int g, int cz) {
		Random rng = new Random(11);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 26; i++) {
			double a = rng.nextDouble() * Math.PI * 2, r = 12 + rng.nextDouble() * 55;
			int x = cx + (int) (Math.cos(a) * r), y = g + 18 + rng.nextInt(16), z = cz + (int) (Math.sin(a) * r);
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
				Builder.set(level, p.set(x + dx, y + dy, z + dz), dx == 0 && dy == 0 && dz == 0 ? b("sea_lantern") : b("white_stained_glass"));
		}
	}

	/** Invisible light blocks (only in empty air, never in a wall) so the whole lobby is bright at night. */
	private static void lights(ServerLevel level, int cx, int g, int cz) {
		BlockState light = b("light").setValue(BlockStateProperties.LEVEL, 15);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int[][] layers = {{5, 6}, {16, 9}, {27, 12}};          // {height over the ground, spacing}
		for (int[] l : layers) {
			for (int dx = -R; dx <= R; dx += l[1]) for (int dz = -R; dz <= R; dz += l[1]) {
				if (Math.hypot(dx, dz) > R - 4) continue;
				p.set(cx + dx, g + l[0], cz + dz);
				if (level.getBlockState(p).isAir()) Builder.set(level, p, light);
			}
		}
	}

	// ---------------------------------------------------------------- NPCs and boards

	public static void tick(MinecraftServer server) {
		ticks++;
		if (entitiesAt >= 0 && ticks >= entitiesAt) { entitiesAt = -1; entities(server); }
		if (ticks % 40 == 5) boards(server);
	}

	private static void entities(MinecraftServer server) {
		Config c = Config.get();
		double cx = Math.floor(c.lobbyX) + 0.5, cz = Math.floor(c.lobbyZ) + 0.5, y = ground() + 1;
		Holo.clearAll(server);
		Holo.npc(server, "moneysmp", cx - 9, y, cz - 25, 0, "MoneySMP", "netherite_sword",
			Component.literal("§c§lMoney SMP"), Component.literal("§7Spawn NPC"), Component.literal("§fQueue for Money SMP"), Component.literal("§f1v1, 2v2 and 4v4 games."));
		Holo.npc(server, "uhc", cx + 9, y, cz - 25, 0, "UHCGames", "golden_apple",
			Component.literal("§6§lUHC Games"), Component.literal("§7Spawn NPC"), Component.literal("§fPlay or spectate other"), Component.literal("§fUHC games in NA or EU."));
		Holo.npc(server, "ranked", cx - 25, y, cz - 9, -90, "Ranked", "diamond_sword",
			Component.literal("§b§lRanked Queue"), Component.literal("§fClimb the tiers: LT5 to HT1"));
		Holo.npc(server, "unranked", cx + 25, y, cz - 9, 90, "Unranked", "iron_sword",
			Component.literal("§a§lUnranked Queue"), Component.literal("§fJust for fun"));
		Holo.npc(server, "kit", cx - 58, y, cz + 0.0, -90, "KitEditor", "book",
			Component.literal("§6§lKit Editor"), Component.literal("§fClick to edit your kits"));
		Holo.npc(server, "ffa", cx + 58, y, cz, 90, "FFA", "end_crystal",
			Component.literal("§d§lFFA"), Component.literal("§fJump in, no queue"));
		Holo.npc(server, "spectate", cx + 6, y, cz - 56, 0, "Spectator", "ender_eye",
			Component.literal("§e§lRanked Fights"), Component.literal("§fClick to spectate"));
		Holo.npc(server, "stats", cx - 25, y, cz + 9, -90, "Stats", "nether_star",
			Component.literal("§6§lLeaderboards"), Component.literal("§fYour stats and the top players"));
		Holo.npc(server, "regions", cx + 25, y, cz + 9, 90, "Regions", "compass",
			Component.literal("§e§lRegion Selector"), Component.literal("§fChoose where you queue"));
		boards(server);
	}

	private static void boards(MinecraftServer server) {
		Config c = Config.get();
		double cx = Math.floor(c.lobbyX) + 0.5, cz = Math.floor(c.lobbyZ) + 0.5, g = ground();
		// the Ranked Fights board on the stage
		List<Duel> live = Duels.active();
		StringBuilder sb = new StringBuilder("§6⚔ §e§lRanked Fights §6⚔\n§fPage 1/" + Math.max(1, (live.size() + 9) / 10) + "\n§7Click a fight to spectate\n\n");
		int n = 0;
		for (Duel d : live) {
			if (n++ >= 10) break;
			String a = d.p[0].getGameProfile().name(), bn = d.p[1].getGameProfile().name();
			double ea = Tiers.elo(d.p[0], d.kit), eb = Tiers.elo(d.p[1], d.kit);
			sb.append("§f").append(a).append(" ").append(Tiers.tag(ea)).append(" §c§lVS §f").append(bn).append(" ").append(Tiers.tag(eb))
				.append(" §8» §e").append(d.score[0]).append(" - ").append(d.score[1]).append("\n");
		}
		if (live.isEmpty()) sb.append(Arenas.building().isEmpty() ? "§7No fights right now\n" : "§7" + Arenas.building() + "\n");
		sb.append("\n§6Gamemodes: §fAll");
		Holo.text(server, "board", cx, g + 4, cz - 54, Component.literal(sb.toString()));
		// the leaderboard by the south path
		if (ticks % 200 == 5 || ticks < 60) {
			StringBuilder lb = new StringBuilder("§6§lTop Ranked §7(Sword)\n\n");
			List<Map.Entry<String, Double>> all = new ArrayList<>();
			for (String bot : Crowd.names()) all.add(Map.entry(bot, Crowd.elo(bot, Kit.SWORD)));
			for (var e : Stats.leaderboard(Kit.SWORD, 10)) all.add(Map.entry(e.getKey(), e.getValue().elo));
			all.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
			for (int i = 0; i < Math.min(10, all.size()); i++) {
				var e = all.get(i);
				lb.append(i < 3 ? "§6" : "§e").append(i + 1).append(". §f").append(e.getKey()).append(" ").append(Tiers.tag(e.getValue())).append("\n");
			}
			if (all.isEmpty()) lb.append("§7Nobody yet\n");
			Holo.text(server, "leaders", cx - 12, g + 5, cz + 30, Component.literal(lb.toString()));
			Holo.text(server, "welcome", cx + 12, g + 5, cz + 30, Component.literal("§6§lWelcome to " + c.serverName + "!\n\n§fRight-click the §6swords §fto queue\n§fthe §6book §fto edit kits\n§fthe §6compass §fto pick a region\n\n§7" + c.address));
		}
	}
}
