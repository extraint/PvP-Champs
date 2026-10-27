package com.relay.pvphub;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** The duel colosseums and the free-for-all one, laid out in a row from the configured origin and built in the background. */
public final class Arenas {
	private Arenas() {}

	private static final List<Arena> DUEL = new ArrayList<>();
	private static Arena ffa;
	private static boolean detected;

	public static void init() {
		Config c = Config.get();
		DUEL.clear();
		detected = false;
		for (int i = 0; i < c.duelSlots; i++) DUEL.add(new Arena("Arena " + (i + 1), new BlockPos(c.arenaX + i * c.slotSpacing, c.arenaY, c.arenaZ)));
		ffa = new Arena("FFA", new BlockPos(c.ffaX, c.ffaY, c.ffaZ));
	}

	/** A few columns of the next unfinished arena each tick: the first duel arena, then the FFA, then the rest. */
	public static void tick(MinecraftServer server) {
		if (ffa == null) return;
		var level = Hub.world(server);
		if (!detected) { for (Arena a : all()) a.detect(level); detected = true; }
		for (var p : server.getPlayerList().getPlayers()) {            // while a person is fighting or watching a fight, build nothing
			if (Crowd.isBot(p)) continue;
			var st = Hub.state(p);
			if (st == Hub.State.DUEL || st == Hub.State.FFA || st == Hub.State.SPECTATE) return;
		}
		for (Arena a : all()) {
			if (a.isBuilt()) continue;
			a.buildStep(level, Config.get().arenaBuildColumnsPerTick);
			return;
		}
	}

	private static List<Arena> all() {
		List<Arena> out = new ArrayList<>();
		if (!DUEL.isEmpty()) out.add(DUEL.get(0));
		out.add(ffa);
		for (int i = 1; i < DUEL.size(); i++) out.add(DUEL.get(i));
		return out;
	}

	/** How far the building has come, for the status lines ("" once everything stands). */
	public static String building() {
		int done = 0, total = 0, pct = 0;
		for (Arena a : all()) { total++; if (a.isBuilt()) done++; else if (pct == 0) pct = a.progress(); }
		return done == total ? "" : "Building arenas " + done + "/" + total + " (next " + pct + "%)";
	}

	public static synchronized Arena acquire() {
		for (Arena a : DUEL) if (!a.busy && a.isBuilt()) { a.busy = true; return a; }
		return null;
	}

	public static synchronized void release(Arena a) { a.busy = false; }

	public static Arena ffa() { return ffa; }

	public static List<Arena> duels() { return DUEL; }

	public static synchronized int free() {
		int n = 0;
		for (Arena a : DUEL) if (!a.busy && a.isBuilt()) n++;
		return n;
	}

	public static Arena at(net.minecraft.world.entity.Entity e) {
		for (Arena a : DUEL) if (a.contains(e)) return a;
		return ffa != null && ffa.contains(e) ? ffa : null;
	}
}
