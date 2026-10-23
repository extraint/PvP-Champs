package com.relay.pvpchamps.moves;

import com.relay.pvpchamps.CombatMaster;
import com.relay.pvpchamps.Roster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import vexbot.bot.ai.SwordPvpController;
import vexbot.fakeplayer.bot.BotPlayerActionPack;
import vexbot.fakeplayer.bot.connection.ServerPlayerInterface;

/**
 * Runs the extra moves on top of VexBot's own AI. The mixin calls {@link #tick} at the start of each bot's
 * tick; while a move is active it returns true and VexBot's tick is skipped, otherwise VexBot carries on.
 * Only bots in the roster get moves, and only the moves listed for them.
 */
public final class MoveEngine {
	private MoveEngine() {}

	public static final class State {
		final Random rng = new Random();
		final Map<String, Integer> cooldowns = new HashMap<>();
		Move current, next;
		int tick;
		int curTicks; // how long the current move has held the bot
		int prevSlot = -1;
		net.minecraft.world.phys.Vec3 lastPos;
		int stillTicks;
		float lastTargetAbsorb;
		final PopPunish punish = new PopPunish();
		final List<Move> moves;

		State() {
			moves = List.of(new HealAtFourHearts(), new PotUp(), new MaceSmash(), new ReachCombo(), new SpearJab(), punish, new MasterCircle(), new MendReset(), new PearlClose(), new BaitRetreat(punish), new LungeSwap(), new WallOff(), new CartBomb(), new TntMinecart(), new PearlWind(), new StrafePressure(), new AntiStall(), new ShieldBreak(), new ThreeBlockHit());
		}

		int cooldown(String id) { return Math.max(0, cooldowns.getOrDefault(id, 0) - tick); }

		void setCooldown(String id, int ticks) { cooldowns.put(id, tick + ticks); }

		/** Hand control to another move as soon as the current one has ended (the bait counter-attack). */
		void forceMove(Move m) { next = m; }
	}

	private static final Map<String, State> STATES = new HashMap<>();
	private static final Map<String, Integer> USED = new HashMap<>();
	private static boolean enabled = true;
	/** Diagnostics for /pvpchamps moves stats: how often the hook ran, and why it did or didn't act. */
	public static final Map<String, Integer> DIAG = new HashMap<>();

	private static void diag(String k) { DIAG.merge(k, 1, Integer::sum); }

	/** Why a move's start check said no, counted per move so /pvpchamps moves stats shows the blocker. */
	public static boolean reject(String move, String why) { DIAG.merge("no:" + move + ":" + why, 1, Integer::sum); return false; }

	public static void setEnabled(boolean on) { enabled = on; }

	public static boolean enabled() { return enabled; }

	public static Map<String, Integer> usage() { return USED; }

	public static void forget(String bot) { STATES.remove(bot); }

