package com.relay.pvphub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/** Everyone's ratings and records, saved to config/pvphub/players.json on every change. */
public final class Stats {
	private Stats() {}

	public static final class KitRecord {
		public double elo = 1000;
		public int wins, losses, streak, bestStreak;
	}

	public static final class Player {
		public String name;
		public Map<String, KitRecord> kits = new TreeMap<>();
		public int ffaKills, ffaDeaths, ffaStreak, ffaBest;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final double K = 32;
	private static final Map<String, Player> PLAYERS = new TreeMap<>();   // by uuid

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("pvphub").resolve("players.json"); }

	public static synchronized void load() {
		try {
			if (!Files.exists(file())) return;
			Map<String, Player> read = GSON.fromJson(Files.readString(file()), new TypeToken<Map<String, Player>>() {}.getType());
			if (read != null) { PLAYERS.clear(); PLAYERS.putAll(read); }
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvphub] players.json unreadable: " + e);
		}
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(PLAYERS));
		} catch (IOException e) {
			System.err.println("[pvphub] could not write players.json: " + e);
		}
	}

	public static synchronized Player of(UUID id, String name) {
		Player p = PLAYERS.computeIfAbsent(id.toString(), k -> new Player());
		p.name = name;
		return p;
	}

	public static synchronized KitRecord record(UUID id, String name, Kit kit) {
		return of(id, name).kits.computeIfAbsent(kit.id, k -> new KitRecord());
	}

	public static synchronized double elo(UUID id, String name, Kit kit) { return record(id, name, kit).elo; }

	/** The result of a ranked duel: standard Elo with K=32. Returns {winnerChange, loserChange}. */
	public static synchronized double[] ranked(UUID win, String winName, UUID lose, String loseName, Kit kit) {
		KitRecord w = record(win, winName, kit), l = record(lose, loseName, kit);
		double expected = 1.0 / (1.0 + Math.pow(10, (l.elo - w.elo) / 400.0));
		double gain = K * (1 - expected);
		w.elo += gain; l.elo -= gain;
		w.wins++; l.losses++;
		w.streak++; w.bestStreak = Math.max(w.bestStreak, w.streak);
		l.streak = 0;
		save();
		return new double[] {gain, -gain};
	}

	/** A casual (unranked or requested) duel: wins and losses count, the rating does not move. */
	public static synchronized void casual(UUID win, String winName, UUID lose, String loseName, Kit kit) {
		KitRecord w = record(win, winName, kit), l = record(lose, loseName, kit);
		w.wins++; l.losses++;
		save();
	}

	public static synchronized void ffaKill(UUID killer, String killerName, UUID victim, String victimName) {
		Player k = of(killer, killerName), v = of(victim, victimName);
		k.ffaKills++; k.ffaStreak++; k.ffaBest = Math.max(k.ffaBest, k.ffaStreak);
		v.ffaDeaths++; v.ffaStreak = 0;
		save();
	}

	public static synchronized void ffaDeath(UUID victim, String victimName) {
		Player v = of(victim, victimName);
		v.ffaDeaths++; v.ffaStreak = 0;
		save();
	}

	public static synchronized int ffaStreak(UUID id, String name) { return of(id, name).ffaStreak; }

	/** Rank names from the rating, like the tier names of a ranked ladder. */
	public static String rankName(double elo) {
		if (elo >= 1800) return "Champion";
		if (elo >= 1600) return "Diamond";
		if (elo >= 1400) return "Platinum";
		if (elo >= 1200) return "Gold";
		if (elo >= 1050) return "Silver";
		return "Bronze";
	}

	public static synchronized List<Map.Entry<String, KitRecord>> leaderboard(Kit kit, int n) {
		List<Map.Entry<String, KitRecord>> rows = new ArrayList<>();
		for (Player p : PLAYERS.values()) {
			KitRecord r = p.kits.get(kit.id);
			if (r != null && r.wins + r.losses > 0) rows.add(Map.entry(p.name, r));
		}
		rows.sort(Comparator.comparingDouble((Map.Entry<String, KitRecord> e) -> e.getValue().elo).reversed());
		return rows.subList(0, Math.min(n, rows.size()));
	}

	public static synchronized List<Player> ffaLeaderboard(int n) {
		List<Player> rows = new ArrayList<>(PLAYERS.values());
		rows.removeIf(p -> p.ffaKills + p.ffaDeaths == 0);
		rows.sort(Comparator.comparingInt((Player p) -> p.ffaKills).reversed());
		return rows.subList(0, Math.min(n, rows.size()));
	}
}
