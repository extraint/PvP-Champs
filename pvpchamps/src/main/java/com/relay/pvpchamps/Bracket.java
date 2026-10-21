package com.relay.pvpchamps;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A 16-player double-elimination bracket played entirely by bots while the player watches.
 *
 * <p>Upper bracket: 8, 4, 2, 1 matches. Lower bracket: 4, 4, 2, 2, 1, 1 matches. Then the Grand Final.
 * A loser in the upper bracket drops to a fixed lower-bracket round: Upper Round 1 losers fill Lower Round 1,
 * Upper Round 2 losers join Lower Round 2 (reversed order, so rematches are avoided), Upper Round 3 losers join
 * Lower Round 4, and the Upper Final loser waits for the Lower Final. Every series is best of 3, except the Upper
 * Final, Lower Final and Grand Final, which are best of 5.
 */
public final class Bracket {
	private static final String EVENT = "PVP CHAMPS II";

	/** The 16 players in bracket order: Upper Round 1 plays them in pairs, top to bottom. */
	public static final List<String> FIELD = List.of(
		"Swight", "Sweatgod", "Freekee_Fang", "ImMixed", "mikagoated", "Ymiau", "BLMcheat", "x_Kyran_x",
		"Coldified", "ZeNain", "Flowtives", "ItzTwilightTT", "ninorc15", "Alepordio", "C0RZZ", "Blvckwlf");

	private record Step(String label, int bestOf, Supplier<String[]> players, BiConsumer<String, String> record) {}

	private final MinecraftServer server;
	private final ServerPlayer viewer;
	private final Random rng = new Random();
	private final List<Step> steps = new ArrayList<>();
	private int idx = -1;
	private boolean stopped, finished;
	private String champion;

	// results by round
	private final String[] ur1W = new String[8], ur1L = new String[8], lr1W = new String[4];
	private final String[] ur2W = new String[4], ur2L = new String[4], lr2W = new String[4];
	private final String[] lr3W = new String[2], ur3W = new String[2], ur3L = new String[2], lr4W = new String[2];
	private String lr5W, ufW, ufL, lfW;

	public Bracket(MinecraftServer server, ServerPlayer viewer) {
		this.server = server;
		this.viewer = viewer;
		build();
	}

	public boolean isOver() { return stopped || finished; }

	public void stop() { stopped = true; }

	private void build() {
		for (int i = 0; i < 8; i++) { final int m = i;
			steps.add(new Step("Upper Round 1 (" + (m + 1) + "/8)", 3, () -> new String[] {FIELD.get(2 * m), FIELD.get(2 * m + 1)},
				(w, l) -> { ur1W[m] = w; ur1L[m] = l; })); }
		for (int i = 0; i < 4; i++) { final int m = i;
			steps.add(new Step("Lower Round 1 (" + (m + 1) + "/4)", 3, () -> new String[] {ur1L[2 * m], ur1L[2 * m + 1]},
				(w, l) -> lr1W[m] = w)); }
		for (int i = 0; i < 4; i++) { final int m = i;
			steps.add(new Step("Upper Round 2 (" + (m + 1) + "/4)", 3, () -> new String[] {ur1W[2 * m], ur1W[2 * m + 1]},
				(w, l) -> { ur2W[m] = w; ur2L[m] = l; })); }
		for (int i = 0; i < 4; i++) { final int m = i;
			steps.add(new Step("Lower Round 2 (" + (m + 1) + "/4)", 3, () -> new String[] {lr1W[m], ur2L[3 - m]},
				(w, l) -> lr2W[m] = w)); }
		for (int i = 0; i < 2; i++) { final int m = i;
			steps.add(new Step("Lower Round 3 (" + (m + 1) + "/2)", 3, () -> new String[] {lr2W[2 * m], lr2W[2 * m + 1]},
				(w, l) -> lr3W[m] = w)); }
		for (int i = 0; i < 2; i++) { final int m = i;
			steps.add(new Step("Upper Round 3 (" + (m + 1) + "/2)", 3, () -> new String[] {ur2W[2 * m], ur2W[2 * m + 1]},
				(w, l) -> { ur3W[m] = w; ur3L[m] = l; })); }
		for (int i = 0; i < 2; i++) { final int m = i;
			steps.add(new Step("Lower Round 4 (" + (m + 1) + "/2)", 3, () -> new String[] {lr3W[m], ur3L[m]},
				(w, l) -> lr4W[m] = w)); }
		steps.add(new Step("Lower Round 5", 3, () -> new String[] {lr4W[0], lr4W[1]}, (w, l) -> lr5W = w));
		steps.add(new Step("Upper Final", 5, () -> new String[] {ur3W[0], ur3W[1]}, (w, l) -> { ufW = w; ufL = l; }));
		steps.add(new Step("Lower Final", 5, () -> new String[] {lr5W, ufL}, (w, l) -> lfW = w));
		steps.add(new Step("Grand Final", 5, () -> new String[] {ufW, lfW}, (w, l) -> champion = w));
	}

