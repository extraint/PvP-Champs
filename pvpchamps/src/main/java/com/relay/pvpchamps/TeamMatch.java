package com.relay.pvpchamps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import vexbot.api.VexBotApi;
import vexbot.kit.KitManager;

/**
 * One best-of-N match between two teams (a team can be a single fighter). Any fighter can be a real player or a bot.
 * {@code participants} are the real players of the tournament: those named in a team fight, the others spectate. A round
 * is a countdown, the spawn, a fight until one whole team is dead, and a short pause. A dead bot is removed straight
 * away so Vexbot cannot respawn it in place; a dead player spectates the rest of the round.
 */
public final class TeamMatch implements Bout {
	private enum State { COUNTDOWN, SPAWNING, FIGHT, BETWEEN, DONE }

	private final MinecraftServer server;
	private final List<java.util.UUID> partIds = new ArrayList<>();      // every real player of the tournament
	private final Set<String> humanNames = new HashSet<>();               // lower-case names of the real players fighting in this match
	private final boolean watch;                                          // no real player fights: they all just spectate
	private final List<String> teamA, teamB;
	private Kits kit;
	private List<Kits> gameKits;
	private final int bestOf;
	private final String roundPrefix, event;
	private final Consumer<Boolean> onEnd;     // true if team A won
	private final ServerLevel level;
	private final Vec3 centre;
	private final Map<java.util.UUID, GameType> savedMode = new HashMap<>();
	private final Map<java.util.UUID, net.minecraft.world.item.ItemStack[]> savedInv = new HashMap<>();
	private net.minecraft.world.item.ItemStack[] kitStacks;
	private ArenaGuard arena;

	private State state = State.COUNTDOWN;
	private int timer, round = 1, scoreA, scoreB, fightTicks;
	private final Set<String> dead = new HashSet<>();           // lower-case names of fighters dead this round
	private final Map<String, Integer> missing = new HashMap<>(), hole = new HashMap<>();
	private int lastDeathA, lastDeathB;
	private Ping.Session ping;
	private long gameTicks;
	private boolean spawned;
	private final Set<String> prepared = new HashSet<>();
	private float stallHp = -1;
	private int stillFor;
	private final WebStats.Live liveInfo = new WebStats.Live();

	public TeamMatch(MinecraftServer server, List<ServerPlayer> participants, List<String> teamA, List<String> teamB, Kits kit,
			int bestOf, String roundPrefix, String event, Consumer<Boolean> onEnd) {
		this.server = server;
		this.teamA = List.copyOf(teamA);
		this.teamB = List.copyOf(teamB);
		ServerPlayer lead = participants.get(0);
		for (ServerPlayer p : participants) {
			partIds.add(p.getUUID());
			savedMode.put(p.getUUID(), p.gameMode());
			String n = p.getGameProfile().name();
			if (inMatch(n)) {
				humanNames.add(n.toLowerCase());
				net.minecraft.world.item.ItemStack[] inv = new net.minecraft.world.item.ItemStack[41];
				for (int i = 0; i < 41; i++) inv[i] = p.getInventory().getItem(i).copy();
				savedInv.put(p.getUUID(), inv);
			}
		}
		this.watch = humanNames.isEmpty();
		this.kit = kit;
		this.bestOf = bestOf;
		this.roundPrefix = roundPrefix;
		this.event = event;
		this.onEnd = onEnd;
		this.level = (ServerLevel) lead.level();
		this.centre = MatchManager.origin() != null ? MatchManager.origin() : lead.position();
		vexbot.BotSettings.botDontLeave = true;
		vexbot.BotSettings.botLeaveOnDeath = false;
		vexbot.BotSettings.shulkerRegear = false;
		vexbot.BotSettings.enderChestRegear = false;
		vexbot.BotSettings.pearlChasing = false;
		command("gamerule immediate_respawn true");
		this.kitStacks = kit.build(server);
		for (String n : fighters()) {
			Roster.Opponent o = Roster.byName(n);
			if (o != null && !isHuman(n)) Roster.applyProfile(o);
		}
		makeTeams();
		aimTeams();
		List<ServerPlayer> fighting = new ArrayList<>();
		for (ServerPlayer p : participants) if (isHuman(p.getGameProfile().name())) fighting.add(p);
		ping = Ping.begin(server, fighters(), fighting, text -> message(Component.literal(text)));
		message(Component.literal("=== " + roundPrefix + ": " + label(teamA) + " vs " + label(teamB) + " - " + kit.label + " - first to "
			+ (bestOf / 2 + 1) + " ==="));
		beginRound();
	}

