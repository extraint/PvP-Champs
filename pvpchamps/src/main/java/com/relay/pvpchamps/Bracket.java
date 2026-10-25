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

	/** An empty slot: whoever it is paired with advances without playing. */
	public static final String BYE = "BYE";

	private record Step(String label, int bestOf, Supplier<String[]> players, BiConsumer<String, String> record) {}

	private final MinecraftServer server;
	private final ServerPlayer viewer;
	private final List<String> field;
	private final boolean duo;      // 2v2: entrants are "A + B" teams
	private final boolean humanPlays;   // you are in the field and play your own series; otherwise you only watch
	private final String me;
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
		this(server, viewer, FIELD, false, false);
	}

	public Bracket(MinecraftServer server, ServerPlayer viewer, List<String> field, boolean duo, boolean humanPlays) {
		this.server = server;
		this.viewer = viewer;
		this.field = field;
		this.duo = duo;
		this.humanPlays = humanPlays;
		this.me = viewer.getGameProfile().name();
		build();
	}

	/**
	 * The 2v2 field: you and a random partner as one team, every other roster player paired at random, 16 slots with
	 * byes for the teams that do not fit (never yours).
	 */
	public static List<String> duoField(String me, Random rng) {
		List<String> names = new ArrayList<>();
		for (Roster.Opponent o : Roster.all()) if (!o.name().equalsIgnoreCase(me)) names.add(o.name());
		Collections.shuffle(names, rng);
		List<String> teams = new ArrayList<>();
		teams.add(me + " + " + names.remove(names.size() - 1));
		for (int i = 0; i + 1 < names.size(); i += 2) teams.add(names.get(i) + " + " + names.get(i + 1));
		Collections.shuffle(teams.subList(1, teams.size()), rng);
		int byes = Math.min(8, 16 - Math.min(16, teams.size()));
		List<Integer> pairs = new ArrayList<>();
		for (int i = 0; i < 8; i++) pairs.add(i);
		Collections.shuffle(pairs, rng);
		List<Integer> byePairs = pairs.subList(0, byes), playPairs = pairs.subList(byes, 8);
		String[] slots = new String[16];
		java.util.Arrays.fill(slots, BYE);
		int t = 0;
		for (int p : playPairs) for (int k = 0; k < 2; k++) if (t < teams.size()) slots[2 * p + k] = teams.get(t++);
		for (int p : byePairs) if (t < teams.size()) slots[2 * p] = teams.get(t++);
		return List.of(slots);
	}

	/** The 1v1 field: the event's 16 players with you taking a random slot. */
	public static List<String> soloField(String me, Random rng) {
		List<String> f = new ArrayList<>(FIELD);
		f.remove(me);
		if (f.size() == 16) f.remove(rng.nextInt(16));
		f.add(rng.nextInt(f.size() + 1), me);
		return f;
	}

	private static List<String> members(String entrant) { return List.of(entrant.split(" \\+ ")); }

	private boolean hasMe(String entrant) { return members(entrant).stream().anyMatch(n -> n.equalsIgnoreCase(me)); }

	public boolean isOver() { return stopped || finished; }

	public void stop() { stopped = true; }

	private void build() {
		for (int i = 0; i < 8; i++) { final int m = i;
			steps.add(new Step("Upper Round 1 (" + (m + 1) + "/8)", 3, () -> new String[] {field.get(2 * m), field.get(2 * m + 1)},
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
		broadcast("§6=== " + EVENT + (duo ? " 2v2" : "") + ": 16-" + (duo ? "slot" : "player") + " double elimination (" + steps.size() + " series) ===");
		if (humanPlays) for (String e : field) if (hasMe(e)) broadcast(duo ? "§eYour team: " + e : "§eYou are in the field.");
		StringBuilder sb = new StringBuilder("§eUpper Round 1: ");
		for (int i = 0; i < 16; i += 2) sb.append(field.get(i)).append(" vs ").append(field.get(i + 1)).append("   ");
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
		if (a.equals(BYE) || b.equals(BYE)) {      // a bye: the other side advances without playing
			drafts.add(List.of());
			resolve(st, a, b, a.equals(BYE) ? b : a);
			stepScore[idx] = "bye";
			next();
			return;
		}
		broadcast("§e" + st.label() + ": " + a + " vs " + b + " (best of " + st.bestOf() + ")");
		draftLog = new ArrayList<>();
		publish();
		boolean mine = humanPlays && (hasMe(a) || hasMe(b));
		Draft.start(server, mine ? viewer : null, mine ? (hasMe(a) ? a : b) : null, a, b, st.bestOf(), this::kitsOf, this::rating, this::broadcast,
			games -> { if (!stopped) play(st, a, b, games, mine); });
	}

	private void play(Step st, String a, String b, List<Kits> games, boolean mine) {
		drafts.add(draftLog);
		publish();
		String label = EVENT + " · " + st.label();
		boolean swap = mine && hasMe(b);       // you are always side A of your own match
		String ta = swap ? b : a, tb = swap ? a : b;
		if (mine) broadcast("§eYour match is up!");
		java.util.function.Consumer<Boolean> done = aWon -> {
			if (stopped) return;
			String w = aWon == !swap ? a : b;
			resolve(st, a, b, w);
			if (mine && !hasMe(w))
				broadcast(st.label().startsWith("Upper") ? "§eYou dropped to the lower bracket." : "§cYou are out of the tournament. Keep watching, or /pvpchamps tournament skip.");
			next();
		};
		if (duo) MatchManager.begin(new TeamMatch(server, viewer, !mine, members(ta), members(tb), games.get(0), st.bestOf(), label, EVENT, done).setGameKits(games));
		else MatchManager.begin(new Match(server, viewer, !mine, ta, tb, games.get(0), st.bestOf(), label, EVENT, done).setGameKits(games));
	}

	private void resolve(Step st, String a, String b, String winner) {
		st.record().accept(winner, winner.equals(a) ? b : a);
		int i = steps.indexOf(st);
		stepWinner[i] = winner;
		stepScore[i] = WebStats.lastScoreFor(members(winner).get(members(winner).size() - 1));
	}

	private List<String> kitsOf(String entrant) {
		List<String> out = new ArrayList<>();
		for (String n : members(entrant)) {
			Roster.Opponent o = Roster.byName(n);
			for (String k : o == null || o.kits().isEmpty() ? Kits.ids() : o.kits()) if (!out.contains(k)) out.add(k);
		}
		return out;
	}

	private int rating(String entrant) {
		int sum = 0;
		for (String n : members(entrant)) {
			Roster.Opponent o = Roster.byName(n);
			sum += o == null ? (n.equalsIgnoreCase(me) ? 75 : 70) : o.rating();
		}
		return sum / members(entrant).size();
	}

	/** Cancels the running series and decides it by rating, then moves on. */
	public void skip() {
		if (isOver() || idx < 0 || idx >= steps.size()) return;
		Step st = steps.get(idx);
		String[] p = st.players().get();
		String a = p[0], b = p[1];
		if (humanPlays && (hasMe(a) || hasMe(b))) return;   // your own series is always played
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
