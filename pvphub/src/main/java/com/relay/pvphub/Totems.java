package com.relay.pvphub;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Totems of undying anywhere in the inventory. Vanilla only reads the hands; the kits keep spare totems in the inventory
 * (as the real servers' kits do), so a fighter who would die pops one of those instead.
 */
public final class Totems {
	private Totems() {}

	/** True if a totem was used up and the death should be cancelled. */
	public static boolean pop(ServerPlayer p) {
		Inventory inv = p.getInventory();
		int slot = -1;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.TOTEM_OF_UNDYING)) { slot = i; break; }
		}
		if (slot < 0) return false;
		inv.getItem(slot).shrink(1);
		p.setHealth(1.0f);
		p.removeAllEffects();
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
		p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
		p.clearFire();
		p.level().broadcastEntityEvent(p, (byte) 35);
		p.inventoryMenu.broadcastFullState();
		return true;
	}
}
