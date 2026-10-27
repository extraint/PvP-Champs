package com.relay.pvphub;

import net.minecraft.server.level.ServerPlayer;

/** Ratings shown the PvPHQ way: a tier (LT5 up to HT1) and TR points, for players and bots alike. */
public final class Tiers {
	private Tiers() {}

	private static final String[] NAMES = {"LT5", "HT5", "LT4", "HT4", "LT3", "HT3", "LT2", "HT2", "LT1", "HT1"};
	private static final double[] FROM = {0, 900, 975, 1050, 1125, 1200, 1300, 1400, 1500, 1650};

	public static String tier(double elo) {
		String t = NAMES[0];
		for (int i = 0; i < FROM.length; i++) if (elo >= FROM[i]) t = NAMES[i];
		return t;
	}

	public static int tr(double elo) { return (int) Math.max(0, Math.round((elo - 800) * 0.6)); }

	public static double elo(ServerPlayer p, Kit kit) {
		return Crowd.isBot(p) ? Crowd.elo(p.getGameProfile().name(), kit) : Stats.elo(p.getUUID(), p.getGameProfile().name(), kit);
	}

	/** "[HT3 180TR]" with the tier's colour. */
	public static String tag(double elo) {
		String t = tier(elo);
		String c = t.startsWith("HT1") || t.startsWith("LT1") ? "§6" : t.startsWith("HT2") || t.startsWith("LT2") ? "§d" : t.endsWith("3") ? "§b" : "§7";
		return c + "[" + t + " " + tr(elo) + "TR]";
	}
}
