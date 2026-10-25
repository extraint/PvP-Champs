package com.relay.pvpchamps.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DISABLED (not listed in pvpchamps.mixins.json; add it there to trace a vanishing weapon): prints a stack trace whenever a sword stack is shrunk to nothing, with its durability. */
@Mixin(ItemStack.class)
public abstract class ItemStackTraceMixin {
	@Inject(method = "shrink", at = @At("HEAD"))
	private void pvpchamps$shrink(int n, CallbackInfo ci) {
		ItemStack s = (ItemStack) (Object) this;
		if ((s.getItem() == Items.NETHERITE_SWORD || s.getItem() == Items.DIAMOND_SWORD) && s.getCount() - n <= 0)
			new Throwable("SWORD SHRUNK dmg=" + s.getDamageValue() + "/" + s.getMaxDamage()).printStackTrace();
	}

	@Inject(method = "setCount", at = @At("HEAD"))
	private void pvpchamps$count(int n, CallbackInfo ci) {
		ItemStack s = (ItemStack) (Object) this;
		if ((s.getItem() == Items.NETHERITE_SWORD || s.getItem() == Items.DIAMOND_SWORD) && n <= 0)
			new Throwable("SWORD SETCOUNT0 dmg=" + s.getDamageValue() + "/" + s.getMaxDamage()).printStackTrace();
	}
}
