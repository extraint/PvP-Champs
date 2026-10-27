package com.relay.pvpchamps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Live match board and player stats, served as a small website straight from the game server
 * (default http://localhost:8080, port in config/pvpchamps/web.json). Matches report in from {@link Match};
 * stats are kept in config/pvpchamps/stats.json.
 */
public final class WebStats {
	private WebStats() {}

	public static final class Live {
		public String id, a, b, kit, round, state;
		/** Where the match is hosted and each side's region and ping, e.g. "NA West 25 ms" (null when ping simulation is off). */
		public String server, netA, netB;
		public int scoreA, scoreB, bestOf;
		public double hpA, hpB;
		public Gear gearA, gearB;
		public long startedAt;
	}

	/** What one fighter is wearing and carrying right now, for the website. */
	public static final class Gear {
		/** head, chest, legs, feet: item id (null if empty) and remaining durability 0-100. */
		public String[] armor = new String[4];
		public int[] armorDura = new int[4];
		public List<String> hotbar = new ArrayList<>();                 // "netherite_sword" or "golden_apple x64", slot order
		public Map<String, Integer> materials = new TreeMap<>();        // everything carried, totals per item
		public String offhand;
	}

	public static Gear gearOf(net.minecraft.server.level.ServerPlayer p) {
		Gear g = new Gear();
		if (p == null) return g;
		net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			var s = p.getItemBySlot(slots[i]);
			if (s.isEmpty()) continue;
			g.armor[i] = id(s);
			g.armorDura[i] = s.getMaxDamage() <= 0 ? 100 : Math.round(100f * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage());
		}
		var off = p.getOffhandItem();
		if (!off.isEmpty()) g.offhand = id(off) + (off.getCount() > 1 ? " x" + off.getCount() : "");
		for (int i = 0; i < 36; i++) {
			var s = p.getInventory().getItem(i);
			if (s.isEmpty()) { if (i < 9) g.hotbar.add(""); continue; }
			String name = id(s);
			if (i < 9) g.hotbar.add(s.getCount() > 1 ? name + " x" + s.getCount() : name);
			g.materials.merge(name, s.getCount(), Integer::sum);
		}
		if (!off.isEmpty()) g.materials.merge(id(off), off.getCount(), Integer::sum);
		return g;
	}

	private static String id(net.minecraft.world.item.ItemStack s) {
		String base = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		var pc = s.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		if (pc != null && pc.potion().isPresent()) return base + ":" + pc.potion().get().getRegisteredName().replace("minecraft:", "");
		return base;
	}

	public static final class Record {
		public int matchesWon, matchesLost, roundsWon, roundsLost;
		public Map<String, int[]> kits = new TreeMap<>(); // kit label -> {rounds won, rounds lost}
		public long lastPlayed;
	}

	public static final class Done {
		public String winner, loser, kit, event;
		public int winnerScore, loserScore;
		public long endedAt;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Live> LIVE = new ConcurrentHashMap<>();
	private static final Deque<Done> HISTORY = new ArrayDeque<>();
	private static final Map<String, Record> STATS = new ConcurrentHashMap<>();
	private static java.net.ServerSocket http;
	private static volatile Object bracket;
	private static Path file;

	public static void setBracket(Object b) { bracket = b; }

	private static volatile Map<String, String> regions = Map.of();
	private static final Map<String, java.util.function.Supplier<Object>> EXTRA = new java.util.concurrent.ConcurrentHashMap<>();

	/** Serves another JSON document at {@code path} (rankings, feed, highlights, predictions). */
	public static void provide(String path, java.util.function.Supplier<Object> data) { EXTRA.put(path, data); }

	/** Roster player to region label, for the leaderboard. */
	public static void setRegions(Map<String, String> r) { regions = r; }

	/** "2-1" style score of the latest finished match won by this player, or null (a skipped series has none). */
	public static synchronized String lastScoreFor(String winner) {
		Done d = HISTORY.peekFirst();
		return d != null && d.winner.equals(winner) && System.currentTimeMillis() - d.endedAt < 5000 ? d.winnerScore + "-" + d.loserScore : null;
	}

	private static volatile int boundPort;
	private static volatile String publicUrl = "";
	private static final java.util.concurrent.Semaphore CONNECTIONS = new java.util.concurrent.Semaphore(120);   // a public site must not be able to starve the game of threads

	/** The address to give people: the configured public one (a tunnel or domain), else this machine's. */
	public static String url() { return publicUrl.isEmpty() ? "http://localhost:" + boundPort : publicUrl; }

	/** Where the site can be reached from other machines on the same network. */
	public static String lanUrl() {
		try { return "http://" + java.net.InetAddress.getLocalHost().getHostAddress() + ":" + boundPort; } catch (java.net.UnknownHostException e) { return null; }
	}

	public static String publicUrlSetting() { return publicUrl; }

	/** The port the site really got, or 0 if it is off. */
	public static int port() { return boundPort; }

	// ------------------------------------------------------------------ reporting (called from the server thread)

	public static void live(Live l) { LIVE.put(l.id, l); }

	public static void endLive(String id) { LIVE.remove(id); }

	public static synchronized void round(String winner, String loser, String kit) {
		rec(winner).roundsWon++;
		rec(loser).roundsLost++;
		rec(winner).kits.computeIfAbsent(kit, k -> new int[2])[0]++;
		rec(loser).kits.computeIfAbsent(kit, k -> new int[2])[1]++;
		touch(winner, loser);
		save();
	}

	public static synchronized void match(String winner, String loser, String kit, String event, int ws, int ls) {
		rec(winner).matchesWon++;
		rec(loser).matchesLost++;
		touch(winner, loser);
		Done d = new Done();
		d.winner = winner; d.loser = loser; d.kit = kit; d.event = event; d.winnerScore = ws; d.loserScore = ls;
		d.endedAt = System.currentTimeMillis();
		HISTORY.addFirst(d);
		while (HISTORY.size() > 50) HISTORY.removeLast();
		save();
	}

	/** Matches won and lost so far, for the forecast's "form". */
	public static synchronized int[] matchRecord(String name) {
		Record r = STATS.get(name);
		return r == null ? new int[] {0, 0} : new int[] {r.matchesWon, r.matchesLost};
	}

	private static Record rec(String name) { return STATS.computeIfAbsent(name, k -> new Record()); }

	private static void touch(String a, String b) {
		long now = System.currentTimeMillis();
		rec(a).lastPlayed = now;
		rec(b).lastPlayed = now;
	}

	// ------------------------------------------------------------------ persistence

	private static void load() {
		try {
			if (Files.exists(file)) {
				JsonObject o = GSON.fromJson(Files.readString(file), JsonObject.class);
				if (o.has("players")) {
					var type = new com.google.gson.reflect.TypeToken<Map<String, Record>>() {}.getType();
					Map<String, Record> m = GSON.fromJson(o.get("players"), type);
					if (m != null) STATS.putAll(m);
				}
				if (o.has("history")) {
					var type = new com.google.gson.reflect.TypeToken<List<Done>>() {}.getType();
					List<Done> l = GSON.fromJson(o.get("history"), type);
					if (l != null) HISTORY.addAll(l);
				}
			}
		} catch (Exception e) {
			System.err.println("[pvpchamps] could not read stats.json: " + e);
		}
	}

	private static synchronized void save() {
		try {
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("players", new TreeMap<>(STATS));
			out.put("history", new ArrayList<>(HISTORY));
			Files.writeString(file, GSON.toJson(out));
		} catch (IOException e) {
			System.err.println("[pvpchamps] could not write stats.json: " + e);
		}
	}

	// ------------------------------------------------------------------ the website

	public static synchronized void start() {
		if (http != null) return; // already up (started when the game launched)
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("pvpchamps");
		int port = 8080;
		try {
			Files.createDirectories(dir);
			file = dir.resolve("stats.json");
			load();
			Path cfg = dir.resolve("web.json");
			if (!Files.exists(cfg)) Files.writeString(cfg, "{\n  \"enabled\": true,\n  \"port\": 8080,\n  \"publicUrl\": \"\"\n}\n");
			JsonObject c = GSON.fromJson(Files.readString(cfg), JsonObject.class);
			if (c.has("enabled") && !c.get("enabled").getAsBoolean()) return;
			if (c.has("port")) port = c.get("port").getAsInt();
			if (c.has("publicUrl")) publicUrl = c.get("publicUrl").getAsString().trim().replaceAll("/+$", "");   // e.g. https://pvpchamps.example.org once a tunnel or domain points at the port
			for (int p = port; http == null && p < port + 10; p++) { // 8080 is often taken by something else: try the next ports
				try { http = new java.net.ServerSocket(p, 20, null); boundPort = p; }
				catch (java.net.BindException e) { System.err.println("[pvpchamps] port " + p + " is busy, trying the next one"); }
			}
			if (http == null) throw new IOException("no free port from " + port + " to " + (port + 9));
			final java.net.ServerSocket srv = http;
			Thread acceptor = new Thread(() -> {
				while (!srv.isClosed()) {
					try {
						java.net.Socket sock = srv.accept();
						if (!CONNECTIONS.tryAcquire()) { sock.close(); continue; }
						Thread t = new Thread(() -> { try { serve(sock); } finally { CONNECTIONS.release(); } }, "pvpchamps-web-conn");
						t.setDaemon(true);
						t.start();
					} catch (IOException e) { if (srv.isClosed()) return; }
				}
			}, "pvpchamps-web");
			acceptor.setDaemon(true);
			acceptor.start();
			System.out.println("[pvpchamps] stats website on http://localhost:" + boundPort);
		} catch (Throwable e) {
			System.err.println("[pvpchamps] stats website could not start: " + e);
		}
	}

	public static void stop() {
		try { if (http != null) http.close(); } catch (IOException ignored) {}
		http = null;
	}

	/** Plain-socket HTTP: the game's bundled Java may not ship the jdk.httpserver module, so nothing outside java.base is used. */
	private static void serve(java.net.Socket sock) {
		try (sock) {
			sock.setSoTimeout(5000);
			var in = new java.io.BufferedReader(new java.io.InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1));
			String line = in.readLine();
			if (line == null) return;
			int headerLines = 0;
			for (String h = in.readLine(); h != null && !h.isEmpty(); h = in.readLine()) { if (++headerLines > 100) return; /* skip headers, but not for ever */ }
			String[] parts = line.split(" ");
			String path = parts.length > 1 ? parts[1] : "/";
			int q = path.indexOf('?');
			if (q >= 0) path = path.substring(0, q);
			int code = 200;
			String type = "application/json";
			byte[] body;
			switch (path) {
				case "/api/live" -> body = GSON.toJson(new ArrayList<>(LIVE.values())).getBytes(StandardCharsets.UTF_8);
				case "/api/rankings", "/api/feed", "/api/highlights", "/api/predictions" -> {
					java.util.function.Supplier<Object> s = EXTRA.get(path);
					body = GSON.toJson(s == null ? Map.of() : s.get()).getBytes(StandardCharsets.UTF_8);
				}
				case "/api/regions" -> body = GSON.toJson(regions).getBytes(StandardCharsets.UTF_8);
				case "/api/bracket" -> body = GSON.toJson(bracket == null ? Map.of() : bracket).getBytes(StandardCharsets.UTF_8);
				case "/api/stats" -> {
					Map<String, Object> out = new LinkedHashMap<>();
					synchronized (WebStats.class) {
						out.put("players", new TreeMap<>(STATS));
						out.put("history", new ArrayList<>(HISTORY));
					}
					body = GSON.toJson(out).getBytes(StandardCharsets.UTF_8);
				}
				case "/", "/index.html" -> {
					type = "text/html; charset=utf-8";
					try (InputStream page = WebStats.class.getResourceAsStream("/web/index.html")) {
						body = page == null ? "missing page".getBytes(StandardCharsets.UTF_8) : page.readAllBytes();
					}
				}
				default -> {
					// Your own kit renders: drop PNGs in config/pvpchamps/web/kits/ (cart.png, uhc.png, ...) and they are served at /kits/<name>.png
					java.nio.file.Path img = path.startsWith("/kits/") && path.endsWith(".png") && !path.contains("..")
						? FabricLoader.getInstance().getConfigDir().resolve("pvpchamps/web" + path) : null;
					byte[] bundled = null;
					if (img != null && !Files.isRegularFile(img)) {
						try (InputStream r = WebStats.class.getResourceAsStream("/web" + path)) { if (r != null) bundled = r.readAllBytes(); }
					}
					if (img != null && Files.isRegularFile(img)) { type = "image/png"; body = Files.readAllBytes(img); }
					else if (bundled != null) { type = "image/png"; body = bundled; }
					else { code = 404; type = "text/plain"; body = "not found".getBytes(StandardCharsets.UTF_8); }
				}
			}
			String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") + "\r\nContent-Type: " + type
				+ "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n";
			var os = sock.getOutputStream();
			os.write(head.getBytes(StandardCharsets.ISO_8859_1));
			os.write(body);
			os.flush();
		} catch (IOException ignored) {
		}
	}
}
