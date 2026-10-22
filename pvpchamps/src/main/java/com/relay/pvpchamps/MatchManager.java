package com.relay.pvpchamps;

/** Holds the one match and tournament that may run at a time and ticks them. */
public final class MatchManager {
	private MatchManager() {}

	private static Match current;
	private static Tournament tournament;
	private static Bracket bracket;
	/** Where the running tournament or bracket started: every one of its matches is staged here, not wherever the viewer drifted to. */
	private static net.minecraft.world.phys.Vec3 origin;

	public static net.minecraft.world.phys.Vec3 origin() { return origin; }

	public static void setOrigin(net.minecraft.world.phys.Vec3 at) { origin = at; }

	public static boolean busy() { return current != null && !current.isDone(); }

	public static void begin(Match m) { current = m; }

	public static Match current() { return current; }

	public static void setTournament(Tournament t) { tournament = t; }

	public static void setBracket(Bracket b) { bracket = b; }

	public static Bracket bracket() { return bracket; }

	/** Cancels the running match without ending the bracket or tournament around it. */
	public static void cancelCurrent() {
		if (current != null) current.cancel();
		current = null;
	}

	public static Tournament tournament() { return tournament; }

	public static void stop() {
		if (bracket != null) bracket.stop();
		bracket = null;
		if (current != null) current.cancel();
		current = null;
		tournament = null;
		origin = null;
	}

	public static void tick() {
		if (current == null) return;
		Match m = current;
		m.tick();
		// A tournament starts the next match from inside the end callback, so only clear if nothing replaced it.
		if (m.isDone() && current == m) current = null;
		if (tournament != null && tournament.isOver() && current == null) tournament = null;
		if (bracket != null && bracket.isOver() && current == null) bracket = null;
		if (tournament == null && bracket == null) origin = null;
	}

	/** The opponent of a bot in the running match, or null. */
	public static net.minecraft.server.level.ServerPlayer opponentOf(String bot) {
		return current == null ? null : current.opponentOf(bot);
	}

	public static void onDeath(String victim) {
		if (current != null) current.fighterDown(victim);
	}
}
