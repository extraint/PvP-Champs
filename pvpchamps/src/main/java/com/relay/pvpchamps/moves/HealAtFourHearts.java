package com.relay.pvpchamps.moves;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import vexbot.fakeplayer.bot.BotPlayerActionPack;

/**
 * Combat Master healing, in two gears like the pros:
 * <ul>
 * <li>Danger (4 hearts or less): throw an instant-health splash potion at the bot's own feet. It lands at once, so
 * it works even with the opponent in reach. With no potion left, eat a golden apple instead.</li>
 * <li>Safe window (6 hearts or less, opponent more than 5 blocks away): eat a golden apple to top up.</li>
 * </ul>
 * Vexbot's own heal threshold is fixed in its jar, so this runs ahead of it.
 */
public final class HealAtFourHearts implements Move {
	private enum Mode { POTION, APPLE, PEARL }

	private Mode mode;
	private int ticks, fled;
	private float fleeYaw;

	@Override public String id() { return "heal_4_hearts"; }

	private static boolean isHealing(ItemStack s) {
		if (s.getItem() != Items.SPLASH_POTION) return false;
		PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
		return pc != null && pc.potion().isPresent()
			&& (pc.potion().get().equals(Potions.HEALING) || pc.potion().get().equals(Potions.STRONG_HEALING));
	}

	private static int potionSlot(Ctx c) {
		var inv = c.bot.getInventory();
		for (int i = 0; i < 36; i++) if (isHealing(inv.getItem(i))) return i;
		return -1;
	}

	/** The opponent is stuck in a cobweb: slowed and unable to crit, the best moment to heal (HT2 Diamond SMP guide). */
	private static boolean inWeb(Ctx c) {
		return c.target.level().getBlockState(c.target.blockPosition()).is(net.minecraft.world.level.block.Blocks.COBWEB);
	}

	/**
	 * Down on health in a trade it cannot gap out of (opponent on top of it, clearly ahead, no healing potion): the guide's
	 * answer is to pearl away, then gap. Throws toward the far side of the arena, away from the opponent.
	 */
	private static boolean pearlAway(Ctx c, float hp) {
		return hp <= 6.5f && c.dist <= 4.5 && c.target.getHealth() >= hp + 3f && c.count(Items.ENDER_PEARL) > 0 && potionSlot(c) < 0
			&& c.st.cooldown("pearl_away") <= 0 && c.bot.onGround();
	}

