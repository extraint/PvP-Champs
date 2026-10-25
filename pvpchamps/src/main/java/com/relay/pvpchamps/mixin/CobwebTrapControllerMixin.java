package com.relay.pvpchamps.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vexbot.bot.ai.CobwebTrapController;

/**
 * Vexbot's emergency web hands the cobweb stack to {@code useItemOn} but the bot's hand can be on another slot, and the
 * game then consumes one item from the held stack instead of the cobweb. A sword or axe (a stack of one) simply
 * vanished. The cobweb slot is selected first, so the web is the thing that gets used up.
 */
@Mixin(CobwebTrapController.class)
public abstract class CobwebTrapControllerMixin {
	@Shadow private ServerPlayer bot;
	@Shadow private int hitWebSlot;

	@Inject(method = "placeCobwebAt", at = @At("HEAD"))
	private void pvpchamps$holdTheWeb(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		var inv = bot.getInventory();
		if (hitWebSlot >= 0 && hitWebSlot <= 8 && inv.getItem(hitWebSlot).is(Items.COBWEB)) inv.setSelectedSlot(hitWebSlot);
	}
}
