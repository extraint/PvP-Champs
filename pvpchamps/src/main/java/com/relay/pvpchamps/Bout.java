package com.relay.pvpchamps;

/** One running fight (a 1v1 {@link Match} or a 2v2 {@link TeamMatch}) as the match manager and the move engine see it. */
public interface Bout {
	/** The kit being played in the current game. */
	Kits kit();

	void tick();

	boolean isDone();

	void cancel();

	/** Who the named bot is fighting right now, or null if it is not in this fight or the fight has not started. */
	net.minecraft.server.level.ServerPlayer opponentOf(String name);

	/** A fighter died (from the death event or from polling). */
	void fighterDown(String name);

	/** Everyone fighting in it (the names a spectator can follow). */
	java.util.List<String> fighterNames();

	/** Where the series is in the event and the tournament, e.g. "Upper Round 1 (4/8) · Round 2 · 1-0". */
	String describe();

	/** The two sides of the series (names), for predictions. */
	java.util.List<java.util.List<String>> sides();

	/** The level the fight is in. */
	net.minecraft.server.level.ServerLevel level();
}
