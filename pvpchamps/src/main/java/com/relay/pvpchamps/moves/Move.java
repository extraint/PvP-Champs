package com.relay.pvpchamps.moves;

/** One pro-style move. The engine calls {@link #start} to see if it wants to begin, then {@link #tick} until it returns false. */
public interface Move {
	String id();

	/** Cheap check, run every few ticks while nothing else is active. */
	boolean start(Ctx c);

	/** One tick of the move; false when it is finished. While true, VexBot's own AI is skipped for the tick. */
	boolean tick(Ctx c);

	/** Always called when the move ends or is interrupted: release anything held. */
	default void end(Ctx c) {}
}
