package com.relay.pvpchamps.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DISABLED (not listed in pvpchamps.mixins.json; add it there to trace a vanishing weapon): prints a stack trace whenever a sword leaves a player's inventory. */
@Mixin(Inventory.class)
public abstract class InventoryTraceMixin {
	@Inject(method = "setItem", at = @At("HEAD"))
	private void pvpchamps$set(int slot, ItemStack stack, CallbackInfo ci) {
		ItemStack old = ((Inventory) (Object) this).getItem(slot);
		if (isSword(old) && stack.isEmpty()) trace("setItem(" + slot + ", empty)");
	}

	@Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
	private void pvpchamps$remove(int slot, int n, CallbackInfoReturnable<ItemStack> cir) {
		if (isSword(((Inventory) (Object) this).getItem(slot))) trace("removeItem(" + slot + ")");
	}

	@Inject(method = "removeItemNoUpdate", at = @At("HEAD"))
	private void pvpchamps$removeNoUpdate(int slot, CallbackInfoReturnable<ItemStack> cir) {
		if (isSword(((Inventory) (Object) this).getItem(slot))) trace("removeItemNoUpdate(" + slot + ")");
	}

	@Inject(method = "clearContent", at = @At("HEAD"))
	private void pvpchamps$clear(CallbackInfo ci) {
		for (int i = 0; i < 41; i++) if (isSword(((Inventory) (Object) this).getItem(i))) { trace("clearContent"); return; }
	}

	@Inject(method = "dropAll", at = @At("HEAD"))
	private void pvpchamps$dropAll(CallbackInfo ci) {
		for (int i = 0; i < 41; i++) if (isSword(((Inventory) (Object) this).getItem(i))) { trace("dropAll"); return; }
	}

	private static boolean isSword(ItemStack s) { return s.getItem() == Items.NETHERITE_SWORD || s.getItem() == Items.DIAMOND_SWORD; }

	private static void trace(String what) {
		new Throwable("SWORD LEAVES INVENTORY via " + what).printStackTrace();
	}
}
