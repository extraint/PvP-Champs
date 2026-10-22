package com.relay.pvpchamps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
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
		public int scoreA, scoreB, bestOf;
		public double hpA, hpB;
		public long startedAt;
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
	private static HttpServer http;
	private static Path file;
	private static volatile int boundPort;

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
			if (!Files.exists(cfg)) Files.writeString(cfg, "{\n  \"enabled\": true,\n  \"port\": 8080\n}\n");
			JsonObject c = GSON.fromJson(Files.readString(cfg), JsonObject.class);
			if (c.has("enabled") && !c.get("enabled").getAsBoolean()) return;
			if (c.has("port")) port = c.get("port").getAsInt();
			for (int p = port; http == null && p < port + 10; p++) { // 8080 is often taken by something else: try the next ports
				try { http = HttpServer.create(new InetSocketAddress(p), 0); boundPort = p; }
				catch (java.net.BindException e) { System.err.println("[pvpchamps] port " + p + " is busy, trying the next one"); }
			}
			if (http == null) throw new IOException("no free port from " + port + " to " + (port + 9));
			http.createContext("/", ex -> {
				String path = ex.getRequestURI().getPath();
				byte[] body;
				String type = "application/json";
				switch (path) {
					case "/api/live" -> body = GSON.toJson(new ArrayList<>(LIVE.values())).getBytes(StandardCharsets.UTF_8);
					case "/api/stats" -> {
						Map<String, Object> out = new LinkedHashMap<>();
						out.put("players", new TreeMap<>(STATS));
						out.put("history", new ArrayList<>(HISTORY));
						body = GSON.toJson(out).getBytes(StandardCharsets.UTF_8);
					}
					case "/", "/index.html" -> {
						type = "text/html; charset=utf-8";
						try (InputStream in = WebStats.class.getResourceAsStream("/web/index.html")) {
							body = in == null ? "missing page".getBytes() : in.readAllBytes();
						}
					}
					default -> {
						ex.sendResponseHeaders(404, -1);
						ex.close();
						return;
					}
				}
				ex.getResponseHeaders().set("Content-Type", type);
				ex.getResponseHeaders().set("Cache-Control", "no-store");
				ex.sendResponseHeaders(200, body.length);
				try (var os = ex.getResponseBody()) { os.write(body); }
			});
			http.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "pvpchamps-web"); t.setDaemon(true); return t; }));
			http.start();
			System.out.println("[pvpchamps] stats website on http://localhost:" + boundPort);
		} catch (Exception e) {
			System.err.println("[pvpchamps] stats website could not start: " + e);
		}
	}

	public static void stop() {
		if (http != null) { http.stop(0); http = null; }
	}
}
