package com.relay.pvpchamps;

import java.util.ArrayList;
import java.util.Collections;
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
	private final String[] stepWinner = new String[30];
	private final String[] stepScore = new String[30];
	private final List<List<String>> drafts = new ArrayList<>();
	private List<String> draftLog = new ArrayList<>();

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

	/** Hands the whole bracket to the website: every series with its players, state, winner, score and kit draft. */
	private void publish() {
		List<java.util.Map<String, Object>> out = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++) {
			Step st = steps.get(i);
			String[] p;
			try { p = st.players().get(); } catch (RuntimeException e) { p = new String[2]; }
			java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
			m.put("n", i + 1);
			m.put("label", st.label());
			m.put("bestOf", st.bestOf());
			m.put("a", p.length > 0 ? p[0] : null);
			m.put("b", p.length > 1 ? p[1] : null);
			m.put("state", stepWinner[i] != null ? "done" : i == idx && !finished ? "live" : "pending");
			m.put("winner", stepWinner[i]);
			m.put("score", stepScore[i]);
			m.put("draft", i < drafts.size() ? drafts.get(i) : List.of());
			out.add(m);
		}
		java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
		root.put("event", EVENT);
		root.put("champion", champion);
		root.put("finished", finished);
		root.put("stopped", stopped);
		root.put("series", out);
		WebStats.setBracket(root);
	}

	private void next() {
		if (stopped) { publish(); return; }
		idx++;
		if (idx >= steps.size()) {
			finished = true;
			publish();
			broadcast("§6=== " + champion + " wins " + EVENT + " ===");
			return;
		}
		Step st = steps.get(idx);
		String[] p = st.players().get();
		String a = p[0], b = p[1];
		broadcast("§e" + st.label() + ": " + a + " vs " + b + " (best of " + st.bestOf() + ")");
		draftLog = new ArrayList<>();
		List<Kits> games = draft(a, b, st.bestOf());
		drafts.add(draftLog);
		publish();
		String label = EVENT + " · " + st.label();
		MatchManager.begin(new Match(server, viewer, true, a, b, games.get(0), st.bestOf(), label, EVENT, aWon -> {
			if (stopped) return;
			resolve(st, a, b, aWon ? a : b);
			next();
		}).setGameKits(games));
	}

	private void resolve(Step st, String a, String b, String winner) {
		st.record().accept(winner, winner.equals(a) ? b : a);
		int i = steps.indexOf(st);
		stepWinner[i] = winner;
		stepScore[i] = WebStats.lastScoreFor(winner);
	}

	/** The kits a series drafts from (Netherite OP is not one of the event's kits). */
	private static final List<Kits> POOL = List.of(Kits.SWORD, Kits.UHC, Kits.SPEAR_MACE, Kits.MACE, Kits.CART,
		Kits.DIAMOND_SMP, Kits.NETHERITE_SMP);

	/**
	 * The kit draft, with the higher seed (higher rating) going first.
	 * Best of 3: ban, ban, pick, pick, ban, ban, and the last kit left is the decider (games 1, 2, 3).
	 * Best of 5: ban, ban, and the five kits left are the five games, in a random order.
	 */
	private List<Kits> draft(String a, String b, int bestOf) {
		String hi = rating(a) >= rating(b) ? a : b, lo = hi.equals(a) ? b : a;
		List<Kits> left = new ArrayList<>(POOL);
		ban(hi, lo, left);
		ban(lo, hi, left);
		if (bestOf >= 5) {
			Collections.shuffle(left, rng);
			broadcast("§7Games: " + names(left));
			return left;
		}
		Kits p1 = pick(hi, left), p2 = pick(lo, left);
		ban(hi, lo, left);
		ban(lo, hi, left);
		Kits decider = left.get(0);
		broadcast("§7Decider: " + decider.label);
		return List.of(p1, p2, decider);
	}

	/** Bans the kit that helps the opponent most: one they play and the banner does not. */
	private void ban(String by, String opp, List<Kits> left) {
		Kits best = null;
		double bestScore = -1e9;
		for (Kits k : left) {
			double score = (kitsOf(opp).contains(k.id) ? 2 : 0) - (kitsOf(by).contains(k.id) ? 1 : 0) + rng.nextDouble() * 0.5;
			if (score > bestScore) { bestScore = score; best = k; }
		}
		left.remove(best);
		broadcast("§c" + by + " bans " + best.label);
	}

	/** Picks the banner's own best kit still available (earlier in their kit list means more preferred). */
	private Kits pick(String by, List<Kits> left) {
		Kits best = null;
		double bestScore = -1e9;
		List<String> own = kitsOf(by);
		for (Kits k : left) {
			int i = own.indexOf(k.id);
			double score = (i >= 0 ? 3 - i * 0.2 : 0) + rng.nextDouble() * 0.5;
			if (score > bestScore) { bestScore = score; best = k; }
		}
		left.remove(best);
		broadcast("§a" + by + " picks " + best.label);
		return best;
	}

	private static String names(List<Kits> kits) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < kits.size(); i++) sb.append(i == 0 ? "" : ", ").append(kits.get(i).label);
		return sb.toString();
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
		if (text.startsWith("§c") || text.startsWith("§a") || text.startsWith("§7Decider") || text.startsWith("§7Games")) draftLog.add(text.replaceAll("§.", ""));
		System.out.println("[pvpchamps] " + text.replaceAll("§.", ""));
		for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(Component.literal(text));
	}
}
