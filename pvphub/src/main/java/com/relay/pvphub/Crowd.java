package com.relay.pvphub;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/** Which players are crowd bots. Plain state, so the rest of the mod can ask without needing VexBot installed. */
public final class Crowd {
	private Crowd() {}

	private static final Set<String> NAMES = ConcurrentHashMap.newKeySet();

	public static boolean isName(String name) { return NAMES.contains(name); }

	public static boolean isBot(ServerPlayer p) { return NAMES.contains(p.getGameProfile().name()); }

	/** True once the crowd has been set up (so callers can ask without loading the VexBot classes). */
	public static volatile boolean active;

	static void add(String name) { NAMES.add(name); }

	/** Each bot's skill, as a rating (one per bot, a little different per kit so the boards look real). */
	private static final java.util.Map<String, Double> ELO = new ConcurrentHashMap<>();

	static void setElo(String name, double elo) { ELO.put(name, elo); }

	public static double elo(String name, Kit kit) {
		double base = ELO.getOrDefault(name, 1150.0);
		return base + ((name.hashCode() * 31 + kit.ordinal() * 977) % 120) - 60;
	}

	public static java.util.Set<String> names() { return NAMES; }
}
