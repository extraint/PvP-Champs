package com.relay.pvphub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import vexbot.api.VexBotApi;
import vexbot.fakeplayer.bot.BotPlayerActionPack;
import vexbot.fakeplayer.bot.connection.ServerPlayerInterface;
import vexbot.kit.KitManager;

/**
 * A lobby that is never empty (needs VexBot): about a hundred bots wander the lobby and now and then two of them go off to
 * an arena for a duel you can spectate. They cannot be hurt in the lobby, they join and leave silently, and their fights
 * are never rated.
 */
public final class Population {
	private Population() {}

	private static final String[] FIRST = {"Dark", "Pixel", "Frost", "Shadow", "Ender", "Toxic", "Swift", "Void", "Nova", "Ghost", "Iron", "Lunar", "Blaze", "Storm", "Cyber", "Rogue", "Neon", "Crimson", "Mystic", "Turbo",
		"Silent", "Rapid", "Lucky", "Salty", "Sweaty", "Clutch", "Hyper", "Vivid", "Arctic", "Solar", "Atomic", "Feral", "Omega", "Zero", "Cosmic", "Wild", "Sly", "Bold", "Grim", "Jolly"};
	private static final String[] SECOND = {"Wolf", "Slayer", "Knight", "Fox", "Hawk", "Viper", "Sniper", "Wizard", "Panda", "Tiger", "Raven", "Golem", "Bandit", "Pilot", "Ninja", "Dragon", "Cobra", "Falcon", "Reaper", "Gamer",
		"Mage", "Shark", "Bear", "Ace", "Hunter", "Phantom", "Rider", "Comet", "Titan", "Spark"};
	/** Kits the bots can fight well (the crystal and cart kits need PvP Champs' move engine). */
	private static final Kit[] KITS = {Kit.SWORD, Kit.SWORD, Kit.UHC, Kit.NETHERITE_OP, Kit.DIAMOND_SMP, Kit.NETHERITE_SMP, Kit.SPEAR_MACE};

	private static final Random RNG = new Random();
	private static boolean enabled;
	private static final List<String> TO_SPAWN = new ArrayList<>();
	private static final Map<String, Bot> BOTS = new HashMap<>();
	private static final List<Recycle> RECYCLE = new ArrayList<>();
	private static final Set<String> ALL = new LinkedHashSet<>();
	private static int ticks, nextDuel = 10;
	private static boolean started;

	private static final class Bot {
		final String name;
		boolean ready;
		int wait;                                            // ticks until it decides what to do next
		Bot(String name) { this.name = name; }
	}

	private record Recycle(String name, int at) {}

	public static void enable() { enabled = true; }

	private static void begin(MinecraftServer server) {
		started = true;
		int want = Config.get().crowdBots;
		while (ALL.size() < want) {
			String n = FIRST[RNG.nextInt(FIRST.length)] + SECOND[RNG.nextInt(SECOND.length)];
			if (RNG.nextInt(3) == 0) n += RNG.nextInt(100);
			if (n.length() > 16 || !ALL.add(n)) continue;
		}
		for (String n : ALL) { Crowd.add(n); TO_SPAWN.add(n); }
		java.util.Collections.shuffle(TO_SPAWN, RNG);
	}

	private static Vec3 spot() {
		Config c = Config.get();
		double a = RNG.nextDouble() * Math.PI * 2, r = 3 + RNG.nextDouble() * 12;
		return new Vec3(c.lobbyX + Math.cos(a) * r, c.lobbyY, c.lobbyZ + Math.sin(a) * r);
	}

	private static void spawn(MinecraftServer server, String name) {
		VexBotApi.spawnBot(server, name, spot(), RNG.nextFloat() * 360f, Hub.world(server).dimension().identifier().toString());
		BOTS.put(name, new Bot(name));
	}

	/** Gives a bot a kit the way VexBot expects (its armour and offhand managers see the change). */
	public static void give(ServerPlayer p, Kit kit) {
		KitManager.apply(p, kit.build(p.level().getServer()));
		p.setGameMode(GameType.SURVIVAL);
		p.removeAllEffects();
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.clearFire();
	}

	/** The fight starts: each bot goes for the other. */
	public static void fight(Duel d) {
		VexBotApi.hunt(d.p[0].getGameProfile().name(), d.p[1].getGameProfile().name());
		VexBotApi.hunt(d.p[1].getGameProfile().name(), d.p[0].getGameProfile().name());
	}

	/** A bot duel is over: drop both bots and bring them back fresh in a few seconds. */
	public static void recycle(ServerPlayer a, ServerPlayer b) {
		RECYCLE.add(new Recycle(a.getGameProfile().name(), ticks + 2));
		RECYCLE.add(new Recycle(b.getGameProfile().name(), ticks + 2));
	}

