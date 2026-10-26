package com.relay.pvpchamps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import vexbot.BotSettings;
import vexbot.DifficultyParams;

/**
 * Simulates the ping of an online event. Every fighter has a region; a match is "hosted" in the region that is closest
 * to everyone in it (the lowest worst-case round trip, then the lowest total), and each fighter's ping is their round
 * trip to that host. A real player gets that ping added to their connection (their packets are really delayed, so the tab
 * list ping and the feel of combat change); a roster bot gets slower reactions and a little worse aim for the same ping.
 */
public final class Ping {
	private Ping() {}

	public enum Region {
		NA_WEST("NA West"), NA_EAST("NA East"), EU("Europe"), SA("South America"), AS("Asia"), OCE("Oceania");
		public final String label;
		Region(String label) { this.label = label; }
		static Region parse(String s) {
			String k = s.toLowerCase().replace(' ', '_').replace('-', '_');
			for (Region r : values()) if (r.name().toLowerCase().equals(k) || r.label.toLowerCase().replace(' ', '_').equals(k)) return r;
			return null;
		}
	}

	/** Typical round-trip times in milliseconds between regions (symmetric); the diagonal is a player and a server in the same region. */
	private static final int[][] RTT = {
		//            NA_W NA_E  EU  SA  AS OCE
		/* NA_WEST */ { 25,  70, 150, 160, 130, 140 },
		/* NA_EAST */ { 70,  25,  85, 120, 190, 210 },
		/* EU      */ { 150, 85,  25, 200, 230, 290 },
		/* SA      */ { 160, 120, 200, 30, 300, 280 },
		/* AS      */ { 130, 190, 230, 300, 40, 110 },
		/* OCE     */ { 140, 210, 290, 280, 110, 25 },
	};

	public static int rtt(Region a, Region b) { return RTT[a.ordinal()][b.ordinal()]; }

	/** The region closest to everyone: lowest worst-case round trip, then lowest total. */
	public static Region host(List<Region> regions) {
		Region best = Region.NA_EAST;
		int bestMax = Integer.MAX_VALUE, bestSum = Integer.MAX_VALUE;
		for (Region c : Region.values()) {
			int max = 0, sum = 0;
			for (Region r : regions) { int d = rtt(r, c); max = Math.max(max, d); sum += d; }
			if (max < bestMax || (max == bestMax && sum < bestSum)) { best = c; bestMax = max; bestSum = sum; }
		}
		return best;
	}

	// ----------------------------------------------------------------------- regions of the players

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Region> BOTS = new HashMap<>();   // lower-case roster name
	private static final Map<String, Region> HUMANS = new HashMap<>(); // lower-case player name
	private static boolean enabled = true;

	/**
	 * Where each roster player lives. These are best guesses, NOT looked up from a real source: edit
	 * config/pvpchamps/regions.json (names to NA_WEST, NA_EAST, EU, SA, AS or OCE) to put anyone where they really are.
	 */
	private static Map<String, Region> defaults() {
		Map<String, Region> m = new HashMap<>();
		for (String n : new String[] { "Swight", "JACKS5ON", "ItzTwilightTT", "Freekee_Fang" }) m.put(n, Region.NA_WEST);
		for (String n : new String[] { "Coldified", "Sweatgod", "BLMcheat", "MyDogIsConfused", "DarkKnightAragon", "Blvckwlf" }) m.put(n, Region.NA_EAST);
		for (String n : new String[] { "ImMixed", "x_Kyran_x", "mikagoated", "Sizierung", "Ricardoxx_", "TheobaldTheBird", "C0RZZ", "Ymiau", "ZeNain",
				"Alepordio", "1kiks_", "Flowtives", "ninorc15" }) m.put(n, Region.EU);
		m.put("SpawnPlayer", Region.AS);
		return m;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("pvpchamps").resolve("regions.json");
		Map<String, String> read = null;
		try {
			Files.createDirectories(file.getParent());
			if (Files.exists(file)) read = GSON.fromJson(Files.readString(file), new TypeToken<Map<String, String>>() {}.getType());
			else {
				Map<String, String> out = new java.util.TreeMap<>();
				defaults().forEach((k, v) -> out.put(k, v.name()));
				Files.writeString(file, GSON.toJson(out));
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvpchamps] regions.json unreadable, using the built-in regions: " + e);
		}
		BOTS.clear();
		defaults().forEach((k, v) -> BOTS.put(k.toLowerCase(), v));
		if (read != null) read.forEach((k, v) -> { Region r = Region.parse(v); if (r != null) BOTS.put(k.toLowerCase(), r); });
	}

	public static Region regionOf(String name, boolean human) {
		if (human) return HUMANS.getOrDefault(name.toLowerCase(), Region.NA_WEST);   // players default to California
		return BOTS.getOrDefault(name.toLowerCase(), Region.NA_EAST);
	}

	public static void setHumanRegion(String name, Region r) { HUMANS.put(name.toLowerCase(), r); }

	public static boolean enabled() { return enabled; }

	public static void setEnabled(boolean on) { enabled = on; }

	public static Map<String, Region> botRegions() { return BOTS; }

	// ----------------------------------------------------------------------- applying the ping

	/** The ping of one match: who is where, the host, and the lag put on the real players until {@link #end}. */
	public static final class Session {
		private final Map<String, Integer> pings = new HashMap<>();
		private final List<ServerPlayer> lagged = new ArrayList<>();

