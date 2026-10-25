package com.relay.pvpchamps.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** DISABLED (not listed in pvpchamps.mixins.json): prints who drops what, with a stack trace (enable in pvpchamps.mixins.json to trace vanishing items). */
@Mixin(ServerPlayer.class)
public abstract class DropTraceMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"))
	private void pvpchamps$drop(ItemStack s, boolean a, boolean b, CallbackInfoReturnable<?> cir) {
		new Throwable("DROP " + ((ServerPlayer) (Object) this).getGameProfile().name() + " " + s.getCount() + "x " + s.getItem()).printStackTrace();
	}

	@Inject(method = "drop(Z)V", at = @At("HEAD"))
	private void pvpchamps$dropSel(boolean all, CallbackInfo ci) {
		var p = (ServerPlayer) (Object) this;
		new Throwable("DROPSEL " + p.getGameProfile().name() + " " + p.getInventory().getSelectedItem()).printStackTrace();
	}
}
