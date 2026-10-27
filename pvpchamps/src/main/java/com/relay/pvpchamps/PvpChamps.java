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
		Arena.load();
		Ping.load();
		Rankings.load();
		Highlights.load();
		Predictions.load();
		Commentary.init();
		WebStats.start(); // up from game launch to game exit: the page works with or without a world loaded
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (WebStats.port() > 0) handler.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"§7PvP Champs stats: " + WebStats.url()));
		});
		vexbot.BotSettings.botDontLeave = true; // bots stay on the server instead of dropping out on their own
		vexbot.BotSettings.botLeaveOnDeath = false;
		vexbot.BotSettings.shulkerRegear = false; // no placing shulker boxes (they ended up as a wall)
		vexbot.BotSettings.enderChestRegear = false;
		vexbot.BotSettings.pearlChasing = false; // two bots chasing each other with pearls overshoot and loop; PearlClose throws one aimed pearl
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> PvpCommands.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(server -> { MatchManager.tick(server); Commentary.tick(server); });
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> Commentary.damage(entity, source, taken));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof net.minecraft.server.level.ServerPlayer v && v.level().getServer() != null)
				Commentary.kill(v.level().getServer(), v, source.getEntity() instanceof net.minecraft.server.level.ServerPlayer k ? k : null);
		});
		// Partners in a 2v2 never hurt each other, whatever the bots decide to swing at.
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(MatchManager.current() instanceof TeamMatch tm && tm.friendly(entity, source)));
		VexBotEvents.registerDeathListener((victim, killer) -> MatchManager.onDeath(victim.getGameProfile().name()));
	}
}
