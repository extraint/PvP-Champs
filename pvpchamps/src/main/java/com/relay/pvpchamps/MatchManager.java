package com.relay.pvpchamps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Holds the one match that may run at a time and ticks it. Tournaments do not start matches themselves: they queue
 * them here, so you can be in the 1v1 and the 2v2 tournament at once and the arena plays whichever match is next.
 */
public final class MatchManager {
	private MatchManager() {}

	private record Entry(Tournament owner, Supplier<Bout> make) {}

	private static Bout current;
	private static Tournament currentOwner;
	private static final ArrayDeque<Entry> queue = new ArrayDeque<>();
	private static final List<Tournament> tournaments = new ArrayList<>();
	private static Bracket bracket;
	/** Where the running tournament or bracket started: every one of its matches is staged here, not wherever the viewer drifted to. */
	private static net.minecraft.world.phys.Vec3 origin;

	public static net.minecraft.world.phys.Vec3 origin() { return origin != null ? origin : Arena.spot(); }

	public static void setOrigin(net.minecraft.world.phys.Vec3 at) { origin = at; }

	public static boolean busy() { return current != null && !current.isDone(); }

	/** True if a single match or a bracket is running that is not part of a tournament. */
	public static boolean blocksTournament() {
		return bracket != null || (busy() && currentOwner == null);
	}

	public static void begin(Bout m) { current = m; currentOwner = null; }

	public static Bout current() { return current; }

	public static Tournament currentOwner() { return currentOwner; }

	public static void enqueue(Tournament owner, Supplier<Bout> make) { queue.add(new Entry(owner, make)); }

	public static void dequeue(Tournament owner) { queue.removeIf(e -> e.owner() == owner); }

	public static void addTournament(Tournament t) { tournaments.add(t); }

	public static List<Tournament> tournaments() { return tournaments; }

	public static void setBracket(Bracket b) { bracket = b; }

	public static Bracket bracket() { return bracket; }

	/** Cancels the running match without ending the bracket or tournament around it. */
	public static void cancelCurrent() {
		if (current != null) current.cancel();
		current = null;
		currentOwner = null;
	}

	public static void stop() {
		if (bracket != null) bracket.stop();
		bracket = null;
		queue.clear();
		if (current != null) current.cancel();
		current = null;
		currentOwner = null;
		tournaments.clear();
		origin = null;
	}

	private static void startQueued() {
		Entry e = queue.poll();
		if (e == null) return;
		Bout b = e.make().get();
		if (b == null) return;
		current = b;
		currentOwner = e.owner();
	}

	public static void tick() {
		if (current == null) startQueued();
		if (current != null) {
			Bout m = current;
			m.tick();
			// A tournament queues its next match from inside the end callback, so only clear if nothing replaced it.
			if (m.isDone() && current == m) { current = null; currentOwner = null; }
		}
		if (current == null && queue.isEmpty()) {
			// A tournament that is neither playing nor waiting lost its match to a cancel: drop it.
			for (Tournament t : tournaments) if (!t.isOver()) t.abort();
			tournaments.removeIf(Tournament::isOver);
		}
		if (bracket != null && bracket.isOver() && current == null) bracket = null;
		if (tournaments.isEmpty() && bracket == null && current == null) origin = null;
	}

	/** The opponent of a bot in the running match, or null. */
	public static net.minecraft.server.level.ServerPlayer opponentOf(String bot) {
		return current == null ? null : current.opponentOf(bot);
	}

	public static void onDeath(String victim) {
		if (current != null) current.fighterDown(victim);
	}
}
