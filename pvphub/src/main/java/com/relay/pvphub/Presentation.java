package com.relay.pvphub;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** What the server looks like: rank tags in the tab list and chat, the tab header and footer, the sidebar. */
public final class Presentation {
	private Presentation() {}

	private static final String[] RANKS = {"Champion", "Diamond", "Platinum", "Gold", "Silver", "Bronze"};
	private static final String[] TAGS = {"§c§l[Champion] §c", "§b[Diamond] §b", "§3[Platinum] §3", "§6[Gold] §6", "§7[Silver] §7", "§8[Bronze] §8"};
	private static final Map<String, String> RANK_OF = new HashMap<>();
	private static Objective side;
	private static int ticks;
	private static final int LINES = 8;

	private static int rankIndex(double elo) {
		String n = Stats.rankName(elo);
		for (int i = 0; i < RANKS.length; i++) if (RANKS[i].equals(n)) return i;
		return RANKS.length - 1;
	}

	/** Puts a name in the team that carries its rank tag (the tab list sorts by team, best rank first). */
	public static void setRank(MinecraftServer server, String name, double elo) {
		int i = rankIndex(elo);
		if (RANKS[i].equals(RANK_OF.get(name))) return;
		Scoreboard sb = server.getScoreboard();
		String team = "pvphub_" + i + RANKS[i].toLowerCase();
		PlayerTeam t = sb.getPlayerTeam(team);
		if (t == null) {
			t = sb.addPlayerTeam(team);
			t.setPlayerPrefix(Component.literal(TAGS[i]));
		}
		sb.addPlayerToTeam(name, t);
		RANK_OF.put(name, RANKS[i]);
	}

	private static double best(ServerPlayer p) {
		double top = 1000;
		for (Kit k : Kit.values()) top = Math.max(top, Stats.elo(p.getUUID(), p.getGameProfile().name(), k));
		return top;
	}

	public static void tick(MinecraftServer server) {
		ticks++;
		if (ticks % 20 != 3) return;
		Config c = Config.get();
		var players = server.getPlayerList().getPlayers();
		int online = players.size(), duelling = 0, queued = 0, ffa = 0;
		for (ServerPlayer p : players) {
			switch (Hub.state(p)) {
				case DUEL -> duelling++;
				case QUEUE -> queued++;
				case FFA -> ffa++;
				default -> {}
			}
		}
		Component header = Component.literal("§6§l" + c.serverName + "\n§f" + c.region + " §7| §f26.2");
		Component footer = Component.literal("\n§fOnline: §e" + online + " players\n§fIn duels: §e" + duelling + " §7· §fQueued: §e" + queued + " §7· §fFFA: §e" + ffa + "\n\n§6Join the Discord!\n§f→ §e" + c.discord);
		for (ServerPlayer p : players) {
			if (Crowd.isBot(p)) continue;
			setRank(server, p.getGameProfile().name(), best(p));
			p.connection.send(new ClientboundTabListPacket(header, footer));
		}
		sidebar(server, c, online, duelling, queued, ffa);
	}

	private static void sidebar(MinecraftServer server, Config c, int online, int duelling, int queued, int ffa) {
		Scoreboard sb = server.getScoreboard();
		if (side == null) {
			side = sb.getObjective("pvphub_side");
			if (side == null) side = sb.addObjective("pvphub_side", ObjectiveCriteria.DUMMY, Component.literal("§6§l" + c.serverName), ObjectiveCriteria.RenderType.INTEGER, true, BlankFormat.INSTANCE);
			sb.setDisplayObjective(DisplaySlot.SIDEBAR, side);
		}
		String[] text = {
			"§7" + LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) + " §8" + c.region,
			"",
			"§fPlaying: §e" + online,
			"§fIn duels: §e" + duelling,
			"§fQueued: §e" + queued,
			"§fFFA: §e" + ffa,
			" ",
			"§e" + c.address};
		for (int i = 0; i < LINES; i++) {
			var acc = sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly("§" + Integer.toHexString(i) + "§r"), side);
			acc.set(LINES - i);
			acc.display(Component.literal(text[i]));
		}
	}
}
