package com.relay.pvpchamps;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.relay.pvpchamps.moves.MoveEngine;
import java.util.Random;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /pvpchamps ... */
public final class PvpCommands {
	private PvpCommands() {}

	private static final String EVENT = "PVP CHAMPS II";
	private static final Random RNG = new Random();

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("pvpchamps")
			.then(Commands.literal("roster").executes(c -> roster(c.getSource())))
			.then(Commands.literal("kits").executes(c -> kits(c.getSource())))
			.then(Commands.literal("challenge")
				.then(Commands.argument("opponent", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(Roster.all().stream().map(Roster.Opponent::name), b))
					.executes(c -> challenge(c, null, 3))
					.then(Commands.argument("kit", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Kits.ids(), b))
						.executes(c -> challenge(c, StringArgumentType.getString(c, "kit"), 3))
						.then(Commands.argument("bestof", IntegerArgumentType.integer(1, 9))
							.executes(c -> challenge(c, StringArgumentType.getString(c, "kit"), IntegerArgumentType.getInteger(c, "bestof")))))))
			.then(Commands.literal("watch")
				.then(Commands.argument("a", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(Roster.all().stream().map(Roster.Opponent::name), b))
					.then(Commands.argument("b", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Roster.all().stream().map(Roster.Opponent::name), b))
						.executes(c -> watch(c, null))
						.then(Commands.argument("kit", StringArgumentType.word())
							.suggests((c, b) -> SharedSuggestionProvider.suggest(Kits.ids(), b))
							.executes(c -> watch(c, StringArgumentType.getString(c, "kit")))))))
			.then(Commands.literal("tournament")
				.then(Commands.literal("start").executes(c -> tournamentStart(c.getSource())))
				.then(Commands.literal("status").executes(c -> tournamentStatus(c.getSource()))))
			.then(Commands.literal("stop").executes(c -> { MatchManager.stop(); say(c.getSource(), "Stopped."); return 1; }))
			.then(Commands.literal("moves")
				.then(Commands.literal("on").executes(c -> { MoveEngine.setEnabled(true); say(c.getSource(), "Extra moves on."); return 1; }))
				.then(Commands.literal("off").executes(c -> { MoveEngine.setEnabled(false); say(c.getSource(), "Extra moves off (plain VexBot)."); return 1; }))
				.then(Commands.literal("stats").executes(c -> { say(c.getSource(), "Moves used so far: " + MoveEngine.usage() + "  diagnostics: " + MoveEngine.DIAG); return 1; }))));
	}

	private static void say(CommandSourceStack src, String text) { src.sendSuccess(() -> Component.literal(text), false); }

	private static int roster(CommandSourceStack src) {
		for (Roster.Opponent o : Roster.all()) {
			say(src, o.name() + " - " + o.title() + " [" + o.difficulty() + "/" + o.style() + ", rating " + o.rating() + "] kits: " + o.kits() + " moves: " + o.moves());
		}
		return Roster.all().size();
	}

	private static int kits(CommandSourceStack src) {
		for (Kits k : Kits.values()) say(src, k.id + " - " + k.label);
		return Kits.values().length;
	}

	private static int challenge(CommandContext<CommandSourceStack> c, String kitId, int bestOf) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack src = c.getSource();
		ServerPlayer p = src.getPlayerOrException();
		if (MatchManager.busy()) { say(src, "A match is already running (/pvpchamps stop)."); return 0; }
		Roster.Opponent o = Roster.byName(StringArgumentType.getString(c, "opponent"));
		if (o == null) { say(src, "Unknown opponent. /pvpchamps roster"); return 0; }
		Kits kit = kitId == null ? Kits.byId(o.kits().get(RNG.nextInt(o.kits().size()))) : Kits.byId(kitId);
		if (kit == null) { say(src, "Unknown kit. /pvpchamps kits"); return 0; }
		MatchManager.begin(new Match(src.getServer(), p, false, p.getGameProfile().name(), o.name(), kit, bestOf,
			"Exhibition", EVENT, null));
		return 1;
	}

	private static int watch(CommandContext<CommandSourceStack> c, String kitId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack src = c.getSource();
		ServerPlayer p = src.getPlayerOrException();
		if (MatchManager.busy()) { say(src, "A match is already running (/pvpchamps stop)."); return 0; }
		Roster.Opponent a = Roster.byName(StringArgumentType.getString(c, "a")), b = Roster.byName(StringArgumentType.getString(c, "b"));
		if (a == null || b == null || a == b) { say(src, "Pick two different roster players. /pvpchamps roster"); return 0; }
		Kits kit = kitId == null ? Kits.values()[RNG.nextInt(Kits.values().length)] : Kits.byId(kitId);
		if (kit == null) { say(src, "Unknown kit. /pvpchamps kits"); return 0; }
		if (!com.relay.spectate.RelayServerApi.canDrive(p)) say(src, "(You don't have the Relay Spectator client mod: you'll see the fight but not the camera or HUD.)");
		MatchManager.begin(new Match(src.getServer(), p, true, a.name(), b.name(), kit, 3, "Main Event", EVENT, null));
		return 1;
	}

	private static int tournamentStart(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		if (MatchManager.busy()) { say(src, "A match is already running (/pvpchamps stop)."); return 0; }
		MatchManager.setTournament(new Tournament(src.getServer(), p));
		return 1;
	}

	private static int tournamentStatus(CommandSourceStack src) {
		Tournament t = MatchManager.tournament();
		say(src, t == null ? "No tournament running. /pvpchamps tournament start" : t.status());
		return 1;
	}
}
