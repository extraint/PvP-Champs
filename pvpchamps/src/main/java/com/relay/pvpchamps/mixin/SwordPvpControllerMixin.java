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
	private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void pvpchamps$moves(CallbackInfo ci) {
		try {
			if (MoveEngine.tick((SwordPvpController) (Object) this)) ci.cancel();
		} catch (RuntimeException e) { // a bug in a move must never freeze the bot: hand the tick back to Vexbot
			if (REPORTED.add(e.toString())) { System.err.println("[pvpchamps] a move crashed (the bot carries on with Vexbot's own AI): " + e); e.printStackTrace(); }
		}
	}
}