		/** Gives every bot in the match its ping (call after the bot's tier is applied each round, which resets its skill). */
		public void applyBots() {
			if (!enabled) return;
			for (Map.Entry<String, Integer> e : pings.entrySet()) {
				DifficultyParams p = BotSettings.botDifficulties.get(e.getKey());
				if (p == null) continue;
				int ping = e.getValue();
				p.reactionMinTicks += ping / 60;           // it sees what happened half a round trip ago and its answer arrives half a trip late
				p.reactionMaxTicks += ping / 30;
				p.shieldReactionMs += ping;
				p.accuracy = Math.max(0.4, p.accuracy * (1.0 - ping / 1500.0));
				p.missChance = Math.min(0.4, p.missChance + ping / 3000.0);
			}
		}

		public void end() {
			for (ServerPlayer p : lagged) clearLag(p);
			lagged.clear();
		}
	}

	/**
	 * Starts the ping for a match. {@code fighters} are every fighter's name; {@code humans} the real players among them.
	 * Announces the host and everyone's ping through {@code say}.
	 */
	public static Session begin(List<String> fighters, List<ServerPlayer> humans, Consumer<String> say) {
		Session s = new Session();
		if (!enabled) return s;
		List<Region> regions = new ArrayList<>();
		Map<String, Region> by = new HashMap<>();
		for (String n : fighters) {
			boolean human = humans.stream().anyMatch(h -> h.getGameProfile().name().equalsIgnoreCase(n));
			Region r = regionOf(n, human);
			by.put(n, r);
			regions.add(r);
		}
		Region host = host(regions);
		StringBuilder sb = new StringBuilder("§7Server: " + host.label + " (closest to everyone) · ping");
		for (String n : fighters) {
			int ping = rtt(by.get(n), host);
			ServerPlayer hp = humans.stream().filter(h -> h.getGameProfile().name().equalsIgnoreCase(n)).findFirst().orElse(null);
			if (hp != null) { setLag(hp, ping); s.lagged.add(hp); }
			else s.pings.put(n, ping);
			sb.append(" ").append(n).append(" ").append(ping).append("ms (").append(by.get(n).label).append(")");
		}
		say.accept(sb.toString());
		System.err.println("[pvpchamps] " + sb.toString().replaceAll("§.", ""));
		return s;
	}

	// ----------------------------------------------------------------------- real latency on a player's connection

	private static final String HANDLER = "pvpchamps_lag";

	private static Channel channelOf(ServerPlayer p) {
		var conn = ((com.relay.pvpchamps.mixin.CommonListenerAccess) p.connection).pvpchamps$connection();
		return ((com.relay.pvpchamps.mixin.ConnectionAccess) conn).pvpchamps$channel();
	}

	private static void setLag(ServerPlayer p, int pingMs) {
		try {
			Channel ch = channelOf(p);
			if (ch.pipeline().get(HANDLER) instanceof Lag l) { l.oneWayMs = pingMs / 2; return; }
			if (ch.pipeline().get("packet_handler") == null) return;
			Lag l = new Lag(pingMs / 2);
			ch.pipeline().addBefore("packet_handler", HANDLER, l);
		} catch (RuntimeException e) {
			System.err.println("[pvpchamps] could not add ping to " + p.getGameProfile().name() + ": " + e);
		}
	}

	/** One line per online player: their connection's measured latency and the delay added to it. */
	public static List<String> status(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.connection == null || p.getClass().getSimpleName().contains("Bot")) continue;
			int added = 0;
			try { if (channelOf(p).pipeline().get(HANDLER) instanceof Lag l) added = l.oneWayMs * 2; } catch (RuntimeException ignored) { continue; }
			out.add(p.getGameProfile().name() + ": latency " + p.connection.latency() + " ms, simulated ping " + added + " ms");
		}
		return out;
	}

	private static void clearLag(ServerPlayer p) {
		try {
			Channel ch = channelOf(p);
			if (ch.pipeline().get(HANDLER) instanceof Lag l) l.oneWayMs = 0;   // stays in the pipeline (ordering) and passes packets straight through
		} catch (RuntimeException ignored) { /* player already gone */ }
	}

	/**
	 * Delays every packet in and out by half the ping (plus or minus 12% jitter). Deliveries are kept in order: a packet is
	 * never delivered before one sent earlier, even when the delay is lowered.
	 */
	private static final class Lag extends ChannelDuplexHandler {
		volatile int oneWayMs;
		private long nextIn, nextOut;
		private final Random rng = new Random();

		Lag(int oneWayMs) { this.oneWayMs = oneWayMs; }

		private long due(long previous) {
			long now = System.nanoTime();
			double jitter = 1.0 + (rng.nextDouble() - 0.5) * 0.24;
			return Math.max(previous, now + (long) (oneWayMs * jitter * 1_000_000L));
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			if (oneWayMs <= 0 && nextIn <= System.nanoTime()) { ctx.fireChannelRead(msg); return; }
			nextIn = due(nextIn);
			ctx.executor().schedule(() -> ctx.fireChannelRead(msg), Math.max(0, nextIn - System.nanoTime()), TimeUnit.NANOSECONDS);
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) {
			if (oneWayMs <= 0 && nextIn <= System.nanoTime()) { ctx.fireChannelReadComplete(); return; }
			ctx.executor().schedule(ctx::fireChannelReadComplete, Math.max(0, nextIn - System.nanoTime()), TimeUnit.NANOSECONDS);
		}

		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
			if (oneWayMs <= 0 && nextOut <= System.nanoTime()) { ctx.write(msg, promise); return; }
			nextOut = due(nextOut);
			ctx.executor().schedule(() -> ctx.write(msg, promise), Math.max(0, nextOut - System.nanoTime()), TimeUnit.NANOSECONDS);
		}

		@Override
		public void flush(ChannelHandlerContext ctx) {
			if (oneWayMs <= 0 && nextOut <= System.nanoTime()) { ctx.flush(); return; }
			ctx.executor().schedule(ctx::flush, Math.max(0, nextOut - System.nanoTime()), TimeUnit.NANOSECONDS);
		}
	}
}
