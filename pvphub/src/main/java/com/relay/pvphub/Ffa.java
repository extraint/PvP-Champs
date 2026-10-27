package com.relay.pvphub;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** The free-for-all: one big arena, instant respawn with the kit, kill streaks. */
public final class Ffa {
	private Ffa() {}

	private static final Random RNG = new Random();
	private static final Map<UUID, Long> PROTECTED_UNTIL = new HashMap<>();
	private static long ticks;
	private static boolean cleaned;

	private static Kit kit() {
		Kit k = Kit.byId(Config.get().ffaKit);
		return k == null ? Kit.SWORD : k;
	}

	public static void reset() { PROTECTED_UNTIL.clear(); cleaned = false; ticks = 0; }

	public static void join(ServerPlayer p) {
		Hub.toLobbyQuietly(p);
		Queues.leave(p, false);
		Hub.setState(p, Hub.State.FFA);
		spawn(p);
		p.sendSystemMessage(Component.literal("§dFFA §7· kit §f" + kit().label + " §7· kill streaks are announced · /hub to leave"));
	}

	public static void leave(ServerPlayer p) {
		if (Hub.state(p) != Hub.State.FFA) return;
		PROTECTED_UNTIL.remove(p.getUUID());
		Hub.setState(p, Hub.State.LOBBY);
	}

	public static void spawn(ServerPlayer p) {
		ServerLevel level = Hub.world(p.level().getServer());
		Arena a = Arenas.ffa();
		if (!cleaned) { a.reset(level); cleaned = true; }
		Vec3 s = a.randomSpawn(RNG);
		p.setGameMode(GameType.SURVIVAL);
		p.removeAllEffects();
		p.clearFire();
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		Hub.teleport(p, level, s.x, s.y, s.z, RNG.nextFloat() * 360f);
		kit().give(p);
		PROTECTED_UNTIL.put(p.getUUID(), ticks + 60);
	}

	public static boolean protectedNow(ServerPlayer p) { return PROTECTED_UNTIL.getOrDefault(p.getUUID(), 0L) > ticks; }

	public static void tick() {
		ticks++;
	}

	/** Damage in the FFA: not during the spawn protection, and not from the void roof to the floor. */
	public static boolean allowDamage(ServerPlayer victim, DamageSource src) {
		if (Hub.state(victim) != Hub.State.FFA) return true;
		if (protectedNow(victim)) return false;
		var attacker = src.getEntity();
		if (attacker instanceof Projectile pr && pr.getOwner() != null) attacker = pr.getOwner();
		return !(attacker instanceof ServerPlayer ap) || !protectedNow(ap) || ap == victim;
	}

	/** A player would have died in the FFA: credit the killer, announce, respawn at once. Returns true if handled. */
	public static boolean onDeath(ServerPlayer p, DamageSource src) {
		if (Hub.state(p) != Hub.State.FFA) return false;
		var e = src.getEntity();
		if (e instanceof Projectile pr && pr.getOwner() != null) e = pr.getOwner();
		ServerPlayer killer = e instanceof ServerPlayer k && k != p && Hub.state(k) == Hub.State.FFA ? k : null;
		String pn = p.getGameProfile().name();
		int lost = Stats.ffaStreak(p.getUUID(), pn);
		if (killer != null) {
			String kn = killer.getGameProfile().name();
			Stats.ffaKill(killer.getUUID(), kn, p.getUUID(), pn);
			int streak = Stats.ffaStreak(killer.getUUID(), kn);
			killer.sendSystemMessage(Component.literal("§a+1 kill §7· streak §f" + streak), true);
			killer.setHealth(Math.min(killer.getMaxHealth(), killer.getHealth() + 6));
			String line = "§d" + kn + " §7killed §d" + pn + (lost >= 3 ? " §7(ended a §c" + lost + " §7streak)" : "");
			if (streak >= 3 && streak % 3 == 0) line += " §6§l" + streak + " KILL STREAK";
			broadcast(p, line);
		} else {
			Stats.ffaDeath(p.getUUID(), pn);
			broadcast(p, "§d" + pn + " §7died");
		}
		spawn(p);
		return true;
	}

	private static void broadcast(ServerPlayer from, String text) {
		for (ServerPlayer o : from.level().getServer().getPlayerList().getPlayers()) if (Hub.state(o) == Hub.State.FFA) o.sendSystemMessage(Component.literal(text));
	}

	public static int count(net.minecraft.server.MinecraftServer server) {
		int n = 0;
		for (ServerPlayer o : server.getPlayerList().getPlayers()) if (Hub.state(o) == Hub.State.FFA) n++;
		return n;
	}

	public static String status(ServerPlayer p) {
		String n = p.getGameProfile().name();
		return "§dFFA §7· streak §f" + Stats.ffaStreak(p.getUUID(), n) + " §7· " + count(p.level().getServer()) + " fighting";
	}
}
