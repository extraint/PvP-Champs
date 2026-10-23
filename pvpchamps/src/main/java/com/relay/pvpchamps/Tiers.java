package com.relay.pvpchamps;

import java.util.HashMap;
import java.util.Map;
import vexbot.BotSettings;
import vexbot.DifficultyParams;

/**
 * MCTiers-style tiers (HT1 best, then LT1, HT2 ... LT5) per player and gamemode, looked up on mctiers.gg in
 * October 2026. A bot's skill in a match comes from its tier in that match's kit, so a player who is HT1 in UHC
 * but LT3 in Mace fights like it. Players with no listing use {@link #DEFAULT}; the five headliners and the two
 * event winners are set by hand.
 */
public final class Tiers {
	private Tiers() {}

	public static final String DEFAULT = "LT3";
	private static final Map<String, Map<String, String>> T = new HashMap<>();

	private static void put(String name, String... pairs) {
		Map<String, String> m = new HashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
		T.put(name.toLowerCase(), m);
	}

	static {
		// The headliners: HT1 in everything, a class above the rest of the field.
		for (String n : new String[] {"Coldified", "ImMixed", "Swight", "BLMcheat", "Freekee_Fang"}) put(n, "*", "HT1");
		// Event winners (not listed on mctiers): one step under the headliners.
		put("ItzTwilightTT", "*", "HT2");
		put("Alepordio", "*", "HT2");
		// From mctiers.gg profiles.
		put("C0RZZ", "UHC", "LT1", "Pot", "HT3", "NethOP", "HT3", "SMP", "HT3", "Sword", "LT3", "Mace", "LT4", "Axe", "LT2");
		put("DarkKnightAragon", "UHC", "HT3", "Pot", "LT3", "NethOP", "LT3", "Mace", "HT4", "SMP", "LT1");
		put("Flowtives", "SMP", "HT3", "Sword", "HT3", "Pot", "HT3", "Mace", "LT3", "Axe", "LT3", "UHC", "HT1", "NethOP", "LT2");
		put("JACKS5ON", "Sword", "HT3", "NethOP", "HT3", "Pot", "HT3", "UHC", "LT3");
		put("MyDogIsConfused", "Sword", "HT3", "SMP", "HT3");
		put("Ricardoxx_", "SMP", "LT3", "NethOP", "LT3", "Axe", "LT3", "Mace", "LT3", "Pot", "LT3", "Sword", "HT4", "UHC", "HT2");
		put("Sizierung", "Axe", "HT3", "Sword", "HT3", "UHC", "LT3", "Pot", "LT3", "SMP", "LT3", "NethOP", "LT3", "Mace", "HT4");
		put("Sweatgod", "UHC", "LT2", "Pot", "HT3", "NethOP", "HT3", "Sword", "LT3", "Axe", "LT3", "SMP", "HT4");
		put("ZeNain", "Pot", "HT3", "UHC", "HT4");
		put("mikagoated", "Axe", "HT3", "Pot", "HT3");
		put("ninorc15", "SMP", "HT3", "UHC", "HT3", "Axe", "HT3", "Sword", "LT2", "Pot", "LT2", "Mace", "LT2", "NethOP", "LT2");
		put("x_Kyran_x", "Pot", "HT3", "SMP", "HT3", "Mace", "LT3", "UHC", "LT3");
	}

	/** The mctiers gamemodes that stand for each kit, best match first. */
	private static String[] modes(Kits kit) {
		return switch (kit) {
			case SWORD -> new String[] {"Sword"};
			case UHC -> new String[] {"UHC"};
			case MACE, SPEAR_MACE -> new String[] {"Mace", "Axe"};
			case NETHERITE_OP -> new String[] {"NethOP", "Pot"};
			case DIAMOND_SMP -> new String[] {"SMP", "Pot"};
			case NETHERITE_SMP -> new String[] {"NethOP", "SMP"};
			case CART -> new String[] {"Pot", "SMP"};
		};
	}

	/** HT1 = 10, LT1 = 9, HT2 = 8 ... LT5 = 1. */
	public static int score(String tier) {
		int n = tier.charAt(2) - '0';
		return (6 - n) * 2 - (tier.charAt(0) == 'L' ? 1 : 0);
	}

	/** This player's tier in this kit: the kit's own gamemode, else a close one a step lower, else the default. */
	public static String tier(String name, Kits kit) {
		Map<String, String> m = T.get(name.toLowerCase());
		if (m == null) return DEFAULT;
		if (m.containsKey("*")) return m.get("*");
		String[] ms = modes(kit);
		if (m.containsKey(ms[0])) return m.get(ms[0]);
		for (int i = 1; i < ms.length; i++) if (m.containsKey(ms[i])) return step(m.get(ms[i]), 1);
		String best = null;
		for (String t : m.values()) if (best == null || score(t) > score(best)) best = t;
		return best == null ? DEFAULT : step(best, 2); // only known in other modes: assume a bit weaker here
	}

	private static String step(String tier, int down) {
		int s = Math.max(1, score(tier) - down);
		int n = 6 - (s + 1) / 2;
		return (s % 2 == 0 ? "HT" : "LT") + n;
	}

	/** Fighting skill for a tier. HT1 is past Vexbot's PERFECT; LT5 is a little under MEDIUM. */
	public static DifficultyParams params(String tier) {
		int s = score(tier);
		BotSettings.Difficulty base = s >= 9 ? BotSettings.Difficulty.PERFECT : s >= 6 ? BotSettings.Difficulty.EXPERT
			: s >= 3 ? BotSettings.Difficulty.HARD : BotSettings.Difficulty.MEDIUM;
		DifficultyParams p = DifficultyParams.forPreset(base);
		int within = switch (base) { case PERFECT -> 10 - s; case EXPERT -> 8 - s; case HARD -> 5 - s; default -> 2 - s; };
		p.accuracy = Math.max(0.3, p.accuracy - within * 0.04);
		p.missChance = Math.min(0.4, p.missChance + within * 0.025);
		p.reactionMinTicks += within / 2;
		p.reactionMaxTicks += within;
		p.shieldReactionMs += within * 40;
		if (s == 10) { // HT1: nothing left to chance
			p.missChance = 0;
			p.accuracy = 1.0;
			p.clutchAccuracy = 1.0;
			p.reactionMinTicks = 0;
			p.reactionMaxTicks = 0;
			p.shieldReactionMs = 0;
			p.shieldReactionJitterMs = 0;
			p.stunSlamChance = 100;
		}
		return p;
	}
}
