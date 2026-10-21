package com.relay.pvpchamps;

import vexbot.BotSettings;
import vexbot.DifficultyParams;

/**
 * "Combat Master": the top difficulty, past Vexbot's PERFECT. Vexbot's own difficulty list is a fixed enum in its jar,
 * so this lives in the addon: a tuned DifficultyParams plus the strafe_pressure and anti_stall moves, which the
 * engine gives to every bot on this difficulty.
 */
public final class CombatMaster {
	private CombatMaster() {}

	public static final String NAME = "COMBAT_MASTER";

	public static boolean is(String difficulty) {
		String d = difficulty == null ? "" : difficulty.toUpperCase().replace(' ', '_');
		return d.equals(NAME);
	}

	/** Server-wide Vexbot settings that Combat Master relies on; applied when a Combat Master bot is set up. */
	public static void applySettings() {
		BotSettings.strafeInCombo = true;
		BotSettings.offhandTotemHearts = 8.0; // totem in the offhand from 8 hearts (Vexbot's default is 4)
		BotSettings.usePotions = true;
		BotSettings.maceTotem = true;
		BotSettings.useCobwebs = true; // Vexbot's cobweb traps and web drain
		BotSettings.useWebDrain = true;
		BotSettings.webDrainChance = 100; // web them whenever the chance comes up
		BotSettings.webEscape = true;
		BotSettings.hitWeb = true;
		var t = BotSettings.techniques; // the axe-stun-then-mace-slam combos are off unless a technique toggle is on
		t.stunSlam = true;
		t.spearStunSlam = true;
		t.elytraStunSlam = true;
		t.stunSlamVariant = vexbot.settings.TechniqueConfig.StunSlamVariant.OPTIMAL; // the tightest axe-to-mace timing
		BotSettings.counterStunChance = 100; // stun back whenever they swing at a bot holding a mace
		t.maceSwap = true;
		t.spearMaceSwap = true;
	}

	public static DifficultyParams params(double accuracy) {
		DifficultyParams p = DifficultyParams.forPreset(BotSettings.Difficulty.PERFECT);
		p.missChance = 0;
		p.accuracy = Math.min(1.0, p.accuracy * accuracy);
		p.reactionMinTicks = 0;
		p.reactionMaxTicks = 0;
		p.shieldReactionMs = 0;
		p.shieldReactionJitterMs = 0;
		p.shieldDisableCharge = 0.0;
		p.reachFlips = 0;
		p.stunSlamChance = 100;
		p.strafeAngle = 30.0; // wider side-steps than PERFECT's 18
		p.potIntervalSeconds = 0.02;
		return p;
	}
}
