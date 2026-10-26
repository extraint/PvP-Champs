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

	/** Server locations and player regions: the same list, each with where it is, so the round trip between two is worked out from the distance. */
	public enum Region {
		NA_WEST("NA West (California)", 34.05, -118.24), NA_CENTRAL("NA Central (Texas)", 32.78, -96.80), NA_EAST("NA East (Virginia)", 39.04, -77.49),
		SA("South America (Brazil)", -23.55, -46.63),
		EU_WEST("EU West (London)", 51.51, -0.13), EU_SOUTH("EU South (Madrid)", 40.42, -3.70), EU_CENTRAL("EU Central (Frankfurt)", 50.11, 8.68),
		EU_EAST("EU East (Warsaw)", 52.23, 21.01),
		MIDDLE_EAST("Middle East (Dubai)", 25.20, 55.27), INDIA("India (Mumbai)", 19.08, 72.88), ASIA_SEA("SE Asia (Singapore)", 1.35, 103.82),
		JAPAN("Japan (Tokyo)", 35.68, 139.69), OCE("Oceania (Sydney)", -33.87, 151.21), AFRICA("Africa (Johannesburg)", -26.20, 28.05);

		public final String label;
		final double lat, lon;

		Region(String label, double lat, double lon) { this.label = label; this.lat = lat; this.lon = lon; }

		/** A region from its name, its label, or an older short name (EU, AS, NA). */
		static Region parse(String s) {
			String k = s.trim().toLowerCase().replace(' ', '_').replace('-', '_');
			for (Region r : values()) if (r.name().toLowerCase().equals(k) || r.label.toLowerCase().replace(' ', '_').equals(k)
					|| r.label.toLowerCase().replaceAll(" \\(.*", "").replace(' ', '_').equals(k)) return r;
			return switch (k) { case "eu", "europe" -> EU_CENTRAL; case "as", "asia" -> JAPAN; case "na" -> NA_EAST; case "oceania", "au" -> OCE; default -> null; };
		}
	}

	/**
	 * Round trip in milliseconds between two regions, from the distance: light in fibre covers about 200 km a millisecond
	 * and cables run roughly 1.5 times the straight line, plus a few milliseconds of routing. A player and a server in the
	 * same region still have at least 25 ms.
	 */
	public static int rtt(Region a, Region b) {
		double p1 = Math.toRadians(a.lat), p2 = Math.toRadians(b.lat), dl = Math.toRadians(b.lon - a.lon);
		double d = 6371 * Math.acos(Math.min(1, Math.sin(p1) * Math.sin(p2) + Math.cos(p1) * Math.cos(p2) * Math.cos(dl)));
		return (int) Math.max(25, Math.round(2 * (d * 1.5 / 200) + 8));
	}

	/** The server location closest to everyone: lowest worst-case round trip, then lowest total. */
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
	private static boolean spikes = true;

	/**
	 * Where each roster player lives. Coldified, MyDogIsConfused, SpawnPlayer, BLMcheat, x_Kyran_x, mikagoated, ninorc15, Flowtives, ZeNain, Blvckwlf and ItzTwilightTT were told to me;
	 * the rest are best guesses, NOT looked up from a real source: edit config/pvpchamps/regions.json (name to a region name)
	 * to put anyone where they really are.
	 */
	private static Map<String, Region> defaults() {
		Map<String, Region> m = new HashMap<>();
		for (String n : new String[] { "Swight", "JACKS5ON", "Freekee_Fang" }) m.put(n, Region.NA_WEST);
		for (String n : new String[] { "Sweatgod", "DarkKnightAragon", "SpawnPlayer", "ZeNain" }) m.put(n, Region.NA_EAST);   // ZeNain: Canada
		for (String n : new String[] { "ImMixed", "Sizierung", "Ricardoxx_", "TheobaldTheBird", "C0RZZ", "Ymiau", "Alepordio", "1kiks_" }) m.put(n, Region.EU_CENTRAL);
		for (String n : new String[] { "mikagoated", "Blvckwlf" }) m.put(n, Region.EU_CENTRAL);                                 // Germany
		for (String n : new String[] { "x_Kyran_x", "ninorc15" }) m.put(n, Region.EU_WEST);                                     // England
		m.put("Flowtives", Region.EU_EAST);                                                                                     // Bulgaria
		m.put("Coldified", Region.EU_SOUTH);                                                                                    // Spain
		m.put("MyDogIsConfused", Region.JAPAN);
		m.put("BLMcheat", Region.INDIA);
		m.put("ItzTwilightTT", Region.ASIA_SEA);                                                                                // Philippines
		return m;
	}

	private static final String REGIONS_VERSION = "3";

	/** Roster player to region label, for the website. */
	private static void publishRegions() {
		Map<String, String> m = new java.util.TreeMap<>();
		BOTS.forEach((k, v) -> m.put(k, v.label));
		WebStats.setRegions(m);
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("pvpchamps").resolve("regions.json");
		Map<String, String> read = null;
		try {
			Files.createDirectories(file.getParent());
			if (Files.exists(file)) {
				read = GSON.fromJson(Files.readString(file), new TypeToken<Map<String, String>>() {}.getType());
				if (read == null || !REGIONS_VERSION.equals(read.get("_version"))) {       // written by an older build: keep it aside and start from the new list
					Files.move(file, file.resolveSibling("regions.json.old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					read = null;
				}
			}
			if (read == null) {
				Map<String, String> out = new java.util.TreeMap<>();
				out.put("_version", REGIONS_VERSION);
				defaults().forEach((k, v) -> out.put(k, v.name()));
				Files.writeString(file, GSON.toJson(out));
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvpchamps] regions.json unreadable, using the built-in regions: " + e);
			read = null;
		}
		BOTS.clear();
		defaults().forEach((k, v) -> BOTS.put(k.toLowerCase(), v));
		if (read != null) read.forEach((k, v) -> { Region r = k.startsWith("_") ? null : Region.parse(v); if (r != null) BOTS.put(k.toLowerCase(), r); });
		publishRegions();
	}

	public static Region regionOf(String name, boolean human) {
		if (human) return HUMANS.getOrDefault(name.toLowerCase(), Region.NA_WEST);   // players default to California
		return BOTS.getOrDefault(name.toLowerCase(), Region.NA_EAST);
	}

	public static void setHumanRegion(String name, Region r) { HUMANS.put(name.toLowerCase(), r); }

	public static boolean enabled() { return enabled; }

	public static void setSpikes(boolean on) { spikes = on; }

	public static void setEnabled(boolean on) { enabled = on; }

	public static Map<String, Region> botRegions() { return BOTS; }

	// ----------------------------------------------------------------------- applying the ping

	/** The ping of one match: who is where, the host, and the lag put on the real players until {@link #end}. */
	public static final class Session {
		private final MinecraftServer server;
		private Region host;
		private final Map<String, Region> regionOf = new HashMap<>();
		private final Map<String, Integer> allPings = new HashMap<>();
		private final Map<String, Integer> pings = new HashMap<>();
		private final List<ServerPlayer> lagged = new ArrayList<>();

		Session(MinecraftServer server) { this.server = server; }

		/** "NA East (Virginia)", or null when the simulation is off. */
		public String serverLabel() { return host == null ? null : host.label; }

		/** "NA West (California) 25 ms" for one fighter, joined with " · " for a team; null when the simulation is off. */
		public String net(List<String> names) {
			List<String> out = new ArrayList<>();
			for (String n : names) {
				Integer ms = allPings.get(n);
				if (ms != null) out.add(regionOf.get(n).label.replaceAll(" \\(.*", "") + " " + ms + " ms");
			}
			return out.isEmpty() ? null : String.join(" · ", out);
		}

		private void resetShown(ServerPlayer p) {
			if (p == null || p.connection == null) return;
			((com.relay.pvpchamps.mixin.CommonListenerAccess) p.connection).pvpchamps$setLatency(0);
			server.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
				java.util.EnumSet.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), List.of(p)));
		}

		private final Map<java.util.UUID, Integer> humanPings = new HashMap<>();
		private int ticks;

		private final Map<String, java.util.ArrayDeque<net.minecraft.world.phys.Vec3>> trail = new HashMap<>();
		private final Map<String, Integer> nextSpike = new HashMap<>(), spikeEnd = new HashMap<>();

		private boolean spiking(String bot) { return spikeEnd.getOrDefault(bot, 0) > ticks; }

		/**
		 * Runs every tick while the match is on. Keeps the ping in the tab list at the simulated ping, for the bots (they have no
		 * connection of their own, so nothing else would ever set it) and for the real players (Minecraft measures it only
		 * every 15 seconds, and never for the host of a singleplayer or LAN world, whose ping always reads 0). Also gives a
		 * bot with a high ping the odd lag spike: it rubber-bands back to where it was a few ticks ago and its ping jumps.
		 * Real players' spikes are in their connection ({@link Lag}).
		 */
		public void sync(boolean fighting) {
			if (!enabled) return;
			ticks++;
			boolean refresh = ticks % 20 == 1;
			for (Map.Entry<String, Integer> e : pings.entrySet()) {
				String bot = e.getKey();
				ServerPlayer p = vexbot.api.VexBotApi.resolveBot(server, bot);
				if (p == null || !p.isAlive()) continue;
				var tr = trail.computeIfAbsent(bot, k -> new java.util.ArrayDeque<>());
				tr.addFirst(p.position());
				if (tr.size() > 14) tr.removeLast();
				if (!fighting || !spikes || e.getValue() < 60) continue;
				if (!nextSpike.containsKey(bot)) nextSpike.put(bot, ticks + 100 + rng.nextInt(300));
				if (ticks >= nextSpike.get(bot)) {
					int back = Math.min(tr.size() - 1, 5 + rng.nextInt(8));       // 0.25 to 0.65 s back
					if (back > 2) {
						net.minecraft.world.phys.Vec3 at = new java.util.ArrayList<>(tr).get(back);
						p.teleportTo((net.minecraft.server.level.ServerLevel) p.level(), at.x, at.y, at.z, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
						p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
					}
					spikeEnd.put(bot, ticks + 6 + rng.nextInt(14));
					nextSpike.put(bot, ticks + (int) ((160 + rng.nextInt(500)) / (1.0 + e.getValue() / 150.0)));
					refresh = true;
				}
			}
			if (!refresh) return;
			for (Map.Entry<String, Integer> e : pings.entrySet())
				showPing(vexbot.api.VexBotApi.resolveBot(server, e.getKey()), e.getValue() * (spiking(e.getKey()) ? 3 : 1));
			for (Map.Entry<java.util.UUID, Integer> e : humanPings.entrySet()) {
				ServerPlayer hp = server.getPlayerList().getPlayer(e.getKey());
				Lag l = hp == null ? null : lagOf(hp);
				showPing(hp, l == null ? e.getValue() : (int) (e.getValue() * l.multiplier()));
			}
		}

		private void showPing(ServerPlayer p, int base) {
			if (p == null || p.connection == null) return;
			int ms = Math.max(1, (int) Math.round(base * (1.0 + (rng.nextDouble() - 0.5) * 0.12)));   // a little jitter so it reads like a real connection
			try {
				((com.relay.pvpchamps.mixin.CommonListenerAccess) p.connection).pvpchamps$setLatency(ms);
				server.getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(
					java.util.EnumSet.of(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), List.of(p)));
			} catch (RuntimeException ex) {
				System.err.println("[pvpchamps] could not set " + p.getGameProfile().name() + "'s ping: " + ex);
			}
		}

		private final Random rng = new Random();

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
			for (String bot : pings.keySet()) resetShown(vexbot.api.VexBotApi.resolveBot(server, bot));
			for (java.util.UUID id : humanPings.keySet()) resetShown(server.getPlayerList().getPlayer(id));
			humanPings.clear();
		}
	}

	/**
	 * Starts the ping for a match. {@code fighters} are every fighter's name; {@code humans} the real players among them.
	 * Announces the host and everyone's ping through {@code say}.
	 */
	public static Session begin(MinecraftServer server, List<String> fighters, List<ServerPlayer> humans, Consumer<String> say) {
		Session s = new Session(server);
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
		s.host = host;
		StringBuilder sb = new StringBuilder("§7Server: " + host.label + " (closest to everyone) · ping");
		for (String n : fighters) {
			int ping = rtt(by.get(n), host);
			s.regionOf.put(n, by.get(n));
			s.allPings.put(n, ping);
			ServerPlayer hp = humans.stream().filter(h -> h.getGameProfile().name().equalsIgnoreCase(n)).findFirst().orElse(null);
			if (hp != null) { setLag(hp, ping); s.lagged.add(hp); s.humanPings.put(hp.getUUID(), ping); }
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

	private static Lag lagOf(ServerPlayer p) {
		try { return channelOf(p).pipeline().get(HANDLER) instanceof Lag l ? l : null; } catch (RuntimeException e) { return null; }
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
			if (p.connection == null) continue;
			int added = 0;
			boolean bot = vexbot.api.VexBotApi.isBot(server, p.getGameProfile().name());
			if (!bot) try { if (channelOf(p).pipeline().get(HANDLER) instanceof Lag l) added = l.oneWayMs * 2; } catch (RuntimeException ignored) { continue; }
			out.add(p.getGameProfile().name() + (bot ? " (bot)" : "") + ": latency " + p.connection.latency() + " ms" + (bot ? "" : ", simulated ping " + added + " ms"));
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
	 * Delays every packet in and out by half the ping (plus or minus 12% jitter). Each direction has its own queue that is
	 * drained strictly in order, so a packet is never delivered before one sent earlier: reordering packets is what makes
	 * a client disconnect with a network protocol error.
	 */
	private static final class Lag extends ChannelDuplexHandler {
		volatile int oneWayMs;
		private final Random rng = new Random();
		private long spikeUntil, nextSpike = System.nanoTime() + (5_000L + new Random().nextInt(15_000)) * 1_000_000L;
		private volatile double spikeMul = 1;
		private final java.util.ArrayDeque<Item> in = new java.util.ArrayDeque<>(), out = new java.util.ArrayDeque<>();
		private long lastIn, lastOut;
		private boolean pumpIn, pumpOut;

		private record Item(Object msg, ChannelPromise promise, long due) {}   // promise null: a flush (outbound) or read-complete (inbound)

		Lag(int oneWayMs) { this.oneWayMs = oneWayMs; }

		/** How much the delay is multiplied right now: 1, or 2.5 to 6 during a lag spike. */
		double multiplier() { return System.nanoTime() < spikeUntil ? spikeMul : 1; }

		private long due(long previous) {
			long now = System.nanoTime();
			if (spikes && oneWayMs >= 8 && now >= nextSpike) {          // a lag spike: the delay jumps for a fraction of a second, then drops
				spikeUntil = now + (250L + rng.nextInt(1100)) * 1_000_000L;
				spikeMul = 2.5 + rng.nextDouble() * 3.5;
				nextSpike = spikeUntil + (long) ((8_000L + rng.nextInt(25_000)) / (1.0 + oneWayMs * 2 / 150.0)) * 1_000_000L;   // higher ping, more often
			}
			double jitter = 1.0 + (rng.nextDouble() - 0.5) * 0.24;
			return Math.max(previous, now + (long) (oneWayMs * jitter * multiplier() * 1_000_000L));
		}

		private void queue(ChannelHandlerContext ctx, java.util.ArrayDeque<Item> q, Item item, boolean inbound) {
			q.add(item);
			boolean running = inbound ? pumpIn : pumpOut;
			if (!running) schedule(ctx, q, inbound);
		}

		private void schedule(ChannelHandlerContext ctx, java.util.ArrayDeque<Item> q, boolean inbound) {
			Item head = q.peek();
			if (head == null) { if (inbound) pumpIn = false; else pumpOut = false; return; }
			if (inbound) pumpIn = true; else pumpOut = true;
			ctx.executor().schedule(() -> drain(ctx, q, inbound), Math.max(0, head.due() - System.nanoTime()), TimeUnit.NANOSECONDS);
		}

		private void drain(ChannelHandlerContext ctx, java.util.ArrayDeque<Item> q, boolean inbound) {
			long now = System.nanoTime();
			for (Item it; (it = q.peek()) != null && it.due() <= now; ) {
				q.poll();
				if (inbound) { if (it.msg() == null) ctx.fireChannelReadComplete(); else ctx.fireChannelRead(it.msg()); }
				else if (it.msg() == null) ctx.flush();
				else ctx.write(it.msg(), it.promise());
			}
			schedule(ctx, q, inbound);
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			lastIn = due(lastIn);
			queue(ctx, in, new Item(msg, null, lastIn), true);
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) {
			lastIn = due(lastIn);
			queue(ctx, in, new Item(null, null, lastIn), true);
		}

		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
			lastOut = due(lastOut);
			queue(ctx, out, new Item(msg, promise, lastOut), false);
		}

		@Override
		public void flush(ChannelHandlerContext ctx) {
			lastOut = due(lastOut);
			queue(ctx, out, new Item(null, null, lastOut), false);
		}
	}
}
