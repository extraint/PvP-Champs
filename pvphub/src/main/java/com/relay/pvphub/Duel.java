package com.relay.pvphub;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** One duel between two players: rounds of countdown, fight and a short pause, until someone has won enough of them. */
public final class Duel {
	public enum Phase { COUNTDOWN, FIGHT, ENDING }

	public final Arena arena;
	public final Kit kit;
	public final boolean ranked;
	public final int bestOf;
	public final ServerPlayer[] p = new ServerPlayer[2];
	public final int[] score = new int[2];
	public final Set<UUID> watchers = new HashSet<>();
	public Phase phase = Phase.COUNTDOWN;
	/** A fight between crowd bots: no ratings, no stats. */
	public boolean simulated;
	public int fightTicks;
	private int timer, round;
	private boolean finished;

	Duel(Arena arena, ServerPlayer a, ServerPlayer b, Kit kit, boolean ranked, int bestOf) {
		this.arena = arena;
		this.p[0] = a;
		this.p[1] = b;
		this.kit = kit;
		this.ranked = ranked;
		this.bestOf = bestOf;
	}

	private ServerLevel level() { return Hub.world(p[0].level().getServer()); }

	int indexOf(ServerPlayer who) { return p[0].getUUID().equals(who.getUUID()) ? 0 : p[1].getUUID().equals(who.getUUID()) ? 1 : -1; }

	public boolean isFinished() { return finished; }

	void begin() {
		for (ServerPlayer x : p) {
			Hub.setState(x, Hub.State.DUEL);
			x.sendSystemMessage(Component.literal("§7" + (ranked ? "§bRanked" : "§aUnranked") + " §7" + kit.label + ": §f" + p[0].getGameProfile().name() + " §7vs §f" + p[1].getGameProfile().name()
				+ (bestOf > 1 ? " §7(best of " + bestOf + ")" : "") + (ranked ? " §7· ratings " + Math.round(Stats.elo(p[0].getUUID(), p[0].getGameProfile().name(), kit)) + " / " + Math.round(Stats.elo(p[1].getUUID(), p[1].getGameProfile().name(), kit)) : "")));
		}
		startRound();
	}

	private void startRound() {
		round++;
		arena.reset(level());
		for (int i = 0; i < 2; i++) {
			ServerPlayer x = p[i];
			Vec3 s = arena.spawn(i);
			x.setCamera(x);
			x.setGameMode(GameType.SURVIVAL);
			Hub.teleport(x, level(), s.x, s.y, s.z, arena.spawnYaw(i));
			if (Crowd.isBot(x)) Population.give(x, kit); else kit.give(x);
		}
		phase = Phase.COUNTDOWN;
		timer = Config.get().countdownSeconds * 20;
	}

	void tick() {
		if (finished) return;
		for (ServerPlayer x : p) if (x.isRemoved() || x.connection == null) { forfeit(x); return; }          // gone (left, or a bot that was removed)
		switch (phase) {
			case COUNTDOWN -> {
				for (int i = 0; i < 2; i++) {                  // nobody moves until the fight starts
					Vec3 s = arena.spawn(i);
					if (Math.abs(p[i].getX() - s.x) > 0.3 || Math.abs(p[i].getZ() - s.z) > 0.3) Hub.teleport(p[i], level(), s.x, s.y, s.z, arena.spawnYaw(i));
				}
				if (timer % 20 == 0 && timer > 0) for (ServerPlayer x : p) x.sendSystemMessage(Component.literal("§e" + timer / 20 + "..."), true);
				if (--timer <= 0) {
					phase = Phase.FIGHT;
					fightTicks = 0;
					if (Crowd.isBot(p[0]) || Crowd.isBot(p[1])) Population.fight(this);
					for (ServerPlayer x : p) x.sendSystemMessage(Component.literal("§c§lFIGHT!"), true);
				}
			}
			case FIGHT -> {
				fightTicks++;
				for (int i = 0; i < 2; i++) {                  // out of the arena counts as losing the round
					if (p[i].getY() < arena.centre.getY() - 4) { died(p[i]); return; }
				}
			}
			case ENDING -> {
				if (--timer <= 0) {
					int need = bestOf / 2 + 1;
					if (score[0] >= need || score[1] >= need) finish(score[0] >= need ? 0 : 1);
					else startRound();
				}
			}
		}
	}

