package com.relay.pvphub;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Entry point: a PvP server in a mod. See the README. */
public final class PvpHub implements ModInitializer {
	@Override
	public void onInitialize() {
		Config.load();
		Stats.load();

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> HubCommands.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Arenas.init();
			Hub.buildLobby(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Stats.save());

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Hub.toLobby(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.player;
			Queues.leave(p, false);
			Duels.forfeit(p);
			Ffa.leave(p);
			Spectate.stop(p);
			Requests.clear(p);
			Hub.forget(p);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (Arenas.ffa() == null) return;
			Hub.tick(server);
			Queues.tick();
			Duels.tick();
			Ffa.tick();
			Requests.tick();
		});

		// Fighting is only allowed where a fight is on.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p)) return true;
			return switch (Hub.state(p)) {
				case LOBBY, QUEUE, SPECTATE -> false;
				case DUEL -> Duels.allowDamage(p, source);
				case FFA -> Ffa.allowDamage(p, source);
			};
		});
		// A fighter who would die has the fight ended for them instead of dying.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p)) return true;
			if (Duels.onDeath(p)) return false;
			if (Ffa.onDeath(p, source)) return false;
			return Hub.inLobby(p) ? false : true;
		});

		// The crowd comes and goes without a word.
		net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server, msg, overlay) -> {
			if (msg.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc && tc.getKey().startsWith("multiplayer.player.") && tc.getArgs().length > 0) {
				Object a = tc.getArgs()[0];
				String n = a instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(a);
				if (Crowd.isName(n)) return false;
			}
			return true;
		});
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vexbot")) {
			Population.enable();
			ServerTickEvents.END_SERVER_TICK.register(Population::tick);
			System.out.println("[pvphub] VexBot found: the lobby crowd is on (" + Config.get().crowdBots + " bots)");
		}

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer p && Hub.state(p) != Hub.State.DUEL && Hub.state(p) != Hub.State.FFA) {
				String a = HubItems.actionOf(player.getItemInHand(hand));
				if (a != null) {
					Menus.action(p, a);
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
		// The lobby is not buildable, and only the fight arenas can be changed from inside.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer p)) return true;
			Arena a = Arenas.at(p);
			return a != null && a.inside(pos) && (Hub.state(p) == Hub.State.FFA || (Duels.of(p) != null && Duels.of(p).phase == Duel.Phase.FIGHT));
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer p)) return InteractionResult.PASS;
			if (Hub.state(p) == Hub.State.DUEL || Hub.state(p) == Hub.State.FFA) return InteractionResult.PASS;
			String a = HubItems.actionOf(player.getItemInHand(hand));
			if (a != null) { Menus.action(p, a); return InteractionResult.FAIL; }
			return InteractionResult.FAIL;      // nothing in the lobby can be used
		});
		System.out.println("[pvphub] loaded");
	}
}
