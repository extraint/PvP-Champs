package com.relay.pvphub;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.world.scores.TeamColor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The PvPHQ ranks: a bold coloured tag in front of the name in the tab list, over the head and in chat. Staff first, then
 * Money SMP, creators, the personal tags some HQ+ players have, HQ+, and everyone else. The tab list sorts by team name,
 * so each rank's team name starts with its place in that order.
 */
public final class Ranks {
	private Ranks() {}

	public record Rank(String id, String tag, TeamColor nameColor, int order) {}

	public static final Rank[] STANDARD = {
		new Rank("OWNER", "§6§l", TeamColor.GOLD, 0),
		new Rank("DEVELOPER", "§9§l", TeamColor.BLUE, 1),
		new Rank("ADMIN", "§c§l", TeamColor.RED, 2),
		new Rank("MANAGER", "§c§l", TeamColor.RED, 3),
		new Rank("SENIOR MOD", "§e§l", TeamColor.YELLOW, 4),
		new Rank("ARBITER", "§b§l", TeamColor.AQUA, 5),
		new Rank("MOD", "§9§l", TeamColor.BLUE, 6),
		new Rank("HELPER", "§d§l", TeamColor.LIGHT_PURPLE, 7),
		new Rank("MONEY SMP", "§a§l", TeamColor.GREEN, 8),
		new Rank("CRACKBEAST", "§4§l", TeamColor.DARK_RED, 9),
		new Rank("CREATOR", "§c§l", TeamColor.RED, 10),
		new Rank("HQ+", "§b§l", TeamColor.WHITE, 12),
		new Rank("DEFAULT", "", TeamColor.GRAY, 13)};

	/** The personal tags (sorted with HQ+, just above it). */
	private static final String[] CUSTOM = {"KITTY", "HOST", "HEAVEN", "LT5", "THEWORLD", "MACIEST", "BEE", "CLOWN", "HELLO", "ITAINTME", "FM", "ALLY", "MUKBANG", "GOAT", "SWEATY", "NOLIFE"};
	private static final String[] CUSTOM_COLORS = {"§e", "§6", "§d", "§a", "§b", "§c"};

	private static final Map<String, String> TEAM_OF = new HashMap<>();
	private static final Map<String, Rank> RANK_OF = new HashMap<>();

	private static TeamColor colorOf(char code) {
		return switch (code) { case '6' -> TeamColor.GOLD; case 'd' -> TeamColor.LIGHT_PURPLE; case 'a' -> TeamColor.GREEN; case 'b' -> TeamColor.AQUA; case 'c' -> TeamColor.RED; default -> TeamColor.YELLOW; };
	}

	public static Rank byId(String id) {
		for (Rank r : STANDARD) if (r.id.equalsIgnoreCase(id) || r.id.replace(" ", "_").equalsIgnoreCase(id)) return r;
		return null;
	}

	public static void reset() { TEAM_OF.clear(); RANK_OF.clear(); }

	public static Rank of(String name) { return RANK_OF.getOrDefault(name, STANDARD[STANDARD.length - 1]); }

	/** Gives a name its rank: the team carrying the tag and the name colour. */
	public static void set(MinecraftServer server, String name, Rank r) {
		place(server, name, "pvphub_" + String.format("%02d", r.order) + r.id.replace(" ", "").toLowerCase(), r.id.equals("DEFAULT") ? "" : r.tag + r.id + " ", r.nameColor);
		RANK_OF.put(name, r);
	}

	private static void place(MinecraftServer server, String name, String team, String prefix, TeamColor color) {
		if (team.equals(TEAM_OF.get(name))) return;
		Scoreboard sb = server.getScoreboard();
		PlayerTeam t = sb.getPlayerTeam(team);
		if (t == null) {
			t = sb.addPlayerTeam(team);
			t.setPlayerPrefix(Component.literal(prefix));
			t.setColor(java.util.Optional.of(color));
		}
		sb.addPlayerToTeam(name, t);
		TEAM_OF.put(name, team);
	}

	/** A rank for a bot, in roughly the proportions of a busy PvPHQ lobby. */
	public static void randomFor(MinecraftServer server, String name, Random rng) {
		int roll = rng.nextInt(1000);
		String[] staff = {"DEVELOPER", "ADMIN", "MANAGER", "SENIOR MOD", "ARBITER", "MOD", "MOD", "HELPER", "HELPER", "HELPER"};
		if (roll < 30) { set(server, name, byId(staff[rng.nextInt(staff.length)])); return; }
		if (roll < 90) { set(server, name, byId("MONEY SMP")); return; }
		if (roll < 190) { set(server, name, byId("CREATOR")); return; }
		if (roll < 192) { set(server, name, byId("CRACKBEAST")); return; }
		if (roll < 260) {
			String tag = CUSTOM[rng.nextInt(CUSTOM.length)];
			String col = CUSTOM_COLORS[rng.nextInt(CUSTOM_COLORS.length)];
			place(server, name, "pvphub_11" + tag.toLowerCase(), col + "§l" + tag + " ", colorOf(col.charAt(1)));
			RANK_OF.put(name, byId("HQ+"));
			return;
		}
		if (roll < 600) { set(server, name, byId("HQ+")); return; }
		set(server, name, byId("DEFAULT"));
	}
}
