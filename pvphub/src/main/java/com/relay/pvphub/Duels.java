package com.relay.pvphub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;

/** The duels in progress. */
public final class Duels {
	private Duels() {}

	private static final List<Duel> ACTIVE = new ArrayList<>();
	private static final Map<UUID, Duel> BY = new HashMap<>();

	/** Starts a duel if an arena is free. */
	public static synchronized boolean start(ServerPlayer a, ServerPlayer b, Kit kit, boolean ranked, int bestOf) {
		Arena arena = Arenas.acquire();
		if (arena == null) return false;
		Duel d = new Duel(arena, a, b, kit, ranked, bestOf);
		ACTIVE.add(d);
		BY.put(a.getUUID(), d);
		BY.put(b.getUUID(), d);
		d.begin();
		return true;
	}

	static synchronized void remove(Duel d) {
		ACTIVE.remove(d);
		BY.remove(d.p[0].getUUID());
		BY.remove(d.p[1].getUUID());
	}

	public static synchronized Duel of(ServerPlayer p) { return BY.get(p.getUUID()); }

	public static synchronized List<Duel> active() { return new ArrayList<>(ACTIVE); }

	public static void tick() {
		for (Duel d : active()) d.tick();
	}

	public static void forfeit(ServerPlayer p) {
		Duel d = of(p);
		if (d != null) d.forfeit(p);
	}

	/** True if the damage should go through: duellists hurt each other only once the fight has started. */
	public static boolean allowDamage(ServerPlayer victim, DamageSource src) {
		Duel d = of(victim);
		if (d == null) return true;
		if (d.phase != Duel.Phase.FIGHT) return false;
		var attacker = src.getEntity();
		if (attacker instanceof Projectile pr && pr.getOwner() != null) attacker = pr.getOwner();
		return attacker == null || attacker == victim || (attacker instanceof ServerPlayer ap && d.indexOf(ap) >= 0);
	}

	/** A duellist would have died: end the round instead. Returns true if this was a duellist. */
	public static boolean onDeath(ServerPlayer p) {
		Duel d = of(p);
		if (d == null) return false;
		d.died(p);
		return true;
	}

	public static String status(ServerPlayer p) {
		Duel d = of(p);
		if (d == null) return "";
		int me = d.indexOf(p);
		ServerPlayer foe = d.p[1 - me];
		return "§c" + (d.ranked ? "Ranked " : "") + d.kit.label + " §7vs §f" + foe.getGameProfile().name() + " §7· " + (foe.connection == null ? 0 : foe.connection.latency()) + " ms · "
			+ d.score[me] + "-" + d.score[1 - me];
	}
}
