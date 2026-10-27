package com.relay.pvpchamps;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Keeps a round from dragging on for ever. The long fights are the entertaining ones (Crystal and the SMP kits can run
 * 7 to 10 minutes), so each kit gets a generous limit; past it both fighters start to wither, a little faster every minute, so a
 * stand-off between two careful players still ends.
 */
public final class Overtime {
	private Overtime() {}

	private static final Map<Kits, Integer> LIMIT_SECONDS = new EnumMap<>(Kits.class);
	static {
		for (Kits k : Kits.values()) LIMIT_SECONDS.put(k, 480);
		LIMIT_SECONDS.put(Kits.CRYSTAL, 600);
		LIMIT_SECONDS.put(Kits.DIAMOND_SMP, 600);
		LIMIT_SECONDS.put(Kits.NETHERITE_SMP, 600);
		LIMIT_SECONDS.put(Kits.SPEAR_MACE, 360);     // spear and mace fights are best when they end quickly
	}

	public static int limit(Kits kit) { return LIMIT_SECONDS.get(kit); }

	public static void setLimit(Kits kit, int seconds) { LIMIT_SECONDS.put(kit, seconds); }

	/** Call every fight tick with how long the round has run; {@code say} announces the start of overtime. */
	public static void tick(int fightTicks, Kits kit, List<ServerPlayer> alive, java.util.function.Consumer<String> say) {
		int start = limit(kit) * 20;
		if (fightTicks < start) return;
		if (fightTicks == start) say.accept("§c§lOVERTIME! §cNeither side has finished it: the arena starts to wither everyone.");
		int into = fightTicks - start;
		if (into % 20 == 0) {   // every 20 seconds one heart off everyone's maximum health, which no potion or apple can heal back
			double max = Math.max(2.0, 20.0 - 2.0 * (into / 400));
			for (ServerPlayer p : alive) {
				var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
				if (attr != null && attr.getBaseValue() != max) attr.setBaseValue(max);
				if (p.getHealth() > max) p.setHealth((float) max);
			}
		}
		// Two minutes in and still standing: potions out-heal the wither, so everyone now takes a hit every second that nothing outheals,
		// until the totems and potions run out.
		if (into >= 2400 && into % 20 == 0) {
			for (ServerPlayer p : alive) if (p.level() instanceof net.minecraft.server.level.ServerLevel lvl) p.hurtServer(lvl, p.damageSources().magic(), 4f);
		}
		if (into % 100 == 0) for (ServerPlayer p : alive) p.addEffect(new MobEffectInstance(MobEffects.WITHER, 140, Math.min(3, into / 1800)));
	}

	/** Back to ten hearts for the next round (and for the player's own world after the match). */
	public static void reset(ServerPlayer p) {
		var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (attr != null && attr.getBaseValue() != 20.0) attr.setBaseValue(20.0);
	}
}
