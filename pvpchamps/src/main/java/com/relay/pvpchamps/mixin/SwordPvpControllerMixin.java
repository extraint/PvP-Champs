package com.relay.pvpchamps.mixin;

import com.relay.pvpchamps.moves.MoveEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vexbot.bot.ai.SwordPvpController;

/** Lets the PvP Champs moves take over a bot's tick while one is running. */
@Mixin(SwordPvpController.class)
public class SwordPvpControllerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void pvpchamps$moves(CallbackInfo ci) {
		if (MoveEngine.tick((SwordPvpController) (Object) this)) ci.cancel();
	}
}
