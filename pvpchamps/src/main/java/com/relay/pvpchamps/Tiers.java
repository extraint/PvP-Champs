package com.relay.pvpchamps;

import java.util.HashMap;
import java.util.Map;
import vexbot.BotSettings;
import vexbot.DifficultyParams;

/**
 * MCTiers-style tiers (HT1 best, then LT1, HT2 ... LT5, worth 60/45/30/20/10/6/4/3/2/1 points) per player and gamemode, looked up on mctiers.gg in
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
		// The headliners: their real mctiers strengths, lifted a step or two so they stay a class above the field.
		put("Coldified", "UHC", "HT1", "SMP", "HT1", "Pot", "HT1", "NethOP", "LT1", "Sword", "LT1", "Mace", "HT2");
		put("Swight", "Axe", "HT1", "SMP", "HT1", "UHC", "HT1", "NethOP", "HT1", "Mace", "LT1", "Sword", "LT1", "Pot", "LT1");
		put("ImMixed", "SMP", "HT1", "UHC", "LT1", "Sword", "LT1", "Pot", "LT1", "Mace", "HT2", "NethOP", "HT2");
		put("BLMcheat", "UHC", "HT1", "SMP", "LT1", "NethOP", "LT1", "Sword", "HT2", "Pot", "HT2", "Mace", "LT2");
		put("Freekee_Fang", "SMP", "HT1", "UHC", "LT1", "Pot", "LT1", "Axe", "LT1", "Sword", "HT2", "Mace", "HT2", "NethOP", "HT2");
		// Event winners (not listed on mctiers): around HT2.
		put("ItzTwilightTT", "Sword", "HT2", "Mace", "HT2", "SMP", "HT2", "UHC", "LT2", "Pot", "LT2", "NethOP", "LT2");
		put("Alepordio", "SMP", "HT2", "NethOP", "HT2", "UHC", "LT2", "Sword", "LT2", "Pot", "LT2", "Mace", "HT3");
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

	private static final String[] ORDER = {"HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5"};
	private static final int[] POINTS = {60, 45, 30, 20, 10, 6, 4, 3, 2, 1}; // MCTiers points per tier

	/** MCTiers points for a tier: HT1 60, LT1 45, HT2 30, LT2 20, HT3 10, LT3 6, HT4 4, LT4 3, HT5 2, LT5 1. */
	public static int points(String tier) {
		for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(tier)) return POINTS[i];
		return 6;
	}

	private static int idx(String tier) {
		for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(tier)) return i;
		return 5;
	}

	/** Higher is better; the points, so comparisons follow the real scale. */
	public static int score(String tier) { return points(tier); }

	/** This player's tier in this kit: the kit's own gamemode, else a close one a step lower, else the default. */
	public static String tier(String name, Kits kit) {
		Map<String, String> m = T.get(name.toLowerCase());
		if (m == null) return DEFAULT;
		if (m.containsKey("*")) return m.get("*");
		String[] ms = modes(kit);
		if (m.containsKey(ms[0])) return m.get(ms[0]);
		for (int i = 1; i < ms.length; i++) if (m.containsKey(ms[i])) return step(m.get(ms[i]), 1);
		String best = null;
		for (String t : m.values()) if (best == null || points(t) > points(best)) best = t;
		return best == null ? DEFAULT : step(best, 2); // only known in other modes: assume a bit weaker here
	}

	private static String step(String tier, int down) {
		return ORDER[Math.min(ORDER.length - 1, idx(tier) + down)];
	}

	/**
	 * Fighting skill from the tier's points, on a log scale (1 point is the floor, 60 is a perfect fighter), so
	 * the gaps match the point gaps: HT1 to LT1 is a small step, HT3 to LT3 a big one.
	 */
	public static DifficultyParams params(String tier) {
		double x = Math.log(Math.max(1, points(tier))) / Math.log(60); // 0..1
		DifficultyParams p = DifficultyParams.forPreset(BotSettings.Difficulty.PERFECT);
		p.accuracy = 0.45 + 0.55 * x;
		p.clutchAccuracy = Math.min(1.0, 0.55 + 0.45 * x);
		p.missChance = 0.32 * (1 - x);
		p.reactionMinTicks = (int) Math.round(5 * (1 - x));
		p.reactionMaxTicks = (int) Math.round(11 * (1 - x));
		p.shieldReactionMs = (int) Math.round(650 * (1 - x));
		p.shieldReactionJitterMs = (int) Math.round(120 * (1 - x));
		p.stunSlamChance = (int) Math.round(35 + 65 * x);
		if (x >= 1.0) { p.missChance = 0; p.accuracy = 1.0; p.clutchAccuracy = 1.0; p.shieldReactionJitterMs = 0; }
		return p;
	}
}