	/** @return true to skip VexBot's own tick for this bot. */
	public static boolean tick(SwordPvpController controller) {
		diag("hook");
		if (!enabled) return false;
		ServerPlayer bot = controller.getBot();
		// The controller only knows its target part-way through its own tick, so ask the match who the opponent is.
		LivingEntity target = com.relay.pvpchamps.MatchManager.opponentOf(controller.getBot().getGameProfile().name());
		if (target == null) target = controller.getTarget();
		Roster.Opponent profile = Roster.byName(bot.getGameProfile().name());
		if (profile == null) { diag("not_in_roster"); return false; }
		diag("roster_bot");
		State st = STATES.computeIfAbsent(bot.getGameProfile().name(), k -> new State());
		st.tick++;
		net.minecraft.world.phys.Vec3 here = bot.position();
		st.stillTicks = (st.lastPos != null && here.distanceToSqr(st.lastPos) < 0.0004) ? st.stillTicks + 1 : 0;
		st.lastPos = here;

		BotPlayerActionPack pack = ((ServerPlayerInterface) bot).getActionPack();
		if (target == null || !target.isAlive() || pack == null) {
			diag(target == null ? "no_target" : pack == null ? "no_pack" : "target_dead");
			if (st.current != null) endCurrent(st, new Ctx(bot, bot, pack, st));
			return false;
		}
		Ctx c = new Ctx(bot, target, pack, st);
		diag("with_target");
		// Mid-dive with a mace: the slam is Vexbot's (or mace_smash's) to finish; nothing else may grab the bot's hands.
		if (c.carriesMace() && st.moves.stream().noneMatch(x -> x.id().equals("mace_smash")) && !bot.onGround() && bot.getDeltaMovement().y < -0.2 && (st.current == null || !st.current.id().equals("heal_4_hearts"))
				&& (st.current == null || !st.current.id().equals("mace_smash"))) {
			boolean heal = bot.getHealth() <= 8f;
			if (!heal) { if (st.current != null) endCurrent(st, c); return false; }
		}

		boolean skip = false;
		// Healing is checked every tick and beats whatever else is running: waiting for the 4-tick move check
		// lets Vexbot's own healing act first.
		// A raised shield is answered at once on Combat Master bots: checked every tick, ahead of everything but healing.
		if (CombatMaster.is(profile.difficulty()) && (st.current == null || !st.current.id().equals("shield_break"))
				&& (st.current == null || !st.current.id().equals("heal_4_hearts"))) {
			for (Move m : st.moves) {
				if (!m.id().equals("shield_break")) continue;
				if (m.start(c)) {
					if (st.current != null) endCurrent(st, c);
					st.current = m;
					USED.merge(bot.getGameProfile().name() + ":" + m.id(), 1, Integer::sum);
				}
			}
		}
		if (st.current == null || !st.current.id().equals("heal_4_hearts")) {
			Move heal = st.moves.get(0);
			if (heal.id().equals("heal_4_hearts") && (CombatMaster.is(profile.difficulty()) || profile.moves().contains(heal.id()))
					&& heal.start(c)) {
				if (st.current != null) endCurrent(st, c);
				st.current = heal;
				USED.merge(bot.getGameProfile().name() + ":" + heal.id(), 1, Integer::sum);
			}
		}
		if (st.current != null && (st.current.id().equals("strafe_pressure") || st.current.id().equals("anti_stall") || st.current.id().equals("master_circle") || st.current.id().equals("reach_combo"))) {
			// Shield breaks and 3-block hits are worth more than a strafe: they take over the bot mid-strafe.
			for (Move m : st.moves) {
				if (!(m.id().equals("shield_break") || m.id().equals("three_block_hit"))) continue;
				if (m.start(c)) { endCurrent(st, c); st.current = m; skip = m.tick(c); if (!skip) endCurrent(st, c); break; }
			}
		}
		if (st.current != null && ++st.curTicks > 240) { // watchdog: no move may hold a bot for more than 12 seconds
			System.err.println("[pvpchamps] " + bot.getGameProfile().name() + " was stuck in " + st.current.id() + " for " + st.curTicks + " ticks; releasing it");
			st.setCooldown(st.current.id(), 600);
			endCurrent(st, c);
			skip = false;
		}
		if (st.current != null) {
			diag("busy:" + st.current.id());
			if (st.current.tick(c)) skip = true;
			else endCurrent(st, c);
		} else if (st.tick % 4 == 0) {
			for (Move m : st.moves) {
				if (!profile.moves().contains(m.id()) && !(com.relay.pvpchamps.CombatMaster.is(profile.difficulty())
						&& (m.id().equals("strafe_pressure") || m.id().equals("anti_stall") || m.id().equals("shield_break") || m.id().equals("three_block_hit") || m.id().equals("heal_4_hearts") || m.id().equals("master_circle") || m.id().equals("mace_smash") || m.id().equals("reach_combo")))) continue;
				diag("try:" + m.id());
				if (m.start(c)) {
					st.current = m;
					USED.merge(bot.getGameProfile().name() + ":" + m.id(), 1, Integer::sum);
					skip = m.tick(c);
					if (!skip) endCurrent(st, c);
					break;
				}
			}
		}
		st.lastTargetAbsorb = target.getAbsorptionAmount();
		return skip;
	}

	private static void endCurrent(State st, Ctx c) {
		Move m = st.current;
		st.current = null;
		st.curTicks = 0;
		if (m != null) m.end(c);
		if (st.next != null) { st.current = st.next; st.next = null; }
	}
}
