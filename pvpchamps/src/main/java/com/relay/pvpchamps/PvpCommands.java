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
import net.minecraft.world.phys.Vec3;

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
			.then(Commands.literal("region")
				.executes(c -> regionCmd(c.getSource(), null))
				.then(Commands.argument("region", StringArgumentType.greedyString())
					.suggests((c, bld) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Ping.Region.values()).map(r -> r.name()), bld))
					.executes(c -> regionCmd(c.getSource(), StringArgumentType.getString(c, "region")))))
			.then(Commands.literal("regions").executes(c -> regionsCmd(c.getSource())))
			.then(Commands.literal("servers").executes(c -> serversCmd(c.getSource())))
			.then(Commands.literal("ping")
				.then(Commands.literal("on").executes(c -> { Ping.setEnabled(true); say(c.getSource(), "Simulated ping is on."); return 1; }))
				.then(Commands.literal("off").executes(c -> { Ping.setEnabled(false); say(c.getSource(), "Simulated ping is off."); return 1; }))
				.then(Commands.literal("spikes")
					.then(Commands.literal("on").executes(c -> { Ping.setSpikes(true); say(c.getSource(), "Lag spikes are on."); return 1; }))
					.then(Commands.literal("off").executes(c -> { Ping.setSpikes(false); say(c.getSource(), "Lag spikes are off."); return 1; })))
				.then(Commands.literal("status").executes(c -> { for (String l : Ping.status(c.getSource().getServer())) say(c.getSource(), l); return 1; })))
			.then(Commands.literal("crystalgate")
				.then(Commands.literal("strict").executes(c -> { com.relay.pvpchamps.moves.CrystalGate.strict = true; say(c.getSource(), "Crystal gate: opponent above only."); return 1; }))
				.then(Commands.literal("loose").executes(c -> { com.relay.pvpchamps.moves.CrystalGate.strict = false; say(c.getSource(), "Crystal gate: above, or healthy and 4+ blocks away."); return 1; })))
			.then(Commands.literal("overtime")
				.then(Commands.argument("kit", StringArgumentType.word())
					.suggests((c, bld) -> SharedSuggestionProvider.suggest(Kits.ids(), bld))
					.then(Commands.argument("seconds", IntegerArgumentType.integer(30, 3600)).executes(c -> {
						Kits k = Kits.byId(StringArgumentType.getString(c, "kit"));
						if (k == null) { say(c.getSource(), "Unknown kit."); return 0; }
						Overtime.setLimit(k, IntegerArgumentType.getInteger(c, "seconds"));
						say(c.getSource(), k.label + " rounds go to overtime after " + IntegerArgumentType.getInteger(c, "seconds") + " s.");
						return 1;
					}))))
			.then(Commands.literal("rank")
				.executes(c -> rankCmd(c.getSource(), null))
				.then(Commands.argument("player", StringArgumentType.word())
					.suggests((c, bld) -> SharedSuggestionProvider.suggest(onlineNames(c.getSource()), bld))
					.executes(c -> rankCmd(c.getSource(), StringArgumentType.getString(c, "player")))))
			.then(Commands.literal("tiertest")
				.then(Commands.argument("kit", StringArgumentType.word())
					.suggests((c, bld) -> SharedSuggestionProvider.suggest(Kits.ids(), bld))
					.executes(c -> tierTest(c))))
			.then(Commands.literal("commentary")
				.then(Commands.literal("on").executes(c -> { Commentary.setEnabled(true); say(c.getSource(), "Commentary is on."); return 1; }))
				.then(Commands.literal("off").executes(c -> { Commentary.setEnabled(false); say(c.getSource(), "Commentary is off."); return 1; }))
				.then(Commands.literal("full").executes(c -> setCommentary(c.getSource(), Commentary.Mode.FULL, "Every call goes to chat.")))
				.then(Commands.literal("lite").executes(c -> setCommentary(c.getSource(), Commentary.Mode.LITE, "Only kills and big moments go to chat; the rest is on the action bar.")))
				.then(Commands.literal("bar").executes(c -> setCommentary(c.getSource(), Commentary.Mode.BAR, "All commentary is on the action bar, none in chat.")))
				.then(Commands.literal("mute").executes(c -> {
					boolean muted = Commentary.toggleMute(c.getSource().getPlayerOrException());
					say(c.getSource(), muted ? "Commentary muted for you." : "Commentary back on for you.");
					return 1;
				})))
			.then(Commands.literal("highlights")
				.executes(c -> highlightsCmd(c.getSource(), 8))
				.then(Commands.argument("n", IntegerArgumentType.integer(1, 30)).executes(c -> highlightsCmd(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
			.then(Commands.literal("predict")
				.then(Commands.argument("who", StringArgumentType.greedyString())
					.suggests((c, bld) -> SharedSuggestionProvider.suggest(MatchManager.current() == null ? java.util.List.<String>of() : MatchManager.current().fighterNames(), bld))
					.executes(c -> {
						String err = Predictions.predict(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "who"));
						say(c.getSource(), err == null ? "Prediction locked in." : err);
						return err == null ? 1 : 0;
					})))
			.then(Commands.literal("predictions").executes(c -> {
				for (String l : Predictions.top(10)) say(c.getSource(), l);
				return 1;
			}))
			.then(Commands.literal("spectate")
				.executes(c -> viewerCmd(c.getSource(), false))
				.then(Commands.literal("leave").executes(c -> viewerCmd(c.getSource(), true))))
			.then(Commands.literal("cam")
				.then(Commands.argument("who", StringArgumentType.greedyString())
					.suggests((c, bld) -> SharedSuggestionProvider.suggest(Viewers.suggestions(), bld))
					.executes(c -> {
						ServerPlayer pl = c.getSource().getPlayerOrException();
						String err = Viewers.cam(pl, StringArgumentType.getString(c, "who"));
						if (err != null) say(c.getSource(), err);
						return err == null ? 1 : 0;
					})))
			.then(Commands.literal("draft")
				.executes(c -> draftCmd(c.getSource(), null))
				.then(Commands.argument("kit", StringArgumentType.greedyString())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(Draft.POOL.stream().map(k -> k.id), b))
					.executes(c -> draftCmd(c.getSource(), StringArgumentType.getString(c, "kit")))))
			.then(Commands.literal("tournament")
				.then(Commands.literal("start").executes(c -> tournamentBracket(c.getSource(), false, ""))
					.then(Commands.argument("players", StringArgumentType.greedyString())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(onlineNames(c.getSource()), b))
						.executes(c -> tournamentBracket(c.getSource(), false, StringArgumentType.getString(c, "players")))))
				.then(Commands.literal("2v2")
					.executes(c -> tournamentBracket(c.getSource(), true, ""))
					.then(Commands.argument("players", StringArgumentType.greedyString())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(onlineNames(c.getSource()), b))
						.executes(c -> tournamentBracket(c.getSource(), true, StringArgumentType.getString(c, "players")))))
				.then(Commands.literal("watch").executes(c -> tournamentWatch(c.getSource())))
				.then(Commands.literal("skip").executes(c -> tournamentSkip(c.getSource())))
				.then(Commands.literal("status").executes(c -> tournamentStatus(c.getSource()))))
			.then(Commands.literal("arena")
				.then(Commands.literal("build").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					say(c.getSource(), "Building the PvP Champs arena here. This takes a few seconds...");
					Arena.build((net.minecraft.server.level.ServerLevel) p.level(), p.blockPosition());
					Vec3 at = Arena.spot();   // the arena may have been raised to fit its stone foundation: stand on the new floor
					p.teleportTo(at.x, at.y + 0.5, at.z);
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
			.then(Commands.literal("stop").executes(c -> { MatchManager.stop(c.getSource().getServer()); say(c.getSource(), "Stopped."); return 1; }))
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

	private static java.util.List<String> onlineNames(CommandSourceStack src) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) out.add(p.getGameProfile().name());
		return out;
	}

	/**
	 * /pvpchamps tournament start [friends...] | 2v2 [partner [other players...]]: the double-elimination bracket. Every
	 * real player named plays their own series and spectates the rest. In 2v2 the first player named is your partner and
	 * the rest are paired up in order (an odd one out gets a roster partner); with nobody named you get a random roster partner.
	 */
	private static int tournamentBracket(CommandSourceStack src, boolean duo, String named) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		if (MatchManager.blocksTournament()) { say(src, "A match or tournament is already running (/pvpchamps stop)."); return 0; }
		java.util.List<ServerPlayer> all = new java.util.ArrayList<>();
		all.add(p);
		for (String n : named.trim().isEmpty() ? new String[0] : named.trim().split("\\s+")) {
			ServerPlayer o = src.getServer().getPlayerList().getPlayerByName(n);
			if (o == null) { say(src, n + " is not online."); return 0; }
			if (all.contains(o)) { say(src, n + " is listed twice (or is you)."); return 0; }
			all.add(o);
		}
		if (all.size() > (duo ? 12 : 8)) { say(src, "Too many players for this bracket."); return 0; }
		MatchManager.setOrigin(Arena.exists() ? Arena.spot() : p.position());
		java.util.List<String> names = new java.util.ArrayList<>();
		for (ServerPlayer o : all) names.add(o.getGameProfile().name());
		java.util.List<String> field;
		if (duo) {
			java.util.List<java.util.List<String>> teams = new java.util.ArrayList<>();
			for (int i = 0; i < names.size(); i += 2) teams.add(names.subList(i, Math.min(i + 2, names.size())));
			field = Bracket.duoField(teams, RNG);
		} else field = Bracket.soloField(names, RNG);
		for (ServerPlayer o : all) if (o != p) o.sendSystemMessage(Component.literal("§e" + p.getGameProfile().name() + " added you to a PvP Champs " + (duo ? "2v2 " : "") + "tournament."));
		Bracket b = new Bracket(src.getServer(), all, field, duo, true);
		MatchManager.setBracket(b);
		b.start();
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

	/** /pvpchamps region [region]: where you are (default NA_WEST, California); it decides your ping in tournaments. */
	private static int regionCmd(CommandSourceStack src, String region) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		String me = p.getGameProfile().name();
		if (region == null) { say(src, "You are in " + Ping.regionOf(me, true).label + ". /pvpchamps region <region> (tab-complete), /pvpchamps servers"); return 1; }
		Ping.Region r = Ping.Region.parse(region);
		if (r == null) { say(src, "Unknown region. /pvpchamps servers lists them."); return 0; }
		Ping.setHumanRegion(me, r);
		say(src, "You are now in " + r.label + ".");
		return 1;
	}

	/** /pvpchamps servers: every server location with your ping to it from your region. */
	private static int serversCmd(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Ping.Region mine = Ping.regionOf(src.getPlayerOrException().getGameProfile().name(), true);
		say(src, "Server locations (ping from " + mine.label + "):");
		for (Ping.Region r : Ping.Region.values()) say(src, "  " + r.name() + " - " + r.label + ": " + Ping.rtt(mine, r) + " ms");
		return 1;
	}

	private static int regionsCmd(CommandSourceStack src) {
		java.util.Map<Ping.Region, java.util.List<String>> by = new java.util.EnumMap<>(Ping.Region.class);
		for (Roster.Opponent o : Roster.all()) by.computeIfAbsent(Ping.regionOf(o.name(), false), k -> new java.util.ArrayList<>()).add(o.name());
		for (var e : by.entrySet()) say(src, e.getKey().label + ": " + String.join(", ", e.getValue()));
		say(src, "Best guesses, not verified. Edit config/pvpchamps/regions.json to correct anyone.");
		return 1;
	}

	/** /pvpchamps spectate [leave]: watch the running tournament as a spectator, with a camera you can put on any fighter. */
	private static int viewerCmd(CommandSourceStack src, boolean leave) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = src.getPlayerOrException();
		String err = leave ? Viewers.leave(p) : Viewers.join(p);
		if (err != null) say(src, err);
		return err == null ? 1 : 0;
	}

	private static int setCommentary(CommandSourceStack src, Commentary.Mode mode, String text) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Commentary.setMode(src.getPlayerOrException(), mode);
		say(src, text);
		return 1;
	}

	private static int rankCmd(CommandSourceStack src, String who) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String name = who != null ? who : src.getPlayerOrException().getGameProfile().name();
		say(src, "Tiers for " + name + ":");
		for (String l : Rankings.describe(name)) say(src, "  " + l);
		return 1;
	}

	/** /pvpchamps tiertest <kit>: a best-of-3 against a roster bot of the tier you are testing for; win it and you hold that tier. */
	private static int tierTest(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack src = c.getSource();
		ServerPlayer p = src.getPlayerOrException();
		if (MatchManager.blocksTournament()) { say(src, "A match or tournament is already running (/pvpchamps stop)."); return 0; }
		Kits kit = Kits.byId(StringArgumentType.getString(c, "kit"));
		if (kit == null || !Draft.POOL.contains(kit)) { say(src, "Pick one of: " + String.join(", ", Draft.POOL.stream().map(k -> k.id).toList())); return 0; }
		String me = p.getGameProfile().name();
		String tier = Rankings.nextTier(me, kit);
		Roster.Opponent exam = Rankings.examiner(kit, tier);
		if (exam == null) { say(src, "Nobody in the roster plays that kit."); return 0; }
		say(src, "Tier test for " + tier + " in " + kit.label + ": best of 3 against " + exam.name() + " (" + Tiers.tier(exam.name(), kit) + ").");
		MatchManager.begin(new Match(src.getServer(), p, false, me, exam.name(), kit, 3, "Tier test " + tier, EVENT, won -> {
			if (won) {
				Rankings.promote(me, kit, tier);
				p.sendSystemMessage(Component.literal("§a=== Tier test passed: you are " + tier + " in " + kit.label + " ==="));
			} else p.sendSystemMessage(Component.literal("§7Tier test failed. Try again with /pvpchamps tiertest " + kit.id));
		}));
		return 1;
	}

	private static int highlightsCmd(CommandSourceStack src, int n) {
		var list = Highlights.recent(n);
		if (list.isEmpty()) { say(src, "No highlights yet."); return 0; }
		for (var h : list) say(src, "[" + h.type + "] " + h.text + (h.match == null || h.match.isEmpty() ? "" : "  (" + h.match + ")"));
		return 1;
	}

	private static int draftCmd(CommandSourceStack src, String kit) {
		String err = Draft.choose(src.getTextName(), kit);
		if (err != null) say(src, err);
		return err == null ? 1 : 0;
	}

	private static int tournamentSkip(CommandSourceStack src) {
		if (MatchManager.bracket() != null) { MatchManager.bracket().skip(); return 1; }
		say(src, "Nothing running. /pvpchamps tournament start");
		return 0;
	}

	private static int tournamentStatus(CommandSourceStack src) {
		if (MatchManager.bracket() != null) { say(src, MatchManager.bracket().status()); return 1; }
		say(src, "No tournament running. /pvpchamps tournament start | 2v2");
		return 0;
	}
}
