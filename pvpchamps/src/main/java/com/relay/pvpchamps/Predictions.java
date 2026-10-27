package com.relay.pvpchamps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Predict who wins the running series before its first round ends. Anyone who is not fighting in it can call a side; a right
 * call is worth 10 points. The points are kept per player and shown on the website.
 */
public final class Predictions {
	private Predictions() {}

	private static final class Record {
		String name;
		int points, correct, total;
	}

	public static final int REWARD = 10;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Record> LEDGER = new TreeMap<>();   // lower-case name
	private static List<List<String>> sides = List.of();
	private static boolean open;
	private static String match = "";
	private static final Map<String, Integer> votes = new HashMap<>();   // lower-case name to side index
	private static double probA = 0.5;
	/** The forecast takes part like a player: it backs the favourite of every series and is paid like anyone else. */
	public static final String FORECAST = "Forecast (AI)";

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("pvpchamps").resolve("predictions.json"); }

	public static synchronized void load() {
		try {
			if (Files.exists(file())) {
				Map<String, Record> read = GSON.fromJson(Files.readString(file()), new TypeToken<Map<String, Record>>() {}.getType());
				if (read != null) { LEDGER.clear(); LEDGER.putAll(read); }
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvpchamps] predictions.json unreadable: " + e);
		}
		WebStats.provide("/api/predictions", Predictions::snapshot);
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(LEDGER));
		} catch (IOException e) {
			System.err.println("[pvpchamps] could not write predictions.json: " + e);
		}
	}

	private static String label(List<String> side) { return String.join(" + ", side); }

	/** A series begins: open predictions and tell everyone who is not in it. */
	public static synchronized void start(MinecraftServer server, List<List<String>> bothSides, String description, Kits kit, int bestOf) {
		sides = bothSides;
		open = true;
		match = description;
		votes.clear();
		probA = Forecast.seriesWin(bothSides.get(0), bothSides.get(1), kit, bestOf);
		votes.put(FORECAST.toLowerCase(), probA >= 0.5 ? 0 : 1);
		String fav = label(bothSides.get(probA >= 0.5 ? 0 : 1));
		Component f = Component.literal("§d🔮 Forecast: §f" + label(bothSides.get(0)) + " " + Math.round(probA * 100) + "% §d·§f " + label(bothSides.get(1)) + " " + Math.round((1 - probA) * 100)
			+ "% §d(" + fav + " favoured, " + kit.label + ")");
		for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!vexbot.api.VexBotApi.isBot(server, p.getGameProfile().name())) p.sendSystemMessage(f);
		Component c = Component.literal("§d🔮 Predict the winner of " + label(sides.get(0)) + " vs " + label(sides.get(1)) + ": §f/pvpchamps predict <name> §d(" + REWARD + " points, before round 1 ends)");
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String n = p.getGameProfile().name();
			boolean fighting = bothSides.stream().anyMatch(s -> s.stream().anyMatch(x -> x.equalsIgnoreCase(n)));
			if (!fighting && !vexbot.api.VexBotApi.isBot(server, n)) p.sendSystemMessage(c);
		}
	}

	/** The first round is over: no more predictions for this series. */
	public static synchronized void close() { open = false; }

	/** Returns an error message, or null when the prediction was taken. */
	public static synchronized String predict(ServerPlayer p, String who) {
		if (sides.isEmpty()) return "No series to predict right now.";
		if (!open) return "Predictions for this series are closed.";
		String me = p.getGameProfile().name();
		if (sides.stream().anyMatch(s -> s.stream().anyMatch(x -> x.equalsIgnoreCase(me)))) return "You are fighting in this one.";
		int side = -1;
		for (int i = 0; i < sides.size(); i++) if (sides.get(i).stream().anyMatch(x -> x.equalsIgnoreCase(who.trim())) || label(sides.get(i)).equalsIgnoreCase(who.trim())) side = i;
		if (side < 0) return "Name a fighter: " + label(sides.get(0)) + " or " + label(sides.get(1));
		votes.put(me.toLowerCase(), side);
		return null;
	}

	/** The series is decided: pay out the right calls. */
	public static synchronized void resolve(MinecraftServer server, List<String> winners) {
		if (sides.isEmpty()) return;
		int win = -1;
		for (int i = 0; i < sides.size(); i++) if (sides.get(i).stream().anyMatch(x -> winners.stream().anyMatch(w -> w.equalsIgnoreCase(x)))) win = i;
		open = false;
		if (win < 0 || votes.isEmpty()) { sides = List.of(); votes.clear(); return; }
		for (Map.Entry<String, Integer> v : votes.entrySet()) {
			Record r = LEDGER.computeIfAbsent(v.getKey(), k -> new Record());
			ServerPlayer p = server.getPlayerList().getPlayerByName(v.getKey());
			r.name = v.getKey().equals(FORECAST.toLowerCase()) ? FORECAST : p != null ? p.getGameProfile().name() : v.getKey();
			r.total++;
			boolean right = v.getValue() == win;
			if (right) { r.points += REWARD; r.correct++; }
			if (p != null) p.sendSystemMessage(Component.literal(right ? "§a🔮 You called it: " + label(sides.get(win)) + " won. +" + REWARD + " points (" + r.points + ")"
				: "§7🔮 " + label(sides.get(win)) + " won. No points this time."));
		}
		votes.clear();
		sides = List.of();
		save();
	}

	public static synchronized List<String> top(int n) {
		List<Record> rows = new ArrayList<>(LEDGER.values());
		rows.sort((a, b) -> Integer.compare(b.points, a.points));
		List<String> out = new ArrayList<>();
		for (int i = 0; i < Math.min(n, rows.size()); i++) out.add((i + 1) + ". " + rows.get(i).name + " - " + rows.get(i).points + " pts (" + rows.get(i).correct + "/" + rows.get(i).total + ")");
		return out;
	}

	private static synchronized Object snapshot() {
		Map<String, Object> out = new LinkedHashMap<>();
		Map<String, Object> cur = new LinkedHashMap<>();
		cur.put("open", open);
		cur.put("a", sides.isEmpty() ? null : label(sides.get(0)));
		cur.put("b", sides.isEmpty() ? null : label(sides.get(1)));
		cur.put("match", match);
		cur.put("votesA", votes.values().stream().filter(v -> v == 0).count());
		cur.put("votesB", votes.values().stream().filter(v -> v == 1).count());
		cur.put("probA", sides.isEmpty() ? null : Math.round(probA * 100));
		cur.put("probB", sides.isEmpty() ? null : Math.round((1 - probA) * 100));
		out.put("current", cur);
		List<Record> rows = new ArrayList<>(LEDGER.values());
		rows.sort((a, b) -> Integer.compare(b.points, a.points));
		out.put("leaders", rows);
		return out;
	}
}
