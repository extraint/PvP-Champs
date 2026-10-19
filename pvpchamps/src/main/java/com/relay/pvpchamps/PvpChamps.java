package com.relay.pvpchamps;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import vexbot.api.VexBotEvents;

/** Entry point: VexBot extension that simulates the PvP Champs event. See the README. */
public final class PvpChamps implements ModInitializer {
	@Override
	public void onInitialize() {
		Roster.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> PvpCommands.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(server -> MatchManager.tick());
		VexBotEvents.registerDeathListener((victim, killer) -> MatchManager.onDeath(victim.getGameProfile().name()));
	}
}
