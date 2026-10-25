package com.relay.pvpchamps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A single-elimination bracket you play in. SOLO is you and seven roster players (1v1); DUO is every roster player
 * in teams of two plus you and a partner (2v2), with byes when the team count is not a power of two. Your matches are
 * played for real. Everyone else's matches are played live too, so once yours ends you spectate them until your
 * next one; /pvpchamps tournament skip simulates them instead. Matches go through the {@link MatchManager} queue, so
 * the 1v1 and the 2v2 tournament can run at the same time and take turns on the arena.
 */
public final class Tournament {
	public enum Mode { SOLO, DUO }

	private record Pair(List<String> a, List<String> b) {}

	private static final int PLAYER_RATING = 75;
	private static final String EVENT = "PVP CHAMPS II";

	private final MinecraftServer server;
	private final UUID playerId;
	private final String me;
	private final Mode mode;
	private final Random rng = new Random();
	private List<List<String>> field = new ArrayList<>();      // teams still in (a solo player is a team of one)
	private List<List<String>> winners = new ArrayList<>();
	private final List<Pair> pending = new ArrayList<>();
	private String roundName = "";
	private int bracketSize;
	private boolean over, humanOut, skipRound, skipAll, humanMatch;
	private Pair activePair;       // the pairing whose match is queued or on screen

	public Tournament(MinecraftServer server, ServerPlayer player, Mode mode, String partner) {
		this.server = server;
		this.playerId = player.getUUID();
		this.me = player.getGameProfile().name();
		this.mode = mode;
		List<String> names = new ArrayList<>();
		for (Roster.Opponent o : Roster.all()) if (!o.name().equalsIgnoreCase(me) && !o.name().equalsIgnoreCase(partner)) names.add(o.name());
		Collections.shuffle(names, rng);
		if (mode == Mode.SOLO) {
			field.add(List.of(me));
			for (String n : names.subList(0, Math.min(7, names.size()))) field.add(List.of(n));
		} else {
			String mate = partner != null ? Roster.byName(partner).name() : names.remove(names.size() - 1);
			field.add(List.of(me, mate));
			for (int i = 0; i + 1 < names.size(); i += 2) field.add(List.of(names.get(i), names.get(i + 1)));
		}
		Collections.shuffle(field, rng);
		broadcast("§6=== PvP Champs" + (mode == Mode.DUO ? " 2v2" : "") + ": " + field.size() + (mode == Mode.DUO ? " teams" : " players") + " ===");
		if (mode == Mode.DUO) broadcast("§eYour team: " + label(teamOfMe()));
		startRound();
	}

	public boolean isOver() { return over; }

	public Mode mode() { return mode; }

	private ServerPlayer player() { return server.getPlayerList().getPlayer(playerId); }

	private static String label(List<String> team) { return String.join(" + ", team); }

	private boolean hasMe(List<String> team) { return team.stream().anyMatch(n -> n.equalsIgnoreCase(me)); }

	private List<String> teamOfMe() { for (List<String> t : field) if (hasMe(t)) return t; return List.of(me); }

	private int rating(List<String> team) {
		int sum = 0;
		for (String n : team) {
			Roster.Opponent o = Roster.byName(n);
			sum += n.equalsIgnoreCase(me) ? PLAYER_RATING : o == null ? 70 : o.rating();
		}
		return sum / team.size();
	}

	private String prefix() { return (mode == Mode.DUO ? "2v2 " : "") + "Main Event - " + roundName; }

	private void startRound() {
		skipRound = false;
		if (field.size() == 1) { champion(field.get(0)); return; }
		int n = field.size();
		bracketSize = Integer.highestOneBit(n) < n ? Integer.highestOneBit(n) << 1 : n;
		roundName = bracketSize == 2 ? "Final" : bracketSize == 4 ? "Semifinal" : bracketSize == 8 ? "Quarterfinal" : "Round of " + bracketSize;
		List<List<String>> rest = new ArrayList<>(field);
		winners = new ArrayList<>();
		for (int byes = bracketSize - n; byes > 0; byes--) {            // the byes go to teams that are not yours
			for (int i = 0; i < rest.size(); i++) if (!hasMe(rest.get(i))) { winners.add(rest.remove(i)); break; }
		}
		pending.clear();
		for (int i = 0; i + 1 < rest.size(); i += 2) {
			Pair p = new Pair(rest.get(i), rest.get(i + 1));
			if (hasMe(p.a()) || hasMe(p.b())) pending.add(0, p); else pending.add(p);   // your match first, then you spectate the rest
		}
		StringBuilder sb = new StringBuilder("§e" + prefix() + ": ");
		for (Pair p : pending) sb.append(label(p.a())).append(" vs ").append(label(p.b())).append("   ");
		if (!winners.isEmpty()) {
			sb.append("(byes: ");
			for (List<String> t : winners) sb.append(label(t)).append(' ');
			sb.append(")");
		}
		broadcast(sb.toString());
		advance();
	}

