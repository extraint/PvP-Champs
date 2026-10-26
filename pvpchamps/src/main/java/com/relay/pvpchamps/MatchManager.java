package com.relay.pvpchamps;

/** Holds the one match that may run at a time (and the bracket around it) and ticks it. */
public final class MatchManager {
	private MatchManager() {}

	private static Bout current;
	private static Bracket bracket;
	/** Where the running bracket started: every one of its matches is staged here, not wherever the viewer drifted to. */
	private static net.minecraft.world.phys.Vec3 origin;

	public static net.minecraft.world.phys.Vec3 origin() { return origin != null ? origin : Arena.spot(); }

	public static void setOrigin(net.minecraft.world.phys.Vec3 at) { origin = at; }

	public static boolean busy() { return current != null && !current.isDone(); }

	/** True if a match or a bracket is already running. */
	public static boolean blocksTournament() { return bracket != null || busy(); }

	public static void begin(Bout m) { current = m; }

	public static Bout current() { return current; }

	public static void setBracket(Bracket b) { bracket = b; }

	public static Bracket bracket() { return bracket; }

	/** Cancels the running match without ending the bracket around it. */
	public static void cancelCurrent() {
		if (current != null) current.cancel();
		current = null;
	}

	public static void stop(net.minecraft.server.MinecraftServer server) {
		Draft.cancel();
		Viewers.leaveAll(server);
		if (bracket != null) bracket.stop();
		bracket = null;
		if (current != null) current.cancel();
		current = null;
		origin = null;
	}

	public static void tick(net.minecraft.server.MinecraftServer server) {
		Draft.tickActive();
		Viewers.tick(server);
		if (current != null) {
			Bout m = current;
			m.tick();
			// A bracket starts its next series from inside the end callback, so only clear if nothing replaced it.
			if (m.isDone() && current == m) current = null;
		}
		if (bracket != null && bracket.isOver() && current == null && !Draft.pending()) bracket = null;
		if (bracket == null && current == null && origin != null) { origin = null; Viewers.leaveAll(server); }
	}

	/** The opponent of a bot in the running match, or null. */
	public static net.minecraft.server.level.ServerPlayer opponentOf(String bot) {
		return current == null ? null : current.opponentOf(bot);
	}

	public static void onDeath(String victim) {
		if (current != null) current.fighterDown(victim);
	}
}
