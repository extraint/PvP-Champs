package com.relay.pvpchamps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Tiers for real players. Every real player has an Elo-style rating per kit that their results move. The roster bots keep
 * their fixed tiers, turned into the same scale ({@code 800 + 25 x MCTiers points}), so beating a high-tier bot is worth
 * more than beating a low-tier one. A rating maps back to the nearest tier (LT5 up to HT1). A tier test promotes you outright.
 */
public final class Rankings {
	private Rankings() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final double START = 1000, K = 32;

	private static final class Player {
		String name;
		Map<String, Double> rating = new TreeMap<>();   // kit id to rating
		Map<String, Integer> games = new TreeMap<>();
	}

	private static final Map<String, Player> PLAYERS = new TreeMap<>();   // lower-case name

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("pvpchamps").resolve("players.json"); }

	public static synchronized void load() {
		try {
			if (!Files.exists(file())) return;
			Map<String, Player> read = GSON.fromJson(Files.readString(file()), new TypeToken<Map<String, Player>>() {}.getType());
			if (read != null) { PLAYERS.clear(); PLAYERS.putAll(read); }
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvpchamps] players.json unreadable: " + e);
		}
		WebStats.provide("/api/rankings", Rankings::snapshot);
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(PLAYERS));
		} catch (IOException e) {
			System.err.println("[pvpchamps] could not write players.json: " + e);
		}
	}

	public static boolean isBot(String name) { return Roster.byName(name) != null; }

	static double eloOfPoints(int points) { return 800 + 25.0 * points; }

	/** The rating of anyone in a kit: a bot's from its tier, a player's from their results (1000 before they have played). */
	public static synchronized double rating(String name, Kits kit) {
		if (isBot(name)) return eloOfPoints(Tiers.points(Tiers.tier(name, kit)));
		Player p = PLAYERS.get(name.toLowerCase());
		return p == null ? START : p.rating.getOrDefault(kit.id, START);
	}

	public static synchronized int games(String name, Kits kit) {
		Player p = PLAYERS.get(name.toLowerCase());
		return p == null ? 0 : p.games.getOrDefault(kit.id, 0);
	}

	/** The nearest tier for a rating, or "Unranked" for a player with no games in the kit. */
	public static String tierOf(String name, Kits kit) {
		if (isBot(name)) return Tiers.tier(name, kit);
		if (games(name, kit) == 0) return "Unranked";
		return tierForRating(rating(name, kit));
	}

	static String tierForRating(double elo) {
		double pts = (elo - 800) / 25.0;
		String best = "LT5";
		double bestGap = Double.MAX_VALUE;
		for (String t : Tiers.order()) {
			double gap = Math.abs(Tiers.points(t) - pts);
			if (gap < bestGap) { bestGap = gap; best = t; }
		}
		return best;
	}

	/** A series ended: move the ratings of the real players on each side. Bots' tiers never change. */
	public static synchronized void record(List<String> winners, List<String> losers, Kits kit) {
		double w = avg(winners, kit), l = avg(losers, kit);
		double expectedWin = 1.0 / (1.0 + Math.pow(10, (l - w) / 400.0));
		double scale = winners.size() > 1 || losers.size() > 1 ? 0.7 : 1.0;      // a team result is shared between two players
		for (String n : winners) apply(n, kit, K * scale * (1 - expectedWin));
		for (String n : losers) apply(n, kit, -K * scale * expectedWin);
		save();
	}

	private static double avg(List<String> names, Kits kit) {
		double sum = 0;
		for (String n : names) sum += rating(n, kit);
		return sum / names.size();
	}

	private static void apply(String name, Kits kit, double delta) {
		if (isBot(name)) return;
		Player p = PLAYERS.computeIfAbsent(name.toLowerCase(), k -> { Player x = new Player(); x.name = name; return x; });
		p.name = name;
		p.rating.merge(kit.id, START + delta, (old, d) -> old + delta);
		p.games.merge(kit.id, 1, Integer::sum);
	}

	/** Passing a tier test sets the player at least at that tier. */
	public static synchronized void promote(String name, Kits kit, String tier) {
		Player p = PLAYERS.computeIfAbsent(name.toLowerCase(), k -> { Player x = new Player(); x.name = name; return x; });
		p.name = name;
		double target = eloOfPoints(Tiers.points(tier));
		if (p.rating.getOrDefault(kit.id, START) < target) p.rating.put(kit.id, target);
		p.games.merge(kit.id, 1, Integer::sum);
		save();
	}

	/** The tier a player should test for next in a kit: the first one above their current rating (LT5 when unranked). */
	public static synchronized String nextTier(String name, Kits kit) {
		if (games(name, kit) == 0) return "LT5";
		double r = rating(name, kit);
		List<String> up = new ArrayList<>(Tiers.order());
		java.util.Collections.reverse(up);                          // worst to best
		for (String t : up) if (eloOfPoints(Tiers.points(t)) > r + 5) return t;
		return "HT1";
	}

	/** A roster bot to test against: the one whose tier in the kit is closest to (and not below) the target, among those who play it. */
	public static Roster.Opponent examiner(Kits kit, String tier) {
		int want = Tiers.points(tier);
		Roster.Opponent best = null;
		int bestGap = Integer.MAX_VALUE;
		for (Roster.Opponent o : Roster.all()) {
			if (!o.kits().contains(kit.id)) continue;
			int pts = Tiers.points(Tiers.tier(o.name(), kit));
			int gap = pts >= want ? pts - want : 1000 + (want - pts);   // never below the target if someone at or above exists
			if (gap < bestGap) { bestGap = gap; best = o; }
		}
		return best;
	}

	public static synchronized List<String> describe(String name) {
		List<String> out = new ArrayList<>();
		Player p = PLAYERS.get(name.toLowerCase());
		for (Kits k : Draft.POOL) {
			int g = p == null ? 0 : p.games.getOrDefault(k.id, 0);
			out.add(k.label + ": " + (g == 0 ? "Unranked" : tierForRating(p.rating.get(k.id)) + " (" + Math.round(p.rating.get(k.id)) + ", " + g + " games)"));
		}
		return out;
	}

	/** For the website: every real player with their tier and rating per kit. */
	private static synchronized Object snapshot() {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Player p : PLAYERS.values()) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("name", p.name);
			Map<String, Object> kits = new LinkedHashMap<>();
			int best = 0;
			for (Kits k : Draft.POOL) {
				Double r = p.rating.get(k.id);
				if (r == null) continue;
				String tier = tierForRating(r);
				kits.put(k.label, Map.of("tier", tier, "rating", Math.round(r), "games", p.games.getOrDefault(k.id, 0)));
				best = Math.max(best, Tiers.points(tier));
			}
			row.put("kits", kits);
			row.put("points", best);
			out.add(row);
		}
		out.sort((a, b) -> Integer.compare((int) b.get("points"), (int) a.get("points")));
		return out;
	}
}
