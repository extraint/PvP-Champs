package com.relay.pvphub;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** The pool of duel arenas and the one free-for-all arena, laid out in a row from the configured origin. */
public final class Arenas {
	private Arenas() {}

	private static final List<Arena> DUEL = new ArrayList<>();
	private static Arena ffa;

	public static void init() {
		Config c = Config.get();
		DUEL.clear();
		for (int i = 0; i < c.duelSlots; i++) DUEL.add(new Arena("Arena " + (i + 1), new BlockPos(c.arenaX + i * c.slotSpacing, c.arenaY, c.arenaZ), 24));
		ffa = new Arena("FFA", new BlockPos(c.ffaX, c.ffaY, c.ffaZ), 34);
	}

	/** A free duel arena, marked busy, or null when they are all in use. */
	public static synchronized Arena acquire() {
		for (Arena a : DUEL) if (!a.busy) { a.busy = true; return a; }
		return null;
	}

	public static synchronized void release(Arena a) { a.busy = false; }

	public static Arena ffa() { return ffa; }

	public static List<Arena> duels() { return DUEL; }

	public static synchronized int free() {
		int n = 0;
		for (Arena a : DUEL) if (!a.busy) n++;
		return n;
	}

	/** The arena (duel or FFA) that holds this position, or null. */
	public static Arena at(net.minecraft.world.entity.Entity e) {
		for (Arena a : DUEL) if (a.contains(e)) return a;
		return ffa != null && ffa.contains(e) ? ffa : null;
	}
}
