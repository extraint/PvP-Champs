package com.relay.pvpchamps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import vexbot.api.VexBotApi;

/**
 * Spectators for a running tournament: any online player can join as a viewer (spectator mode above the arena, with the
 * match chat), follow a fighter with a camera, and leave to get back where they were. Tournament players who are
 * waiting for their next match can use the camera too.
 */
public final class Viewers {
	private Viewers() {}

	private record Saved(GameType mode, Vec3 pos, net.minecraft.server.level.ServerLevel level, float yaw, float pitch) {}

	private static final Map<UUID, Saved> JOINED = new HashMap<>();
	private static final Map<UUID, String> FOLLOWING = new HashMap<>();
	private static int ticks;

	public static boolean joined(ServerPlayer p) { return JOINED.containsKey(p.getUUID()); }

	/** Sends a match message to every viewer. */
	public static void message(MinecraftServer server, Component c, boolean overlay) {
		for (UUID id : JOINED.keySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) p.sendSystemMessage(c, overlay);
		}
	}

	public static String join(ServerPlayer p) {
		Bout b = MatchManager.current();
		if (!MatchManager.blocksTournament()) return "Nothing is running to watch. /pvpchamps tournament start";
		if (JOINED.containsKey(p.getUUID())) return "You are already watching. /pvpchamps cam <name>, /pvpchamps spectate leave";
		var level = b != null ? b.level() : (net.minecraft.server.level.ServerLevel) p.level();
		JOINED.put(p.getUUID(), new Saved(p.gameMode(), p.position(), (net.minecraft.server.level.ServerLevel) p.level(), p.getYRot(), p.getXRot()));
		p.setGameMode(GameType.SPECTATOR);
		Vec3 at = MatchManager.origin().add(0, 7, 0);
		p.teleportTo(level, at.x, at.y, at.z, Set.<Relative>of(), 0f, 0f, true);
		p.sendSystemMessage(Component.literal("§eYou are watching. §7/pvpchamps cam <name | next | prev | off>, /pvpchamps spectate leave"));
		return null;
	}

	public static String leave(ServerPlayer p) {
		Saved s = JOINED.remove(p.getUUID());
		FOLLOWING.remove(p.getUUID());
		if (s == null) return "You are not watching.";
		restore(p, s);
		return null;
	}

	private static void restore(ServerPlayer p, Saved s) {
		p.setCamera(p);
		p.setGameMode(s.mode());
		p.teleportTo(s.level(), s.pos().x, s.pos().y, s.pos().z, Set.<Relative>of(), s.yaw(), s.pitch(), true);
	}

	/** Called when the tournament is over: every viewer goes back where they were. */
	public static void leaveAll(MinecraftServer server) {
		for (Map.Entry<UUID, Saved> e : new ArrayList<>(JOINED.entrySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p != null) { restore(p, e.getValue()); p.sendSystemMessage(Component.literal("§7The tournament is over.")); }
		}
		JOINED.clear();
		FOLLOWING.clear();
	}

	// ----------------------------------------------------------------------- camera

	private static List<String> fighters() {
		Bout b = MatchManager.current();
		return b == null ? List.of() : b.fighterNames();
	}

	private static ServerPlayer find(MinecraftServer server, String name) {
		ServerPlayer p = server.getPlayerList().getPlayerByName(name);
		if (p == null) p = VexBotApi.resolveBot(server, name);
		return p;
	}

	/** /pvpchamps cam <name | next | prev | off>. Returns an error message or null. */
	public static String cam(ServerPlayer p, String what) {
		if (!joined(p) && p.gameMode() != GameType.SPECTATOR) return "Join first with /pvpchamps spectate, or wait until your own match is over.";
		List<String> fs = fighters();
		if (what.equalsIgnoreCase("off")) {
			FOLLOWING.remove(p.getUUID());
			p.setCamera(p);
			return null;
		}
		if (fs.isEmpty()) return "No fight is running right now.";
		String target;
		if (what.equalsIgnoreCase("next") || what.equalsIgnoreCase("prev")) {
			int i = fs.indexOf(FOLLOWING.get(p.getUUID()));
			int d = what.equalsIgnoreCase("next") ? 1 : -1;
			target = fs.get(Math.floorMod(i + d, fs.size()));
		} else {
			target = fs.stream().filter(n -> n.equalsIgnoreCase(what)).findFirst().orElse(null);
			if (target == null) return "Not fighting right now. Fighters: " + String.join(", ", fs);
		}
		FOLLOWING.put(p.getUUID(), target);
		ServerPlayer t = find(p.level().getServer(), target);
		if (t != null) { p.setCamera(t); p.sendSystemMessage(Component.literal("§7Following " + target), true); }
		return null;
	}

	public static List<String> suggestions() {
		List<String> out = new ArrayList<>(List.of("next", "prev", "off"));
		out.addAll(fighters());
		return out;
	}

	/** Keeps every follower's camera on its fighter (a respawned fighter is a new entity, and a new round resets the camera). */
	public static void tick(MinecraftServer server) {
		if (FOLLOWING.isEmpty() || ++ticks % 10 != 0) return;
		for (Map.Entry<UUID, String> e : FOLLOWING.entrySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			ServerPlayer t = find(server, e.getValue());
			if (p != null && t != null && t.isAlive() && p.getCamera() != t) p.setCamera(t);
		}
	}
}
