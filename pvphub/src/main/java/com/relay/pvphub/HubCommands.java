package com.relay.pvphub;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class HubCommands {
	private HubCommands() {}

	private static final java.util.Map<String, ServerPlayer> FAKES = new java.util.HashMap<>();

	private static void say(CommandSourceStack s, String t) { s.sendSuccess(() -> Component.literal(t), false); }

	private static Kit kit(CommandSourceStack s, String id) {
		Kit k = Kit.byId(id);
		if (k == null) s.sendFailure(Component.literal("Unknown kit. Kits: " + String.join(", ", Kit.ids())));
		return k;
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		for (String n : new String[] {"hub", "lobby", "l"}) d.register(literal(n).executes(c -> { Hub.toLobby(c.getSource().getPlayerOrException()); return 1; }));

		d.register(literal("queue")
			.then(literal("leave").executes(c -> { Queues.leave(c.getSource().getPlayerOrException(), true); return 1; }))
			.then(argument("kit", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Kit.ids(), b))
				.executes(c -> queue(c.getSource(), StringArgumentType.getString(c, "kit"), false))
				.then(literal("ranked").executes(c -> queue(c.getSource(), StringArgumentType.getString(c, "kit"), true)))
				.then(literal("unranked").executes(c -> queue(c.getSource(), StringArgumentType.getString(c, "kit"), false)))));

		d.register(literal("kiteditor")
			.executes(c -> { KitEditor.open(c.getSource().getPlayerOrException()); return 1; })
			.then(literal("save").executes(c -> { KitEditor.confirm(c.getSource().getPlayerOrException()); return 1; }))
			.then(literal("cancel").executes(c -> { KitEditor.cancel(c.getSource().getPlayerOrException()); return 1; }))
			.then(literal("reset").executes(c -> { KitEditor.resetLayout(c.getSource().getPlayerOrException()); return 1; })));

		d.register(literal("rank")
			.then(argument("rank", StringArgumentType.greedyString()).suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Ranks.STANDARD).map(Ranks.Rank::id).toList(), b))
				.executes(c -> {
					Ranks.Rank r = Ranks.byId(StringArgumentType.getString(c, "rank"));
					if (r == null) { c.getSource().sendFailure(Component.literal("Ranks: OWNER, DEVELOPER, ADMIN, MANAGER, SENIOR MOD, ARBITER, MOD, HELPER, MONEY SMP, CRACKBEAST, CREATOR, HQ+, DEFAULT")); return 0; }
					Config.get().playerRank = r.id();
					Config.save();
					say(c.getSource(), "Your rank is now " + r.id());
					return 1;
				})));

		d.register(literal("regions").executes(c -> { Regions.open(c.getSource().getPlayerOrException()); return 1; }));

		d.register(literal("ffa").executes(c -> { Ffa.join(c.getSource().getPlayerOrException()); return 1; }));

		d.register(literal("duel")
			.then(literal("accept").then(argument("from", EntityArgument.player()).executes(c -> { Requests.accept(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "from")); return 1; })))
			.then(literal("deny").then(argument("from", EntityArgument.player()).executes(c -> { Requests.deny(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "from")); return 1; })))
			.then(argument("target", EntityArgument.player())
				.executes(c -> duel(c.getSource(), EntityArgument.getPlayer(c, "target"), Kit.SWORD.id, 1))
				.then(argument("kit", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Kit.ids(), b))
					.executes(c -> duel(c.getSource(), EntityArgument.getPlayer(c, "target"), StringArgumentType.getString(c, "kit"), 1))
					.then(argument("bestof", IntegerArgumentType.integer(1, 9)).executes(c -> duel(c.getSource(), EntityArgument.getPlayer(c, "target"), StringArgumentType.getString(c, "kit"), IntegerArgumentType.getInteger(c, "bestof")))))));

		d.register(literal("spectate")
			.executes(c -> { Spectate.open(c.getSource().getPlayerOrException()); return 1; })
			.then(argument("player", EntityArgument.player()).executes(c -> {
				ServerPlayer me = c.getSource().getPlayerOrException();
				Duel du = Duels.of(EntityArgument.getPlayer(c, "player"));
				if (du == null) { c.getSource().sendFailure(Component.literal("They are not in a duel.")); return 0; }
				Spectate.watch(me, du);
				return 1;
			})));

		d.register(literal("stats").executes(c -> { Menus.stats(c.getSource().getPlayerOrException()); return 1; }));

		d.register(literal("leaderboard")
			.then(literal("ffa").executes(c -> { Menus.ffaBoard(c.getSource().getPlayerOrException()); return 1; }))
			.then(argument("kit", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Kit.ids(), b))
				.executes(c -> {
					Kit k = kit(c.getSource(), StringArgumentType.getString(c, "kit"));
					if (k == null) return 0;
					Menus.leaderboard(c.getSource().getPlayerOrException(), k);
					return 1;
				})));

		d.register(literal("pvphub")
			.then(literal("setlobby").executes(c -> {
				ServerPlayer p = c.getSource().getPlayerOrException();
				Config cfg = Config.get();
				cfg.lobbyX = p.getX(); cfg.lobbyY = p.getY(); cfg.lobbyZ = p.getZ(); cfg.lobbyYaw = p.getYRot();
				Config.save();
				say(c.getSource(), "Lobby spawn moved to where you stand.");
				return 1;
			}))
			.then(literal("dev")
				.then(literal("spawn").then(argument("name", StringArgumentType.word()).executes(c -> {
					String n = StringArgumentType.getString(c, "name");
					var level = Hub.world(c.getSource().getServer());
					var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(n.getBytes()), n));
					FAKES.put(n, fp);
					Hub.toLobby(fp);
					say(c.getSource(), "Spawned fake " + n);
					return 1;
				})))
				.then(literal("queue").then(argument("name", StringArgumentType.word()).then(argument("kit", StringArgumentType.word()).then(argument("ranked", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
					ServerPlayer fp = FAKES.get(StringArgumentType.getString(c, "name"));
					Kit k = kit(c.getSource(), StringArgumentType.getString(c, "kit"));
					if (fp == null || k == null) return 0;
					Queues.join(fp, k, com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "ranked"));
					return 1;
				})))))
				.then(literal("kill").then(argument("name", StringArgumentType.word()).executes(c -> kill(c.getSource(), StringArgumentType.getString(c, "name"), null))
					.then(argument("killer", StringArgumentType.word()).executes(c -> kill(c.getSource(), StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "killer"))))))
				.then(literal("ffa").then(argument("name", StringArgumentType.word()).executes(c -> {
					ServerPlayer p = FAKES.get(StringArgumentType.getString(c, "name"));
					if (p == null) return 0;
					Ffa.join(p);
					return 1;
				}))))
			.then(literal("status").executes(c -> {
				say(c.getSource(), "Arenas free: " + Arenas.free() + "/" + Arenas.duels().size() + ", duels: " + Duels.active().size() + ", FFA: " + Ffa.count(c.getSource().getServer()) + Duels.active().stream().map(x -> " [" + x.phase + " " + x.score[0] + "-" + x.score[1] + " t" + x.fightTicks + " hp" + Math.round(x.p[0].getHealth()) + "/" + Math.round(x.p[1].getHealth()) + (x.simulated ? " sim" : "") + "]").reduce("", String::concat));
				return 1;
			})));
	}

	/** Test helper: fake players are invulnerable, so run the death handlers directly. */
	private static int kill(CommandSourceStack s, String name, String killer) {
		ServerPlayer p = FAKES.get(name);
		if (p == null) return 0;
		ServerPlayer k = killer == null ? null : FAKES.get(killer);
		net.minecraft.world.damagesource.DamageSource src = k == null ? p.level().damageSources().generic() : p.level().damageSources().playerAttack(k);
		boolean handled = Duels.onDeath(p) || Ffa.onDeath(p, src);
		say(s, name + " death handled=" + handled);
		return 1;
	}

	private static int queue(CommandSourceStack s, String id, boolean ranked) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Kit k = kit(s, id);
		if (k == null) return 0;
		Queues.join(s.getPlayerOrException(), k, ranked);
		return 1;
	}

	private static int duel(CommandSourceStack s, ServerPlayer target, String id, int bestOf) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Kit k = kit(s, id);
		if (k == null) return 0;
		Requests.send(s.getPlayerOrException(), target, k, bestOf | 1);
		return 1;
	}
}
