package com.relay.pvpchamps;

/** Holds the one match and tournament that may run at a time and ticks them. */
public final class MatchManager {
	private MatchManager() {}

	private static Match current;
	private static Tournament tournament;

	public static boolean busy() { return current != null && !current.isDone(); }

	public static void begin(Match m) { current = m; }

	public static Match current() { return current; }

	public static void setTournament(Tournament t) { tournament = t; }

	public static Tournament tournament() { return tournament; }

	public static void stop() {
		if (current != null) current.cancel();
		current = null;
		tournament = null;
	}

	public static void tick() {
		if (current == null) return;
		Match m = current;
		m.tick();
		// A tournament starts the next match from inside the end callback, so only clear if nothing replaced it.
		if (m.isDone() && current == m) current = null;
		if (tournament != null && tournament.isOver() && current == null) tournament = null;
	}

	/** The opponent of a bot in the running match, or null. */
	public static net.minecraft.server.level.ServerPlayer opponentOf(String bot) {
		return current == null ? null : current.opponentOf(bot);
	}

	public static void onDeath(String victim) {
		if (current != null) current.fighterDown(victim);
	}
}