	/** Starts the bracket: announces the first round and plays the first match. */
	public void start() {
		broadcast("§6=== " + EVENT + ": 16-player double elimination (" + steps.size() + " series) ===");
		StringBuilder sb = new StringBuilder("§eUpper Round 1: ");
		for (int i = 0; i < 16; i += 2) sb.append(FIELD.get(i)).append(" vs ").append(FIELD.get(i + 1)).append("   ");
		broadcast(sb.toString());
		broadcast("§7Best of 3 until the Upper Final, Lower Final and Grand Final, which are best of 5. /pvpchamps tournament skip to skip a series, /pvpchamps stop to end.");
		next();
	}

	private void next() {
		if (stopped) return;
		idx++;
		if (idx >= steps.size()) {
			finished = true;
			broadcast("§6=== " + champion + " wins " + EVENT + " ===");
			return;
		}
		Step st = steps.get(idx);
		String[] p = st.players().get();
		String a = p[0], b = p[1];
		broadcast("§e" + st.label() + ": " + a + " vs " + b + " (best of " + st.bestOf() + ")");
		Kits kit = pickKit(a, b);
		String label = EVENT + " · " + st.label();
		MatchManager.begin(new Match(server, viewer, true, a, b, kit, st.bestOf(), label, EVENT, aWon -> {
			if (stopped) return;
			resolve(st, a, b, aWon ? a : b);
			next();
		}));
	}

	private void resolve(Step st, String a, String b, String winner) {
		st.record().accept(winner, winner.equals(a) ? b : a);
	}

	/** A random kit both players can play, falling back to any kit. */
	private Kits pickKit(String a, String b) {
		List<String> ka = kitsOf(a), kb = kitsOf(b), both = new ArrayList<>();
		for (String k : ka) if (kb.contains(k)) both.add(k);
		List<String> pool = both.isEmpty() ? Kits.ids() : both;
		return Kits.byId(pool.get(rng.nextInt(pool.size())));
	}

	private List<String> kitsOf(String name) {
		Roster.Opponent o = Roster.byName(name);
		return o == null || o.kits().isEmpty() ? Kits.ids() : o.kits();
	}

	private int rating(String name) {
		Roster.Opponent o = Roster.byName(name);
		return o == null ? 70 : o.rating();
	}

	/** Cancels the running series and decides it by rating, then moves on. */
	public void skip() {
		if (isOver() || idx < 0 || idx >= steps.size()) return;
		Step st = steps.get(idx);
		String[] p = st.players().get();
		String a = p[0], b = p[1];
		MatchManager.cancelCurrent();
		double pa = 1.0 / (1.0 + Math.pow(10, (rating(b) - rating(a)) / 40.0));
		String w = rng.nextDouble() < pa ? a : b;
		broadcast("§7Skipped " + st.label() + ": " + w + " advances.");
		resolve(st, a, b, w);
		next();
	}

	public String status() {
		if (finished) return "Bracket finished.";
		if (idx < 0 || idx >= steps.size()) return "Bracket not started.";
		Step st = steps.get(idx);
		String[] p = st.players().get();
		return "Series " + (idx + 1) + "/" + steps.size() + ": " + st.label() + " - " + p[0] + " vs " + p[1] + " (best of " + st.bestOf() + ")";
	}

	private void broadcast(String text) {
		System.out.println("[pvpchamps] " + text.replaceAll("§.", ""));
		for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(Component.literal(text));
	}
}
