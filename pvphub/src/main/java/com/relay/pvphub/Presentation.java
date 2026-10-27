package com.relay.pvphub;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** The PvPHQ look: the tab list header and footer, the sidebar, your rank tag and your ping. */
public final class Presentation {
	private Presentation() {}

	private static final Random RNG = new Random();
	private static Objective side;
	private static int ticks, online;
	private static String lobbyId = "lobby_" + Integer.toHexString(0x1000 + RNG.nextInt(0xefff));
	private static final int LINES = 10;

	public static void reset() {
		side = null; ticks = 0; online = 0;
		lobbyId = "lobby_" + Integer.toHexString(0x1000 + RNG.nextInt(0xefff));
		Ranks.reset();
	}

	public static void tick(MinecraftServer server) {
		ticks++;
		if (ticks % 20 != 3) return;
		Config c = Config.get();
		var players = server.getPlayerList().getPlayers();
		if (online == 0) online = c.simulatedOnline;
		online = Math.max(c.simulatedOnline / 2, online + RNG.nextInt(13) - 6);             // drifts a little, like a real count
		int shown = online + players.size();
		Regions.Region home = Regions.home();
		ServerPlayer you = null;
		Component header = Component.literal("\n§6§l" + c.serverName + "\n§f" + home.city() + " §7| §f" + c.version + "\n");
		Component footer = Component.literal("\n§fOnline: §6" + String.format("%,d", shown) + " players\n§fViewing: §6Everyone §7(Sneak to switch)\n\n§6Join the " + c.serverName + " Discord!\n§6→ §e" + c.discord
			+ "\n\n§6Buy Cosmetics and Ranks at §e" + c.store + "\n");
		for (ServerPlayer p : players) {
			if (Crowd.isBot(p)) continue;
			if (you == null) you = p;
			Ranks.Rank r = Ranks.byId(c.playerRank);
			Ranks.set(server, p.getGameProfile().name(), r == null ? Ranks.byId("HQ+") : r);
			if (p.connection != null) ((com.relay.pvphub.mixin.CommonListenerAccess) p.connection).pvphub$setLatency(home.ping() + RNG.nextInt(5));
			p.connection.send(new ClientboundTabListPacket(header, footer));
		}
		sidebar(server, c, home, you);
	}

	private static void sidebar(MinecraftServer server, Config c, Regions.Region home, ServerPlayer you) {
		Scoreboard sb = server.getScoreboard();
		if (side == null) {
			side = sb.getObjective("pvphub_side");
			if (side == null) side = sb.addObjective("pvphub_side", ObjectiveCriteria.DUMMY, Component.literal("§6§l" + c.serverName), ObjectiveCriteria.RenderType.INTEGER, true, BlankFormat.INSTANCE);
			sb.setDisplayObjective(DisplaySlot.SIDEBAR, side);
		}
		String name = you == null ? "player" : you.getGameProfile().name();
		String state = you == null ? "" : switch (Hub.state(you)) {
			case QUEUE -> "§eIn queue";
			case DUEL -> "§cIn a duel";
			case FFA -> "§dIn FFA";
			case EDIT -> "§aEditing a kit";
			case SPECTATE -> "§7Spectating";
			default -> "";
		};
		String build = Arenas.building();
		String[] text = {
			"§fHi, §6" + name + "§f! §c❤",
			"",
			"§6ⓘ §fUse §6⚔ §for §6/duel",
			state.isEmpty() ? (build.isEmpty() ? " " : "§8" + build) : state,
			"§6⚑ §f" + home.city(),
			"§6⊕ §7" + lobbyId,
			"§6⌚ §7" + LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")),
			"  ",
			"§6" + c.address,
			"   "};
		for (int i = 0; i < LINES; i++) {
			var acc = sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("§" + Integer.toHexString(i) + "§r"), side);
			acc.set(LINES - i);
			acc.display(Component.literal(text[i]));
		}
	}
}