	private static final String TEAM_A = "pvpchamps_a", TEAM_B = "pvpchamps_b";

	/** Puts each side into a Vexbot team so partners never target or retaliate against each other. */
	private void makeTeams() {
		for (String[] t : new String[][] { { TEAM_A }, { TEAM_B } }) {
			boolean a = t[0].equals(TEAM_A);
			List<String> side = a ? teamA : teamB;
			vexbot.team.TeamManager.delete(t[0]);
			vexbot.team.TeamManager.getOrCreate(t[0]);
			List<String> bots = new ArrayList<>(), players = new ArrayList<>();
			for (String n : side) (isHuman(n) ? players : bots).add(n);
			vexbot.team.TeamManager.setTeamBots(t[0], bots);
			vexbot.team.TeamManager.setTeamPlayers(t[0], players);
			var team = vexbot.team.TeamManager.get(t[0]);
			var tc = team.tactics;
			System.err.println("[pvpchamps] team " + t[0] + " defaults: mode=" + team.getMode() + " shield=" + tc.shieldBehaviour + " priority=" + tc.targetPriority
				+ " formation=" + tc.formation + " maintain=" + tc.maintainFormation + " follow=" + tc.followBoss + " active=" + tc.active());
		}
	}

	/**
	 * A Vexbot team's own settings replace the global ones for its members (and start at different defaults), so copy
	 * every global setting into the two teams; run after the bots' profiles are applied.
	 */
	private void syncTeams() {
		for (String t : new String[] { TEAM_A, TEAM_B }) {
			var team = vexbot.team.TeamManager.get(t);
			if (team == null) continue;
			for (java.lang.reflect.Field f : team.getClass().getFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || java.lang.reflect.Modifier.isFinal(f.getModifiers())) continue;
				try {
					java.lang.reflect.Field g = vexbot.BotSettings.class.getField(f.getName());
					if (java.lang.reflect.Modifier.isStatic(g.getModifiers()) && g.getType() == f.getType()) f.set(team, g.get(null));
				} catch (ReflectiveOperationException ignored) { /* no global of that name: leave the team's own value */ }
			}
		}
		// Revenge makes a bot hit back at whoever it last saw attack it: one stray arrow at a partner and the pair fight for the whole round.
		for (String t : new String[] { TEAM_A, TEAM_B }) vexbot.team.TeamManager.setTeamRevengeMode(t, false);
		vexbot.EffectiveSettings.invalidate();
	}

	/** Each team targets only the other team, and its shield and target behaviour is left to the bots. */
	private void aimTeams() {
		vexbot.team.TeamManager.setMode(TEAM_A, vexbot.team.TeamManager.TargetMode.SPECIFIC_TEAMS);
		vexbot.team.TeamManager.setTargetTeams(TEAM_A, List.of(TEAM_B));
		vexbot.team.TeamManager.setMode(TEAM_B, vexbot.team.TeamManager.TargetMode.SPECIFIC_TEAMS);
		vexbot.team.TeamManager.setTargetTeams(TEAM_B, List.of(TEAM_A));
		for (String t : new String[] { TEAM_A, TEAM_B }) {
			var tc = vexbot.team.TeamManager.get(t).tactics;
			tc.shieldBehaviour = vexbot.team.TeamTactics.ShieldBehaviour.FREE;
			tc.targetPriority = vexbot.team.TeamTactics.TargetPriority.INDEPENDENT;
			tc.maintainFormation = false;
			tc.followBoss = false;
			tc.protectBoss = false;
		}
	}

	private static void dropTeams() {
		vexbot.team.TeamManager.delete(TEAM_A);
		vexbot.team.TeamManager.delete(TEAM_B);
	}

	public TeamMatch setGameKits(List<Kits> kits) { this.gameKits = kits; return this; }

	// ----------------------------------------------------------------------- helpers

	private static String label(List<String> team) { return String.join(" + ", team); }

	private List<String> fighters() {
		List<String> all = new ArrayList<>(teamA);
		all.addAll(teamB);
		return all;
	}

	private boolean isHuman(String name) { return humanNames.contains(name.toLowerCase()); }

	/** The name as the teams spell it. */
	private String teamName(String name) {
		for (String n : fighters()) if (n.equalsIgnoreCase(name)) return n;
		return name;
	}

	private boolean inA(String name) { return teamA.stream().anyMatch(n -> n.equalsIgnoreCase(name)); }

	private boolean inMatch(String name) { return inA(name) || teamB.stream().anyMatch(n -> n.equalsIgnoreCase(name)); }

	/** Every tournament player who is still online. */
	private List<ServerPlayer> players() {
		List<ServerPlayer> out = new ArrayList<>();
		for (java.util.UUID id : partIds) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) out.add(p); }
		return out;
	}

	private boolean spectates(ServerPlayer p) { return !isHuman(p.getGameProfile().name()); }

	private ServerPlayer entity(String name) {
		if (!isHuman(name)) return VexBotApi.resolveBot(server, name);
		for (ServerPlayer p : players()) if (p.getGameProfile().name().equalsIgnoreCase(name)) return p;
		return null;
	}

	private boolean alive(String name) { return !dead.contains(name.toLowerCase()); }

	private boolean teamOut(List<String> team) { return team.stream().noneMatch(this::alive); }

	private String roundLabel() { return roundPrefix + " · Round " + round + " · " + scoreA + "-" + scoreB; }

	private int sc(int ticks) {
		float rate = server.tickRateManager().tickrate();
		return Math.round(ticks * Math.max(1f, rate / 20f));
	}

	private Vec3 spot(double dx, double dz) {
		double x = centre.x + dx, z = centre.z + dz;
		if (centre.equals(Arena.spot())) return new Vec3(x, centre.y, z);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		return new Vec3(x, Math.max(y, centre.y - 2), z);
	}

	/** Where a fighter starts: team A on the west side, team B on the east, partners a few blocks apart. */
	private Vec3 spawnOf(String name) {
		boolean a = inA(name);
		List<String> team = a ? teamA : teamB;
		int i = Math.max(0, team.indexOf(name));
		double dz = team.size() == 1 ? 0 : (i == 0 ? -3.5 : 3.5);
		return spot(a ? -10 : 10, dz);
	}

	private float yawOf(String name) { return inA(name) ? -90f : 90f; }

	private void teleport(ServerPlayer p, Vec3 pos, float yaw) {
		p.teleportTo(level, pos.x, pos.y, pos.z, Set.<Relative>of(), yaw, 0f, true);
	}

	private String dimension() { return level.dimension().identifier().toString(); }

	private void command(String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
	}

	@Override public List<String> fighterNames() { return fighters(); }

	@Override public ServerLevel level() { return level; }

	private void message(Component c) { for (ServerPlayer p : players()) p.sendSystemMessage(c); Viewers.message(server, c, false); }

	private void message(Component c, boolean overlay) { for (ServerPlayer p : players()) p.sendSystemMessage(c, overlay); Viewers.message(server, c, overlay); }

	private void revive() {
		for (ServerPlayer p : players()) if (!p.isAlive()) server.getPlayerList().respawn(p, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
	}

	private void prepare(ServerPlayer p, boolean isHumanFighter) {
		KitManager.apply(p, isHumanFighter ? Layouts.apply(p, kit, kitStacks) : kitStacks);
		p.setGameMode(GameType.SURVIVAL);
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.removeAllEffects();
		p.clearFire();
	}

	// ----------------------------------------------------------------------- Bout

	@Override public Kits kit() { return kit; }

	@Override public boolean isDone() { return state == State.DONE; }

	@Override public void cancel() { finish(false, true); }

	/** The nearest living enemy of the named fighter, or null. */
	private ServerPlayer nearestEnemy(String name) {
		ServerPlayer self = entity(name);
		if (self == null) return null;
		ServerPlayer best = null;
		double bd = Double.MAX_VALUE;
		for (String e : inA(name) ? teamB : teamA) {
			if (!alive(e)) continue;
			ServerPlayer p = entity(e);
			if (p == null || !p.isAlive()) continue;
			double d = p.distanceToSqr(self);
			if (d < bd) { bd = d; best = p; }
		}
		return best;
	}

	@Override
	public ServerPlayer opponentOf(String name) {
		if (state != State.FIGHT || !inMatch(name) || isHuman(name)) return null;
		return nearestEnemy(name);
	}

	@Override
	public void fighterDown(String name) {
		if (state != State.FIGHT || !inMatch(name) || !alive(name)) return;
		ServerPlayer who = entity(name);
		if (who != null && who.isAlive() && who.getHealth() > 0) return; // a death event for someone still standing is not a death
		markDead(name);
	}

	private void markDead(String name) {
		if (!dead.add(name.toLowerCase())) return;
		if (inA(name)) lastDeathA = fightTicks; else lastDeathB = fightTicks;
		message(Component.literal("§7" + name + " is down."));
	}

	/** Points every living bot at the nearest living enemy. */
	private void retarget() {
		for (String n : fighters()) {
			if (isHuman(n) || !alive(n)) continue;
			ServerPlayer e = nearestEnemy(n);
			if (e != null) VexBotApi.hunt(n, e.getGameProfile().name());
		}
	}

	// ----------------------------------------------------------------------- round flow

	private void beginRound() {
		if (arena == null) { arena = Arena.guard(level); if (arena == null) arena = new ArenaGuard(level, centre); } else arena.restore();
		if (gameKits != null && round - 1 < gameKits.size() && gameKits.get(round - 1) != kit) {
			kit = gameKits.get(round - 1);
			kitStacks = kit.build(server);
			message(Component.literal("§eGame " + round + " kit: " + kit.label));
		}
		for (String n : fighters()) if (!isHuman(n)) Roster.applyTier(n, kit);
		ping.applyBots();
		syncTeams();
		state = State.COUNTDOWN;
		spawned = false;
		prepared.clear();
		timer = sc(60);
		dead.clear();
		missing.clear();
		hole.clear();
		fightTicks = 0;
		stallHp = -1;
		stillFor = 0;
		for (String n : fighters()) if (!isHuman(n)) VexBotApi.despawnBot(server, n);
		revive();
		System.err.println("[pvpchamps] round " + round + " begins: " + label(teamA) + " vs " + label(teamB) + " in " + kit.label);
		for (ServerPlayer p : players()) {
			String n = teamName(p.getGameProfile().name());
			if (spectates(p)) {
				p.setGameMode(GameType.SPECTATOR);
				teleport(p, centre.add(0, 7, 0), 0f);
			} else {
				prepare(p, true);
				teleport(p, spawnOf(n), yawOf(n));
			}
			Spec.stop(p);
		}
	}

	@Override
	public void tick() {
		if (state == State.DONE) return;
		ping.sync(state == State.FIGHT);
		gameTicks++;
		boolean fighterGone = false;           // a fighter leaving ends the match; a spectator leaving does not
		for (String n : humanNames) if (entity(n) == null) fighterGone = true;
		if (fighterGone || players().isEmpty()) {
			System.err.println("[pvpchamps] team match cancelled: a player left the server");
			finish(false, true);
			return;
		}
		switch (state) {
			case COUNTDOWN -> {
				if (timer == sc(60) || timer == sc(40) || timer == sc(20)) message(Component.literal("§e" + Math.round(timer / (float) sc(20))), true);
				// The bots appear at the start of the countdown and get their kit the tick they exist, so the fight starts fully equipped.
				if (!spawned) {
					for (String n : fighters()) if (!isHuman(n)) VexBotApi.spawnBot(server, n, spawnOf(n), yawOf(n), dimension());
					spawned = true;
				}
				prepareNew();
				if (--timer <= 0) {
					state = State.SPAWNING;
					timer = 0;
				}
			}
			case SPAWNING -> {
				timer++;
				boolean ready = true;
				for (String n : fighters()) if (entity(n) == null) ready = false;
				if (ready) {
					prepareNew();
					retarget();
					message(Component.literal("§cFIGHT!"), true);
					String a1 = teamA.get(0), b1 = teamB.get(0);
					String extra = "2v2: " + label(teamA) + " vs " + label(teamB);
					for (ServerPlayer p : players()) if (spectates(p)) Spec.watch(p, a1, b1, kit.label, roundLabel(), event, extra); // no top bar while you play
					state = State.FIGHT;
				} else if (timer > sc(300)) {
					message(Component.literal("§cA fighter did not spawn (is the area loaded and clear?). Match cancelled."));
					finish(false, true);
				}
			}
			case FIGHT -> fightTick();
			case BETWEEN -> {
				if (--timer <= 0) {
					if (scoreA > bestOf / 2 || scoreB > bestOf / 2) finish(scoreA > scoreB, false);
					else { round++; beginRound(); }
				}
			}
			case DONE -> {}
		}
	}

	/** Gives the kit to every bot that has appeared and has not had it yet. */
	private void prepareNew() {
		for (String n : fighters()) {
			if (isHuman(n) || prepared.contains(n)) continue;
			ServerPlayer p = entity(n);
			if (p == null) continue;
			prepare(p, false);
			prepared.add(n);
		}
	}

	/** True if this damage is one fighter hurting a teammate; it is cancelled. */
	public boolean friendly(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource src) {
		if (!(victim instanceof ServerPlayer v) || !(src.getEntity() instanceof ServerPlayer a) || a == v) return false;
		String vn = v.getGameProfile().name(), an = a.getGameProfile().name();
		boolean same = inMatch(vn) && inMatch(an) && inA(vn) == inA(an);
		if (same) System.err.println("[pvpchamps] blocked friendly fire: " + an + " -> " + vn + " (" + src.type().msgId() + ")");
		return same;
	}

	private void fightTick() {
		fightTicks++;
		for (String n : fighters()) {
			if (!alive(n)) continue;
			ServerPlayer e = entity(n);
			if (e == null) { if (missing.merge(n, 1, Integer::sum) > sc(100)) markDead(n); continue; }
			missing.remove(n);
			if (e.getHealth() <= 0 || !e.isAlive()) markDead(n);
		}
		// Vexbot respawns a dead bot in place; take it out on the tick after, never inside the death (an explosion is still iterating players).
		for (String n : fighters()) if (!isHuman(n) && !alive(n) && VexBotApi.resolveBot(server, n) != null) VexBotApi.despawnBot(server, n);
		for (String n : humanNames) if (!alive(n)) spectateAsDead(n);
		if (fightTicks == sc(60) || fightTicks == sc(140) || fightTicks % sc(200) == 0) retarget();
		if (fightTicks % Math.max(2, sc(4)) == 1) publishLive();
		if (fightTicks > sc(100)) stallWatch();
		if (kit == Kits.CRYSTAL && fightTicks % 4 == 0) {
			for (String n : fighters()) {
				ServerPlayer p = alive(n) ? entity(n) : null;
				if (p != null && p.isAlive()) Restock.placed(level, p, isHuman(n) ? Layouts.apply(p, kit, kitStacks) : kitStacks);
			}
		}
		trackHoles();
		boolean aOut = teamOut(teamA), bOut = teamOut(teamB);
		if (!aOut && !bOut) return;
		boolean aWon = bOut && (!aOut || lastDeathB > lastDeathA); // both wiped at once: the team that fell last wins
		if (aWon) scoreA++; else scoreB++;
		List<String> win = aWon ? teamA : teamB, lose = aWon ? teamB : teamA;
		for (int i = 0; i < Math.max(win.size(), lose.size()); i++) WebStats.round(win.get(i % win.size()), lose.get(i % lose.size()), kit.label);
		for (ServerPlayer p : players()) if (spectates(p)) Spec.roundEnd(p, lose.get(0));
		message(Component.literal("§6" + label(win) + " win round " + round + " (" + scoreA + "-" + scoreB + ")"));
		System.err.println("[pvpchamps] DUR round " + round + " " + kit.id + " " + label(teamA) + " vs " + label(teamB) + ": " + fightTicks / 20 + " s of fight");
		state = State.BETWEEN;
		timer = sc(140);
	}

	/** A dead human spectates the rest of the round from above the arena. */
	private void spectateAsDead(String name) {
		revive();
		ServerPlayer p = entity(name);
		if (p != null && p.isAlive() && p.gameMode() != GameType.SPECTATOR) {
			p.setGameMode(GameType.SPECTATOR);
			teleport(p, centre.add(0, 7, 0), 0f);
		}
	}

	private void stallWatch() {
		float hp = 0;
		for (String n : fighters()) { ServerPlayer p = alive(n) ? entity(n) : null; if (p != null) hp += p.getHealth(); }
		if (stallHp < 0 || hp < stallHp) stillFor = 0; else stillFor++;
		stallHp = hp;
		if (stillFor == sc(900)) {
			System.err.println("[pvpchamps] " + label(teamA) + " vs " + label(teamB) + " no damage for 45s; re-issuing the hunt");
			retarget();
		} else if (stillFor == sc(1200)) {
			System.err.println("[pvpchamps] still no damage; bringing the teams together");
			ServerPlayer anchor = null;
			for (String n : teamA) if (alive(n) && entity(n) != null) { anchor = entity(n); break; }
			if (anchor != null) for (String n : teamB) {
				ServerPlayer p = alive(n) ? entity(n) : null;
				if (p != null) teleport(p, anchor.position().add(2.5, 0, 0), 90f);
			}
			retarget();
			stillFor = 0;
		}
	}

	/** Anyone stuck in a hole for 15 seconds: reset the arena and put everyone still alive back on the floor. */
	private void trackHoles() {
		boolean reset = false;
		for (String n : fighters()) {
			ServerPlayer p = alive(n) ? entity(n) : null;
			if (p != null && p.getY() < centre.y - 4.5) { if (hole.merge(n, 1, Integer::sum) >= sc(300)) reset = true; }
			else hole.remove(n);
		}
		if (!reset) return;
		if (arena != null) arena.restore();
		for (String n : fighters()) {
			ServerPlayer p = alive(n) ? entity(n) : null;
			if (p != null) teleport(p, spawnOf(n), yawOf(n));
		}
		hole.clear();
		message(Component.literal("§eStuck in a hole too long: the arena was reset and everyone is back on the floor."));
	}

	private void publishLive() {
		liveInfo.id = label(teamA) + "|" + label(teamB);
		liveInfo.a = label(teamA); liveInfo.b = label(teamB);
		liveInfo.kit = kit.label; liveInfo.round = roundLabel(); liveInfo.state = state.name().toLowerCase();
		liveInfo.scoreA = scoreA; liveInfo.scoreB = scoreB; liveInfo.bestOf = bestOf;
		liveInfo.server = ping.serverLabel(); liveInfo.netA = ping.net(teamA); liveInfo.netB = ping.net(teamB);
		ServerPlayer a = firstAlive(teamA), b = firstAlive(teamB);
		liveInfo.hpA = a == null ? 0 : a.getHealth() + a.getAbsorptionAmount();
		liveInfo.hpB = b == null ? 0 : b.getHealth() + b.getAbsorptionAmount();
		liveInfo.gearA = WebStats.gearOf(a);
		liveInfo.gearB = WebStats.gearOf(b);
		if (liveInfo.startedAt == 0) liveInfo.startedAt = System.currentTimeMillis();
		WebStats.live(liveInfo);
	}

	private ServerPlayer firstAlive(List<String> team) {
		for (String n : team) if (alive(n)) { ServerPlayer p = entity(n); if (p != null) return p; }
		return null;
	}

	private void finish(boolean aWon, boolean cancelled) {
		if (state == State.DONE) return;
		state = State.DONE;
		System.err.println("[pvpchamps] DUR match " + kit.id + " " + label(teamA) + " vs " + label(teamB) + ": " + gameTicks / 20 + " s total, " + round + " rounds");
		ping.end();
		dropTeams();
		WebStats.endLive(label(teamA) + "|" + label(teamB));
		if (!cancelled) {
			List<String> win = aWon ? teamA : teamB, lose = aWon ? teamB : teamA;
			for (int i = 0; i < Math.max(win.size(), lose.size()); i++)
				WebStats.match(win.get(i % win.size()), lose.get(i % lose.size()), kit.label, roundPrefix, Math.max(scoreA, scoreB), Math.min(scoreA, scoreB));
		}
		for (String n : fighters()) if (!isHuman(n)) VexBotApi.despawnBot(server, n);
		if (arena != null) arena.restore();
		revive();
		for (ServerPlayer p : players()) {
			Spec.stop(p);
			p.setGameMode(savedMode.getOrDefault(p.getUUID(), GameType.SURVIVAL));
			net.minecraft.world.item.ItemStack[] inv = savedInv.get(p.getUUID());
			if (inv != null) {
				p.getInventory().clearContent();
				KitManager.apply(p, inv);
			}
		}
		command("gamerule immediate_respawn false");
		if (!cancelled) message(Component.literal("§a=== " + label(aWon ? teamA : teamB) + " win the match " + Math.max(scoreA, scoreB) + "-" + Math.min(scoreA, scoreB) + " ==="));
		else message(Component.literal("§7Match ended."));
		if (!cancelled && onEnd != null) onEnd.accept(aWon);
	}
}
