package com.relay.pvphub;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /duel requests between two players: a kit, a length, and sixty seconds to say yes. */
public final class Requests {
	private Requests() {}

	private record Req(ServerPlayer from, ServerPlayer to, Kit kit, int bestOf, long expires) {}

	private static final List<Req> OPEN = new ArrayList<>();
	private static long ticks;

	public static void tick() {
		ticks++;
		if (ticks % 20 == 0) OPEN.removeIf(r -> r.expires < ticks);
	}

	public static void send(ServerPlayer from, ServerPlayer to, Kit kit, int bestOf) {
		if (from == to) { from.sendSystemMessage(Component.literal("§cYou cannot duel yourself.")); return; }
		if (!Hub.inLobby(from) || !Hub.inLobby(to)) { from.sendSystemMessage(Component.literal("§cBoth of you need to be in the lobby.")); return; }
		OPEN.removeIf(r -> r.from == from && r.to == to);
		OPEN.add(new Req(from, to, kit, bestOf, ticks + Config.get().requestSeconds * 20L));
		from.sendSystemMessage(Component.literal("§7Duel request sent to §f" + to.getGameProfile().name() + " §7(" + kit.label + ", best of " + bestOf + ")."));
		to.sendSystemMessage(Component.literal("§e" + from.getGameProfile().name() + " §7challenges you to §f" + kit.label + " §7(best of " + bestOf + "). §a/duel accept " + from.getGameProfile().name() + " §7or §c/duel deny " + from.getGameProfile().name()));
	}

	public static void accept(ServerPlayer to, ServerPlayer from) {
		Req r = find(from, to);
		if (r == null) { to.sendSystemMessage(Component.literal("§cNo request from " + from.getGameProfile().name() + ".")); return; }
		OPEN.remove(r);
		if (!Hub.inLobby(from) || !Hub.inLobby(to)) { to.sendSystemMessage(Component.literal("§cBoth of you need to be in the lobby.")); return; }
		Queues.leave(from, false);
		Queues.leave(to, false);
		if (!Duels.start(from, to, r.kit, false, r.bestOf)) {
			to.sendSystemMessage(Component.literal("§cAll arenas are busy, try again in a moment."));
			from.sendSystemMessage(Component.literal("§cAll arenas are busy, try again in a moment."));
		}
	}

	public static void deny(ServerPlayer to, ServerPlayer from) {
		Req r = find(from, to);
		if (r == null) return;
		OPEN.remove(r);
		from.sendSystemMessage(Component.literal("§c" + to.getGameProfile().name() + " declined your duel."));
		to.sendSystemMessage(Component.literal("§7Declined."));
	}

	private static Req find(ServerPlayer from, ServerPlayer to) {
		for (Req r : OPEN) if (r.from == from && r.to == to) return r;
		return null;
	}

	public static void clear(ServerPlayer p) { OPEN.removeIf(r -> r.from == p || r.to == p); }
}