	/** Plays or simulates the next pairing of the round, or moves to the next round. */
	private void advance() {
		if (over) return;
		while (!pending.isEmpty()) {
			Pair p = pending.remove(0);
			boolean mine = hasMe(p.a()) || hasMe(p.b());
			if (mine || !(skipRound || skipAll || player() == null)) { play(p, mine); return; }
			simulate(p);
		}
		field = winners;
		startRound();
	}

	private void play(Pair p, boolean mine) {
		List<String> a = mine && hasMe(p.b()) ? p.b() : p.a();
		List<String> b = a == p.a() ? p.b() : p.a();
		humanMatch = mine;
		activePair = p;
		if (mine && mode == Mode.SOLO) {       // your own series starts with the kit draft in a menu
			ServerPlayer pl = player();
			if (pl != null) {
				Draft.start(server, pl, a.get(0), a.get(0), b.get(0), 3, this::kitsOf, n -> rating(List.of(n)), this::broadcast,
					games -> { if (!over) queue(a, b, games.get(0), games, mine); });
				return;
			}
		}
		queue(a, b, pickKit(b, mine), null, mine);
	}

	private List<String> kitsOf(String name) {
		Roster.Opponent o = Roster.byName(name);
		return o == null || o.kits().isEmpty() ? Kits.ids() : o.kits();
	}

	private void queue(List<String> a, List<String> b, Kits kit, List<Kits> games, boolean mine) {
		String label = prefix();
		Consumer<Boolean> done = aWon -> finished(a, b, aWon, mine);
		MatchManager.enqueue(this, () -> {
			ServerPlayer pl = player();
			if (pl == null) { over = true; return null; }
			if (mine) pl.sendSystemMessage(Component.literal("§eYour match is up: " + label(a) + " vs " + label(b) + " (" + kit.label + ")"));
			if (mode == Mode.SOLO) {
				Match m = new Match(server, pl, !mine, a.get(0), b.get(0), kit, 3, label, EVENT, done);
				return games == null ? m : m.setGameKits(games);
			}
			return new TeamMatch(server, pl, !mine, a, b, kit, 3, label, EVENT, done);
		});
	}

	private Kits pickKit(List<String> opponents, boolean mine) {
		List<String> kits = null;
		if (mine && mode == Mode.SOLO) {
			Roster.Opponent o = Roster.byName(opponents.get(0));
			if (o != null && !o.kits().isEmpty()) kits = o.kits();
		}
		if (kits == null) kits = Kits.ids();
		return Kits.byId(kits.get(rng.nextInt(kits.size())));
	}

	private void finished(List<String> a, List<String> b, boolean aWon, boolean mine) {
		activePair = null;
		winners.add(aWon ? a : b);
		if (mine) {
			humanMatch = false;
			if (!aWon) {
				humanOut = true;
				broadcast("§cYou are out of the " + (mode == Mode.DUO ? "2v2 " : "") + "tournament. You can keep watching, or /pvpchamps tournament skip.");
			}
		}
		advance();
	}

	private void simulate(Pair p) {
		double pa = 1.0 / (1.0 + Math.pow(10, (rating(p.b()) - rating(p.a())) / 40.0));
		boolean aWon = rng.nextDouble() < pa;
		List<String> w = aWon ? p.a() : p.b(), l = aWon ? p.b() : p.a();
		Kits k = Kits.values()[rng.nextInt(Kits.values().length)];
		broadcast("§7" + label(w) + " def. " + label(l) + " 2-" + rng.nextInt(2) + " (" + k.label + ")");
		winners.add(w);
	}

	/** Simulates the live matches you are watching; your own matches are still played. */
	public void skip() {
		if (over) return;
		skipRound = true;
		if (humanOut) skipAll = true;
		if (humanMatch || activePair == null) return;
		// Drop the watched match (on screen or still queued) and settle that pairing by rating.
		if (MatchManager.currentOwner() == this) MatchManager.cancelCurrent(); else MatchManager.dequeue(this);
		Pair dropped = activePair;
		activePair = null;
		simulate(dropped);
		advance();
	}

	private void champion(List<String> team) {
		over = true;
		broadcast(hasMe(team) ? "§6§l*** YOU WON PVP CHAMPS" + (mode == Mode.DUO ? " 2v2" : "") + "! ***" : "§6" + label(team) + " win PvP Champs" + (mode == Mode.DUO ? " 2v2" : "") + ".");
	}

	/** Called when its match was cancelled underneath it. */
	public void abort() {
		if (over) return;
		over = true;
		broadcast("§7The " + (mode == Mode.DUO ? "2v2 " : "") + "tournament was ended.");
	}

	private void broadcast(String text) {
		ServerPlayer p = player();
		if (p != null) p.sendSystemMessage(Component.literal(text));
	}

	public String status() {
		if (over) return (mode == Mode.DUO ? "2v2 t" : "T") + "ournament finished.";
		StringBuilder sb = new StringBuilder((mode == Mode.DUO ? "2v2 " : "") + roundName + " - still in: ");
		for (List<String> t : field) sb.append(label(t)).append("; ");
		return sb.toString();
	}
}