	/** A fighter was killed (their death was cancelled): the other takes the round. */
	void died(ServerPlayer loser) {
		if (phase != Phase.FIGHT || finished) return;
		int li = indexOf(loser);
		if (li < 0) return;
		int wi = 1 - li;
		score[wi]++;
		phase = Phase.ENDING;
		timer = 50;
		loser.setHealth(loser.getMaxHealth());
		for (ServerPlayer x : p) x.sendSystemMessage(Component.literal(x == loser ? "§c§lDEFEAT" : "§a§lVICTORY"), true);
		String line = "§6" + p[wi].getGameProfile().name() + " §7wins the round (" + score[0] + "-" + score[1] + ")";
		for (ServerPlayer x : p) x.sendSystemMessage(Component.literal(line));
		broadcastWatchers(line);
	}

	/** A fighter left: the other wins the duel on the spot. */
	void forfeit(ServerPlayer leaver) {
		if (finished) return;
		int li = indexOf(leaver);
		if (li < 0) return;
		p[1 - li].sendSystemMessage(Component.literal("§e" + leaver.getGameProfile().name() + " left the duel."));
		finish(1 - li);
	}

	private void finish(int winner) {
		if (finished) return;
		finished = true;
		ServerPlayer w = p[winner], l = p[1 - winner];
		String wn = w.getGameProfile().name(), ln = l.getGameProfile().name();
		boolean botW = Crowd.isBot(w), botL = Crowd.isBot(l);
		if (simulated) {
			// bots: nothing to rate
		} else if (botW || botL) {                                   // a person against a crowd bot
			ServerPlayer human = botW ? l : w;
			boolean won = human == w;
			String hn = human.getGameProfile().name();
			if (ranked) {
				double d = Stats.rankedVsBot(human.getUUID(), hn, kit, won);
				double e = Stats.elo(human.getUUID(), hn, kit);
				human.sendSystemMessage(Component.literal((won ? "§a=== You won! §7Rating " : "§c=== You lost. §7Rating ") + Math.round(e) + (won ? " §a(+" : " §c(") + Math.round(d) + ") §7· " + Stats.rankName(e) + " ==="));
			} else {
				Stats.casualVsBot(human.getUUID(), hn, kit, won);
				human.sendSystemMessage(Component.literal(won ? "§a=== You won! ===" : "§c=== You lost. ==="));
			}
		} else if (ranked) {
			double[] d = Stats.ranked(w.getUUID(), wn, l.getUUID(), ln, kit);
			double we = Stats.elo(w.getUUID(), wn, kit), le = Stats.elo(l.getUUID(), ln, kit);
			w.sendSystemMessage(Component.literal("§a=== You won! §7Rating " + Math.round(we) + " §a(+" + Math.round(d[0]) + ") §7· " + Stats.rankName(we) + " ==="));
			l.sendSystemMessage(Component.literal("§c=== You lost. §7Rating " + Math.round(le) + " §c(" + Math.round(d[1]) + ") §7· " + Stats.rankName(le) + " ==="));
		} else {
			Stats.casual(w.getUUID(), wn, l.getUUID(), ln, kit);
			w.sendSystemMessage(Component.literal("§a=== You won! ==="));
			l.sendSystemMessage(Component.literal("§c=== You lost. ==="));
		}
		broadcastWatchers("§6" + wn + " §7beat §6" + ln + " §7(" + score[0] + "-" + score[1] + ")");
		for (UUID id : new HashSet<>(watchers)) {
			ServerPlayer s = p[0].level().getServer().getPlayerList().getPlayer(id);
			if (s != null) Hub.toLobby(s);
		}
		Arenas.release(arena);
		Duels.remove(this);
		Hub.toLobby(w);
		Hub.toLobby(l);
		if (simulated) Population.recycle(w, l);
		else { if (botW) Population.recycle(w, w); if (botL) Population.recycle(l, l); }
	}

	private void broadcastWatchers(String text) {
		for (UUID id : watchers) {
			ServerPlayer s = p[0].level().getServer().getPlayerList().getPlayer(id);
			if (s != null) s.sendSystemMessage(Component.literal(text));
		}
	}
}
