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
	private enum Mode { POTION, APPLE }

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

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.bot.isUsingItem()) return MoveEngine.reject(id(), "busy");
		float hp = c.bot.getHealth();
		boolean danger = hp <= 8f, safeTopUp = hp <= 12f && c.dist > 5.0;
		if (!danger && !safeTopUp) return MoveEngine.reject(id(), "healthy_hp" + (int) Math.min(hp, 20));
		if (danger && potionSlot(c) >= 0) mode = Mode.POTION;
		else if (c.count(Items.GOLDEN_APPLE) > 0) mode = Mode.APPLE;
		else return MoveEngine.reject(id(), "nothing_to_heal_with");
		ticks = 0;
		fled = 0;
		MoveEngine.reject(id(), "start_" + mode.name().toLowerCase());
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++ticks > 140) return false;
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
			return flee(c, 6.0, 25); // the potion is on the floor and working: put distance between them while it ticks
		}
		if (c.dist < 7.5 && fled < 60 && flee(c, 7.5, 60)) return true; // get away first, eat once there is room
		if (!c.hold(Items.GOLDEN_APPLE)) return false;
		c.pack.stopMovement();
		c.pack.start(BotPlayerActionPack.ActionType.USE, BotPlayerActionPack.Action.continuous());
		return c.bot.isUsingItem() && c.bot.getHealth() < 20f;
	}

	/** Sprint-jump straight away from the opponent. Returns false once the gap is wide enough or the time is up. */
	private boolean flee(Ctx c, double gap, int maxTicks) {
		if (c.dist >= gap || ++fled > maxTicks) return false;
		if (fled % 4 == 1) fleeYaw = c.openYawAwayFrom(c.target.position()); // re-check the way out every few ticks
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
		c.st.setCooldown(id(), mode == Mode.POTION ? 12 : 30);
	}
}
