package com.relay.pvphub;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;

/** The lobby: where players arrive, what they hold, what state each is in. */
public final class Hub {
	private Hub() {}

	public enum State { LOBBY, QUEUE, DUEL, FFA, SPECTATE }

	private static final Map<UUID, State> STATE = new HashMap<>();
	private static int ticks;

	public static State state(ServerPlayer p) { return STATE.getOrDefault(p.getUUID(), State.LOBBY); }

	public static void setState(ServerPlayer p, State s) { STATE.put(p.getUUID(), s); }

	public static void reset() { STATE.clear(); ticks = 0; }

	public static void forget(ServerPlayer p) { STATE.remove(p.getUUID()); }

	public static boolean inLobby(ServerPlayer p) { State s = state(p); return s == State.LOBBY || s == State.QUEUE; }

	public static ServerLevel world(MinecraftServer server) { return server.overworld(); }

	public static void teleport(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw) {
		p.teleportTo(level, x, y, z, Set.<Relative>of(), yaw, 0f, true);
		p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		p.fallDistance = 0;
	}

	/** Sends a player to the lobby from anywhere: out of the queue, the duel, the FFA, the spectator view. */
	public static void toLobby(ServerPlayer p) {
		Queues.leave(p, false);
		Duels.forfeit(p);
		Ffa.leave(p);
		Spectate.stop(p);
		Config c = Config.get();
		ServerLevel level = world(p.level().getServer());
		p.setCamera(p);
		p.setGameMode(GameType.ADVENTURE);
		teleport(p, level, c.lobbyX, c.lobbyY, c.lobbyZ, c.lobbyYaw);
		p.removeAllEffects();
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.clearFire();
		p.getAbilities().flying = false;
		setState(p, State.LOBBY);
		if (!Crowd.isBot(p)) HubItems.giveLobby(p, false);
	}

	/** Puts a player in the lobby state without touching the queue (used when joining one). */
	public static void toLobbyQuietly(ServerPlayer p) {
		if (state(p) == State.LOBBY || state(p) == State.QUEUE) return;
		toLobby(p);
	}

	/** Builds the lobby platform once (afterwards it is yours to change). */
	public static void buildLobby(MinecraftServer server) {
		Config c = Config.get();
		ServerLevel level = world(server);
		if (!level.getBlockState(new BlockPos((int) Math.floor(c.lobbyX), (int) Math.floor(c.lobbyY) - 1, (int) Math.floor(c.lobbyZ))).isAir()) return;     // built already (in this world)
		int cx = (int) Math.floor(c.lobbyX), cy = (int) Math.floor(c.lobbyY) - 1, cz = (int) Math.floor(c.lobbyZ);
		int r = 20;
		for (int chx = (cx - r - 2) >> 4; chx <= (cx + r + 2) >> 4; chx++) for (int chz = (cz - r - 2) >> 4; chz <= (cz + r + 2) >> 4; chz++) level.setChunkForced(chx, chz, true);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				double d = Math.hypot(x - cx, z - cz);
				if (d > r + 0.5) continue;
				Builder.set(level, p.set(x, cy, z), d > r - 1.5 ? Builder.b("polished_blackstone_bricks") : (((x + z) & 1) == 0 ? Builder.b("smooth_quartz") : Builder.b("quartz_bricks")));
				Builder.set(level, p.set(x, cy - 1, z), Builder.b("stone_bricks"));
				for (int y = cy + 1; y <= cy + 6; y++) Builder.set(level, p.set(x, y, z), Builder.b("air"));
				if (d > r - 1.5) for (int y = cy + 1; y <= cy + 25; y++) Builder.set(level, p.set(x, y, z), Builder.b(y <= cy + 2 ? "polished_blackstone_bricks" : "barrier"));
			}
		}
		for (int i = 0; i < 8; i++) {                                      // light posts around the middle
			double a = i * Math.PI / 4;
			int lx = cx + (int) Math.round(Math.cos(a) * 9), lz = cz + (int) Math.round(Math.sin(a) * 9);
			for (int y = cy + 1; y <= cy + 3; y++) Builder.set(level, p.set(lx, y, lz), Builder.b("polished_blackstone_bricks"));
			Builder.set(level, p.set(lx, cy + 4, lz), Builder.b("sea_lantern"));
		}
		System.out.println("[pvphub] built the lobby at " + cx + " " + (cy + 1) + " " + cz);
	}

	/** Once a tick: no hunger, no loose items, nobody falling out of the world; once a second: the hotbar and the status line. */
	public static void tick(MinecraftServer server) {
		ticks++;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Crowd.isBot(p)) continue;          // the crowd is looked after by Population
			State s = state(p);
			if (s == State.LOBBY || s == State.QUEUE) {
				p.getFoodData().setFoodLevel(20);
				if (p.getY() < Config.get().lobbyY - 20) toLobby(p);
			}
			if (ticks % 20 == 7) second(p, s);
		}
		if (ticks % 20 == 0) {
			Config c = Config.get();
			for (ItemEntity e : world(server).getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(c.lobbyX - 40, c.lobbyY - 10, c.lobbyZ - 40, c.lobbyX + 40, c.lobbyY + 30, c.lobbyZ + 40))) e.discard();
		}
	}

	private static void second(ServerPlayer p, State s) {
		boolean queued = s == State.QUEUE;
		if ((s == State.LOBBY || queued) && !HubItems.intact(p, queued)) HubItems.giveLobby(p, queued);
		String line = switch (s) {
			case LOBBY -> "§b§lPvP Hub §7· " + p.getGameProfile().name() + " · /queue, /duel, /ffa";
			case QUEUE -> Queues.status(p);
			case DUEL -> Duels.status(p);
			case FFA -> Ffa.status(p);
			case SPECTATE -> "§7Spectating · §f/hub §7to leave";
		};
		p.sendSystemMessage(Component.literal(line), true);
	}
}
