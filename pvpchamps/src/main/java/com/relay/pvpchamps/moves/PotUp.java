package com.relay.pvpchamps.moves;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * SMP buff discipline: strength and speed run out mid-fight. When one is missing and the opponent is not on top of
 * the bot, throw the matching splash potion at its own feet before re-engaging.
 */
public final class PotUp implements Move {
	private int t, slot;

	@Override public String id() { return "pot_up"; }

	private static boolean matches(ItemStack s, String word) {
		if (s.getItem() != Items.SPLASH_POTION) return false;
		PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
		return pc != null && pc.potion().isPresent() && pc.potion().get().getRegisteredName().contains(word);
	}

	private static int find(Ctx c, String word) {
		var inv = c.bot.getInventory();
		for (int i = 0; i < 36; i++) if (matches(inv.getItem(i), word)) return i;
		return -1;
	}

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.dist < 4.5 || c.bot.isUsingItem() || !c.bot.onGround()) return MoveEngine.reject(id(), "busy_or_close");
		slot = -1;
		if (!c.bot.hasEffect(MobEffects.STRENGTH)) slot = find(c, "strength");
		if (slot < 0 && !c.bot.hasEffect(MobEffects.SPEED)) slot = find(c, "swiftness");
		if (slot < 0) return MoveEngine.reject(id(), "buffed_or_none");
		t = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 8) return false;
		if (t == 1) return c.hold(c.bot.getInventory().getItem(slot).getItem());
		c.pack.stopMovement();
		c.pack.look(c.bot.getYRot(), 90f);
		if (t == 3) c.useOnce();
		return t < 5;
	}

	@Override
	public void end(Ctx c) {
		c.restoreSlot();
		c.st.setCooldown(id(), 200);
	}
}
