package com.relay.pvpchamps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * An 8-player single-elimination bracket, like the event's main bracket: you and seven roster bots. Each match is
 * best-of-3 in a random game mode. Your matches are played for real, the others are simulated from ratings.
 */
public final class Tournament {
	private static final String[] ROUNDS = { "Quarterfinal", "Semifinal", "Final" };
	private static final int PLAYER_RATING = 75;
	private static final String EVENT = "PVP CHAMPS II";

	private final MinecraftServer server;
	private final ServerPlayer player;
	private final Random rng = new Random();
	private List<String> alive = new ArrayList<>();
	private int round;
	private boolean over;

	public Tournament(MinecraftServer server, ServerPlayer player) {
		this.server = server;
		this.player = player;
		List<String> names = new ArrayList<>();
		for (Roster.Opponent o : Roster.all()) names.add(o.name());
		Collections.shuffle(names, rng);
		alive.add(player.getGameProfile().name());
		alive.addAll(names.subList(0, Math.min(7, names.size())));
		while (alive.size() < 8 && alive.size() % 2 == 0) break;
		Collections.shuffle(alive, rng);
		broadcast("§6=== PvP Champs: " + alive.size() + " players ===");
		describe();
		playNext();
	}

	public boolean isOver() { return over; }

	private String me() { return player.getGameProfile().name(); }

	private int rating(String name) {
		if (name.equalsIgnoreCase(me())) return PLAYER_RATING;
		Roster.Opponent o = Roster.byName(name);
		return o == null ? 70 : o.rating();
	}

	private void describe() {
		StringBuilder sb = new StringBuilder("§e" + roundName() + ": ");
		for (int i = 0; i + 1 < alive.size(); i += 2) sb.append(alive.get(i)).append(" vs ").append(alive.get(i + 1)).append("   ");
		broadcast(sb.toString());
	}

	private String roundName() { return round < ROUNDS.length ? "Main Event - " + ROUNDS[round + (3 - totalRounds())] : "Main Event"; }

	private int totalRounds() { int n = 0, p = alive.size(); while (p > 1) { p /= 2; n++; } return Math.max(n, 1); }

	/** Finds your match in the current round and starts it, or declares the winner. */
	private void playNext() {
		if (alive.size() == 1) { champion(alive.get(0)); return; }
		int idx = alive.indexOf(me());
		if (idx < 0) { over = true; return; }
		String opp = alive.get(idx % 2 == 0 ? idx + 1 : idx - 1);
		Roster.Opponent profile = Roster.byName(opp);
		List<String> kits = profile != null && !profile.kits().isEmpty() ? profile.kits() : Kits.ids();
		Kits kit = Kits.byId(kits.get(rng.nextInt(kits.size())));
		String label = alive.size() == 2 ? "Main Event - Final" : alive.size() == 4 ? "Main Event - Semifinal" : "Main Event - Quarterfinal";
		MatchManager.begin(new Match(server, player, false, me(), opp, kit, 3, label, EVENT, won -> afterMatch(opp, won)));
	}

	private void afterMatch(String opp, boolean youWon) {
		// Simulate every other match in this round.
		List<String> next = new ArrayList<>();
		for (int i = 0; i + 1 < alive.size(); i += 2) {
			String a = alive.get(i), b = alive.get(i + 1);
			if (a.equalsIgnoreCase(me()) || b.equalsIgnoreCase(me())) { next.add(youWon ? me() : opp); continue; }
			double pa = 1.0 / (1.0 + Math.pow(10, (rating(b) - rating(a)) / 40.0));
			String w = rng.nextDouble() < pa ? a : b;
			Kits k = Kits.values()[rng.nextInt(Kits.values().length)];
			int loserWins = rng.nextInt(2);
			broadcast("§7" + w + " def. " + (w.equals(a) ? b : a) + " 2-" + loserWins + " (" + k.label + ")");
			next.add(w);
		}
		alive = next;
		round++;
		if (!youWon) {
			broadcast("§cYou are out of the tournament.");
			over = true;
			return;
		}
		if (alive.size() == 1) { champion(me()); return; }
		describe();
		playNext();
	}

	private void champion(String name) {
		over = true;
		broadcast(name.equalsIgnoreCase(me()) ? "§6§l*** YOU WON PVP CHAMPS! ***" : "§6" + name + " wins PvP Champs.");
	}

	private void broadcast(String text) { player.sendSystemMessage(Component.literal(text)); }

	public String status() {
		return over ? "Tournament finished." : roundName() + " - still in: " + String.join(", ", alive);
	}
}