	public static void tick(MinecraftServer server) {
		if (!enabled || Arenas.ffa() == null) return;
		if (!started) { if (server.getTickCount() < 100) return; begin(server); }
		ticks++;
		MinecraftServer s = server;

		if (ticks % 4 == 0 && !TO_SPAWN.isEmpty()) spawn(s, TO_SPAWN.remove(TO_SPAWN.size() - 1));      // fifteen a second at most

		for (var it = RECYCLE.iterator(); it.hasNext();) {
			Recycle r = it.next();
			if (r.at > ticks) continue;
			it.remove();
			if (VexBotApi.isBot(s, r.name)) {
				VexBotApi.despawnBot(s, r.name);
				BOTS.remove(r.name);
				RECYCLE.add(new Recycle("+" + r.name, ticks + 60));          // "+" marks "bring it back"
			} else if (r.name.startsWith("+")) {
				spawn(s, r.name.substring(1));
			}
		}

		int i = 0;
		for (Bot b : BOTS.values()) {
			i++;
			ServerPlayer p = VexBotApi.resolveBot(s, b.name);
			if (p == null) continue;
			if (!b.ready) {                                                  // just arrived: settle it into the lobby
				b.ready = true;
				p.setGameMode(GameType.ADVENTURE);
				p.getInventory().clearContent();
				Hub.setState(p, Hub.State.LOBBY);
				continue;
			}
			if (Hub.state(p) != Hub.State.LOBBY) continue;
			if (p.getY() < Config.get().lobbyY - 4) { Vec3 v = spot(); Hub.teleport(p, Hub.world(s), v.x, v.y, v.z, 0f); }
			p.getFoodData().setFoodLevel(20);
			if ((ticks + i) % 10 == 0) wander(p, b);
		}

		if (ticks % 20 == 0) duels(s);
	}

	/** Walk a little, stop, look about; turn back when near the edge. */
	private static void wander(ServerPlayer p, Bot b) {
		b.wait -= 10;
		if (b.wait > 0) return;
		BotPlayerActionPack pack = ((ServerPlayerInterface) p).getActionPack();
		if (pack == null) return;
		Config c = Config.get();
		double dx = c.lobbyX - p.getX(), dz = c.lobbyZ - p.getZ();
		boolean far = Math.hypot(dx, dz) > 15;
		int roll = RNG.nextInt(10);
		if (roll < 3 && !far) {                                              // stand and look around
			pack.stopMovement();
			pack.look(RNG.nextFloat() * 360f - 180f, RNG.nextFloat() * 30f - 10f);
			b.wait = 40 + RNG.nextInt(100);
			return;
		}
		float yaw = far ? (float) Math.toDegrees(Math.atan2(-dx, dz)) + RNG.nextFloat() * 60f - 30f : RNG.nextFloat() * 360f - 180f;
		pack.look(yaw, 0f);
		pack.setForward(1f);
		pack.setSprinting(RNG.nextInt(4) == 0);
		if (RNG.nextInt(5) == 0) pack.start(BotPlayerActionPack.ActionType.JUMP, BotPlayerActionPack.Action.once());
		b.wait = 30 + RNG.nextInt(90);
	}

	/** Every so often two idle bots take an arena and fight; a fight that drags on is ended. */
	private static void duels(MinecraftServer s) {
		int running = 0;
		for (Duel d : Duels.active()) {
			if (!d.simulated) continue;
			running++;
			if (d.phase == Duel.Phase.FIGHT && d.fightTicks > 20 * 150) d.died(d.p[RNG.nextInt(2)]);          // two and a half minutes is enough
		}
		Config c = Config.get();
		if (--nextDuel > 0 || running >= c.crowdDuels || Arenas.free() <= c.crowdReserveArenas) return;
		List<ServerPlayer> idle = new ArrayList<>();
		for (Bot b : BOTS.values()) {
			if (!b.ready) continue;
			ServerPlayer p = VexBotApi.resolveBot(s, b.name);
			if (p != null && Hub.state(p) == Hub.State.LOBBY && p.isAlive()) idle.add(p);
		}
		if (idle.size() < 2) return;
		ServerPlayer a = idle.remove(RNG.nextInt(idle.size())), b = idle.remove(RNG.nextInt(idle.size()));
		BotPlayerActionPack pa = ((ServerPlayerInterface) a).getActionPack(), pb = ((ServerPlayerInterface) b).getActionPack();
		if (pa != null) pa.stopAll();
		if (pb != null) pb.stopAll();
		Kit kit = KITS[RNG.nextInt(KITS.length)];
		Duels.start(a, b, kit, false, RNG.nextInt(4) == 0 ? 3 : 1, true);
		nextDuel = 8 + RNG.nextInt(20);
	}
}
