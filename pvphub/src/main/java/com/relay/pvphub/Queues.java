package com.relay.pvphub;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The duel queues, one per kit and kind (ranked or not). Matching is by rating (ranked) and by connection: two players are
 * paired when their ratings and their pings are both within a window that widens the longer they have waited, so a fair
 * fight is found first and a fast one second.
 */
public final class Queues {
	private Queues() {}

	private record Entry(ServerPlayer p, Kit kit, boolean ranked, long since) {}

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static long ticks;

	private static int ping(ServerPlayer p) { return p.connection == null ? 0 : p.connection.latency(); }

	public static synchronized void join(ServerPlayer p, Kit kit, boolean ranked) {
		leave(p, false);
		Hub.toLobbyQuietly(p);
		ENTRIES.add(new Entry(p, kit, ranked, ticks));
		Hub.setState(p, Hub.State.QUEUE);
		HubItems.giveLobby(p, true);
		p.sendSystemMessage(Component.literal("§7Searching for a " + (ranked ? "§branked " : "§aunranked ") + "§f" + kit.label + " §7duel... (right-click the barrier to stop)"));
	}

	public static synchronized void reset() { ENTRIES.clear(); ticks = 0; }

	public static synchronized boolean isQueued(ServerPlayer p) {
		for (Entry e : ENTRIES) if (e.p.getUUID().equals(p.getUUID())) return true;
		return false;
	}

	public static synchronized void leave(ServerPlayer p, boolean announce) {
		boolean was = ENTRIES.removeIf(e -> e.p.getUUID().equals(p.getUUID()));
		if (was) {
			if (Hub.state(p) == Hub.State.QUEUE) Hub.setState(p, Hub.State.LOBBY);
			HubItems.giveLobby(p, false);
			if (announce) p.sendSystemMessage(Component.literal("§7You left the queue."));
		}
	}

	public static synchronized int size(Kit kit, boolean ranked) {
		int n = 0;
		for (Entry e : ENTRIES) if (e.kit == kit && e.ranked == ranked) n++;
		return n;
	}

	public static synchronized String status(ServerPlayer p) {
		for (Entry e : ENTRIES) {
			if (!e.p.getUUID().equals(p.getUUID())) continue;
			long s = (ticks - e.since) / 20;
			return "§eSearching §f" + e.kit.label + (e.ranked ? " §b(ranked)" : " §a(unranked)") + " §7· " + s / 60 + ":" + String.format("%02d", s % 60) + " · " + size(e.kit, e.ranked) + " in queue";
		}
		return "";
	}

	/** Once a tick; the matching itself runs once a second. */
	public static synchronized void tick() {
		ticks++;
		if (ticks % 20 != 0 || ENTRIES.isEmpty()) return;
		for (int i = 0; i < ENTRIES.size(); i++) {
			Entry a = ENTRIES.get(i);
			Entry best = null;
			double bestGap = Double.MAX_VALUE;
			for (int j = 0; j < ENTRIES.size(); j++) {
				if (j == i) continue;
				Entry b = ENTRIES.get(j);
				if (b.kit != a.kit || b.ranked != a.ranked) continue;
				double wait = Math.max(ticks - a.since, ticks - b.since) / 20.0;                 // seconds, by whoever has waited longest
				double eloGap = Math.abs(Stats.elo(a.p.getUUID(), a.p.getGameProfile().name(), a.kit) - Stats.elo(b.p.getUUID(), b.p.getGameProfile().name(), b.kit));
				double pingGap = Math.abs(ping(a.p) - ping(b.p));
				if (a.ranked && eloGap > 60 + 12 * wait) continue;          // ratings: 60 apart at first, 12 more every second
				if (pingGap > 50 + 8 * wait) continue;                      // pings: 50 ms apart at first, 8 more every second
				double score = eloGap + pingGap;
				if (score < bestGap) { bestGap = score; best = b; }
			}
			if (best == null && Crowd.active && ticks - a.since >= 160 && Arenas.free() > 0) {     // nobody to play: a bot steps in
				ServerPlayer bot = Population.idleBot(a.p.level().getServer());
				if (bot != null) {
					ENTRIES.remove(a);
					if (!Duels.start(a.p, bot, a.kit, a.ranked, 1)) ENTRIES.add(a);
					i = -1;
					continue;
				}
			}
			if (best == null || Arenas.free() == 0) continue;
			ENTRIES.remove(best);
			ENTRIES.remove(a);
			if (!Duels.start(a.p, best.p, a.kit, a.ranked, 1)) { ENTRIES.add(a); ENTRIES.add(best); }
			i = -1;   // the list changed: start again
		}
	}
}
