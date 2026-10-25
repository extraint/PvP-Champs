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
			.then(Commands.literal("layout")
				.then(Commands.literal("save").executes(c -> layoutFinish(c, true))
					.then(Commands.argument("kit", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Kits.ids(), b))
						.executes(c -> layoutSaveDirect(c))))
				.then(Commands.literal("cancel").executes(c -> layoutFinish(c, false)))
				.then(Commands.literal("reset")
					.then(Commands.argument("kit", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Kits.ids(), b))
						.executes(c -> layoutReset(c))))
				.then(Commands.argument("kit", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(Kits.ids(), b))
					.executes(c -> layoutEdit(c))))
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
			.then(Commands.literal("bots")
				.then(Commands.literal("clear").executes(c -> {
					java.util.List<String> names = new java.util.ArrayList<>(vexbot.api.VexBotApi.listBots());
					for (String n : names) vexbot.api.VexBotApi.despawnBot(c.getSource().getServer(), n);
					say(c.getSource(), "Removed " + names.size() + " bot(s).");
					return 1;
				})))
			.then(Commands.literal("tournament")
				.then(Commands.literal("start").executes(c -> tournamentStart(c.getSource(), Tournament.Mode.SOLO, null)))
				.then(Commands.literal("2v2")
					.executes(c -> tournamentStart(c.getSource(), Tournament.Mode.DUO, null)))
				.then(Commands.literal("watch").executes(c -> tournamentWatch(c.getSource())))
				.then(Commands.literal("skip").executes(c -> tournamentSkip(c.getSource())))
				.then(Commands.literal("status").executes(c -> tournamentStatus(c.getSource()))))
			.then(Commands.literal("arena")
				.then(Commands.literal("build").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					say(c.getSource(), "Building the PvP Champs arena here. This takes a few seconds...");
					Arena.build((net.minecraft.server.level.ServerLevel) p.level(), p.blockPosition());
					p.teleportTo(p.getX(), p.getY() + 0.5, p.getZ());
					say(c.getSource(), "Arena built. Every match is staged inside it, and the 500 x 500 area around it resets after each round.");
					return 1;
				}))
				.then(Commands.literal("tp").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					var spot = Arena.spot();
					if (spot == null) { say(c.getSource(), "No arena yet: /pvpchamps arena build"); return 0; }
					p.teleportTo(spot.x, spot.y + 1, spot.z);
					return 1;
				}))
				.then(Commands.literal("clear").executes(c -> { Arena.forget(); say(c.getSource(), "Arena forgotten (the blocks stay). Matches go back to staging where you start them."); return 1; })))
			.then(Commands.literal("tiers").executes(c -> {
				StringBuilder sb = new StringBuilder("Tiers by kit (HT1 best, LT5 lowest):");
				for (Roster.Opponent o : Roster.all()) {
					sb.append("\n").append(o.name()).append(": ");
					for (Kits k : Kits.values()) sb.append(k.label).append(" ").append(Tiers.tier(o.name(), k)).append("  ");
				}
				say(c.getSource(), sb.toString());
				return 1;
			}))
			.then(Commands.literal("speedup").executes(c -> setRate(c.getSource(), 100)))
			.then(Commands.literal("slowdown").executes(c -> setRate(c.getSource(), 20)))
			.then(Commands.literal("stop").executes(c -> { MatchManager.stop(); say(c.getSource(), "Stopped."); return 1; }))
			.then(Commands.literal("moves")
				.then(Commands.literal("on").executes(c -> { MoveEngine.setEnabled(true); say(c.getSource(), "Extra moves on."); return 1; }))
				.then(Commands.literal("off").executes(c -> { MoveEngine.setEnabled(false); say(c.getSource(), "Extra moves off (plain VexBot)."); return 1; }))
				.then(Commands.literal("stats").executes(c -> { say(c.getSource(), "Moves used so far: " + MoveEngine.usage() + "  diagnostics: " + MoveEngine.DIAG); return 1; }))));
	}

	/** /pvpchamps speedup (100 ticks per second) and slowdown (back to 20), through the vanilla tick command. */
	private static int setRate(CommandSourceStack src, int tps) {
		src.getServer().getCommands().performPrefixedCommand(src.getServer().createCommandSourceStack().withSuppressedOutput(), "tick rate " + tps);
		say(src, "Game speed set to " + tps + " ticks per second" + (tps > 20 ? " (/pvpchamps slowdown to go back)." : "."));
		return 1;
	}

	private static void say(CommandSourceStack src, String text) { src.sendSuccess(() -> Component.literal(text), false); }

	private static int roster(CommandSourceStack src) {
		for (Roster.Opponent o : Roster.all()) {
			say(src, o.name() + " - " + o.title() + " [" + o.difficulty() + "/" + o.style() + ", rating " + o.rating() + "] kits: " + o.kits() + " moves: " + o.moves());
		}
		return Roster.all().size();
	}

	/** /pvpchamps layout <kit>: hands you the kit (in your saved layout) so you can rearrange it, then save. */
	private static int layoutEdit(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack src = c.getSource();
		ServerPlayer p = src.getPlayerOrException();
		Kits kit = Kits.byId(StringArgumentType.getString(c, "kit"));
		if (kit == null) { say(src, "Unknown kit. /pvpchamps kits"); return 0; }
		if (MatchManager.busy()) { say(src, "A match is running (/pvpchamps stop)."); return 0; }
		if (Layouts.editing(p)) { say(src, "You are already arranging a kit: /pvpchamps layout save or cancel."); return 0; }
		Layouts.edit(p, kit);
		say(src, "Here is the " + kit.label + " kit. Move things around, then /pvpchamps layout save (or cancel). Your own inventory comes back either way.");
		return 1;
	}

	/** /pvpchamps layout save | cancel: finish arranging; save keeps your layout, both give your inventory back. */
	private static int layoutFinish(CommandContext<CommandSourceStack> c, boolean save) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack src = c.getSource();
		ServerPlayer p = src.getPlayerOrException();
		try {
			Kits kit = Layouts.finish(p, save);
			if (kit == null) { say(src, "You are not arranging a kit. Start with /pvpchamps layout <kit>."); return 0; }
			say(src, save ? "Saved your " + kit.label + " layout. Your inventory is back." : "Cancelled. Your inventory is back.");
			return 1;
		} catch (java.io.IOException e) {
			say(src, "Could not save the layout: " + e.getMessage());
			return 0;
		}
	}

	/** /pvpchamps layout save <kit>: saves whatever is in your slots right now as that kit's layout. */
	private static int layoutSaveDirect(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack src = c.getSource();
		ServerPlayer p = src.getPlayerOrException();
		Kits kit = Kits.byId(StringArgumentType.getString(c, "kit"));
		if (kit == null) { say(src, "Unknown kit. /pvpchamps kits"); return 0; }
		try {
			say(src, "Saved your " + kit.label + " layout (" + Layouts.save(p, kit) + " slots).");
			return 1;
		} catch (java.io.IOException e) {
			say(src, "Could not save the layout: " + e.getMessage());
			return 0;
		}
	}

	private static int layoutReset(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack src = c.getSource();
		ServerPlayer p = src.getPlayerOrException();
		Kits kit = Kits.byId(StringArgumentType.getString(c, "kit"));
		if (kit == null) { say(src, "Unknown kit. /pvpchamps kits"); return 0; }
		try {
			say(src, Layouts.reset(p, kit) ? "Reset your " + kit.label + " layout." : "You had no saved " + kit.label + " layout.");
			return 1;
		} catch (java.io.IOException e) {
			say(src, "Could not reset the layout: " + e.getMessage());
			return 0;
		}
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
		if (!Spec.canDrive(p)) say(src, "(You don't have the Relay Spectator client mod: you'll see the fight but not the camera or HUD.)");
		MatchManager.begin(new Match(src.getServer(), p, true, a.name(), b.name(), kit, 3, "Main Event", EVENT, null));
		return 1;
	}

	private static int tournamentStart(CommandSourceStack src, Tournament.Mode mode, String partner) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		if (MatchManager.blocksTournament()) { say(src, "A match or bracket is already running (/pvpchamps stop)."); return 0; }
		for (Tournament t : MatchManager.tournaments()) if (!t.isOver()) { say(src, "You can only be in one tournament at a time (/pvpchamps stop)."); return 0; }
		if (partner != null && Roster.byName(partner) == null) { say(src, "Unknown partner. /pvpchamps roster"); return 0; }
		if (MatchManager.tournaments().isEmpty()) MatchManager.setOrigin(Arena.exists() ? Arena.spot() : p.position());
		MatchManager.addTournament(new Tournament(src.getServer(), p, mode, partner));
		return 1;
	}

	/** /pvpchamps tournament watch: a 16-player double-elimination bracket of bot matches that you just watch. */
	private static int tournamentWatch(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		if (MatchManager.busy() || MatchManager.bracket() != null) { say(src, "Something is already running (/pvpchamps stop)."); return 0; }
		MatchManager.setOrigin(Arena.exists() ? Arena.spot() : p.position());
		Bracket b = new Bracket(src.getServer(), p);
		MatchManager.setBracket(b);
		b.start();
		return 1;
	}

	private static int tournamentSkip(CommandSourceStack src) {
		if (MatchManager.bracket() != null) { MatchManager.bracket().skip(); return 1; }
		if (MatchManager.tournaments().isEmpty()) { say(src, "Nothing running. /pvpchamps tournament start"); return 0; }
		for (Tournament t : new java.util.ArrayList<>(MatchManager.tournaments())) t.skip();
		return 1;
	}

	private static int tournamentStatus(CommandSourceStack src) {
		if (MatchManager.bracket() != null) { say(src, MatchManager.bracket().status()); return 1; }
		if (MatchManager.tournaments().isEmpty()) { say(src, "No tournament running. /pvpchamps tournament start | 2v2"); return 0; }
		for (Tournament t : MatchManager.tournaments()) say(src, t.status());
		return 1;
	}
}
