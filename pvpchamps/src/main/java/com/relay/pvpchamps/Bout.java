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
}