	/** The opponent is moving away from the bot: a good moment to disengage and heal instead of chasing. */
	private static boolean opponentRunning(Ctx c) {
		var to = c.target.position().subtract(c.bot.position()).multiply(1, 0, 1);
		if (to.lengthSqr() < 1e-4) return false;
		return c.target.getDeltaMovement().multiply(1, 0, 1).dot(to.normalize()) > 0.12 && c.dist > 2.5;
	}

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.bot.isUsingItem()) return MoveEngine.reject(id(), "busy");
		float hp = c.bot.getHealth();
		double skill = ReachCombo.skillOf(c);      // 0..1 from the bot's tier in this kit
		float dangerHp = (float) (6 + 2 * skill);    // a real emergency: 3 to 4 hearts, not the first scratch
		float topUpHp = (float) (10 + 4 * skill);    // top-ups wait for a safe window (see below), 5 to 7 hearts
		boolean danger = hp <= dangerHp, safeTopUp = hp <= topUpHp && (c.dist > 7.0 || opponentRunning(c) || inWeb(c)); // heal earlier: 5 hearts is danger, 7 is a safe top-up
		// After a totem pop the bot has Regeneration II for 45 seconds: let it heal on its own and keep the pressure, and only
		// step in with a potion if it is really low.
		if (c.bot.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION) && hp > 6f) return MoveEngine.reject(id(), "regen_active");
		if (!danger && !safeTopUp) return MoveEngine.reject(id(), "healthy_hp" + (int) Math.min(hp, 20));
		if (danger && pearlAway(c, hp)) mode = Mode.PEARL;
		else if (danger && potionSlot(c) >= 0) mode = Mode.POTION;
		else if (c.count(Items.GOLDEN_APPLE) > 0) {
			// Never gap with the opponent on top of you unless it is the last resort: they get free hits while you eat.
			if (c.dist < 3.0 && hp > 6f && !opponentRunning(c) && !inWeb(c)) return MoveEngine.reject(id(), "opponent_near_no_gap");
			mode = Mode.APPLE;
		}
		else return MoveEngine.reject(id(), "nothing_to_heal_with");
		ticks = 0;
		fled = 0;
		MoveEngine.reject(id(), "start_" + mode.name().toLowerCase());
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++ticks > 140) return false;
		if (mode == Mode.PEARL) {
			if (!c.hold(Items.ENDER_PEARL)) { mode = Mode.APPLE; return true; }
			var origin = com.relay.pvpchamps.MatchManager.origin();
			net.minecraft.world.phys.Vec3 away = c.bot.position().subtract(c.target.position()).multiply(1, 0, 1);
			net.minecraft.world.phys.Vec3 dir = away.lengthSqr() < 1e-4 ? new net.minecraft.world.phys.Vec3(1, 0, 0) : away.normalize();
			net.minecraft.world.phys.Vec3 land = c.bot.position().add(dir.scale(18));
			if (origin != null) { // stay on the arena floor: never throw out over the stands
				net.minecraft.world.phys.Vec3 off = land.subtract(origin).multiply(1, 0, 1);
				if (off.length() > 34) land = origin.add(off.normalize().scale(34));
				land = new net.minecraft.world.phys.Vec3(land.x, origin.y, land.z);
			}
			float[] a = Ballistics.aim(c.bot.getEyePosition(), land);
			c.pack.look(a[0], a[1]);
			c.pack.stopMovement();
			if (ticks == 4) c.useOnce();
			if (ticks >= 7) { c.st.setCooldown("pearl_away", 400); mode = Mode.APPLE; ticks = 0; fled = 0; } // landed far away: now the gap to eat in
			return true;
		}
		if (mode == Mode.POTION) {
			int slot = potionSlot(c);
			if (slot < 0) return false;
			if (ticks == 1) {
				if (!c.hold(c.bot.getInventory().getItem(slot).getItem())) return false;
				return true; // one tick to switch
			}
			// Throw it straight down so it lands on the bot.
			c.pack.look(c.bot.getYRot(), 90f);
			if (ticks == 2) c.useOnce();
			if (ticks < 4) return true;
			c.pack.stop(BotPlayerActionPack.ActionType.USE);
			return flee(c, 4.5, 8); // the potion works where it landed: a short hop out of reach, not a chase
		}
		if (c.dist < 4.0 && c.hp() > 6f && fled < 20 && flee(c, 5.0, 20)) return true; // a short break from point-blank range, then eat where it stands
		if (!c.hold(Items.GOLDEN_APPLE)) return false;
		c.face(c.targetEye());                          // keep eyes on the opponent while eating, not on wherever the run ended
		c.pack.stopMovement();
		c.pack.start(BotPlayerActionPack.ActionType.USE, BotPlayerActionPack.Action.continuous());
		return c.bot.isUsingItem() && c.bot.getHealth() < 20f;
	}

	/** Sprint-jump straight away from the opponent. Returns false once the gap is wide enough or the time is up. */
	private boolean flee(Ctx c, double gap, int maxTicks) {
		if (fled == 0 && c.dist <= 3.2 && c.bot.getAttackStrengthScale(0.5f) >= 0.9f) c.attackOnce(); // fly-out: land one hit, then get flung away
		if (c.dist >= gap || ++fled > maxTicks) return false;
		// Pick the way out once and stick to it: re-picking every few ticks made the head swing back and forth. Look again
		// only if the bot has barely moved (a wall in the way).
		if (fled == 1 || (fled % 10 == 1 && c.st.stillTicks > 5)) fleeYaw = c.openYawAwayFrom(c.target.position());
		c.pack.look(fleeYaw, 0f);
		c.pack.setForward(1f);
		c.pack.setSprinting(true);
		if (c.bot.onGround()) c.jumpOnce(); // bunny hop: jumping keeps the sprint speed up
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.pack.stop(BotPlayerActionPack.ActionType.USE);
		c.restoreSlot();
		c.st.setCooldown(id(), mode == Mode.POTION ? 6 : 12); // shorter gaps: a second drink in a fight is often what keeps a bot alive
	}
}
