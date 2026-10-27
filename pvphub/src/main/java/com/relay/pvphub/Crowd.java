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

	static void add(String name) { NAMES.add(name); }
}
