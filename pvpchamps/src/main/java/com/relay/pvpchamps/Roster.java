package com.relay.pvpchamps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import vexbot.BotSettings;
import vexbot.DifficultyParams;

/**
 * The bots you can fight. Names are the event's players as seen in the broadcast; their styles, kits and
 * ratings here are invented approximations, not their real stats. Edit config/pvpchamps/roster.json freely.
 */
public final class Roster {
	private Roster() {}

	/** @param difficulty VexBot preset (EASY..PERFECT) or COMBAT_MASTER; @param style VexBot play style; @param moves ids of the extra moves this bot uses. */
	public record Opponent(String name, String title, String difficulty, String style, int rating, double accuracy,
			List<String> kits, List<String> moves) {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static List<Opponent> roster = defaults();

	private static List<Opponent> defaults() {
		List<String> all = Kits.ids();
		// Names are PvP Champs players (Liquipedia's main-event page, plus Flowtives and C0RZZ). Playstyles, ratings,
		// kits and move lists are invented: each player gets their own style rather than one shared one.
		List<String> press = List.of("strafe_pressure", "anti_stall");
		List<String> heal = List.of("heal_4_hearts");
		return new ArrayList<>(List.of(
			// Combat Masters
			new Opponent("Coldified", "Champion, adapts to whoever he faces", "COMBAT_MASTER", "ADAPTIVE", 95, 1.0,
				all, join(heal, "pop_punish", "pearl_close", "bait_retreat", "lunge_swap", "pearl_wind", "wall_off", "mend_reset")),
			new Opponent("Swight", "Spear & Mace specialist, relentless", "COMBAT_MASTER", "AGGRESSIVE", 92, 1.0,
				List.of("spear_mace", "mace"), join(press, "pop_punish", "pearl_close", "bait_retreat", "lunge_swap", "pearl_wind")),
			new Opponent("ImMixed", "All-rounder, balanced", "COMBAT_MASTER", "BALANCED", 90, 1.0,
				all, join(heal, "pop_punish", "bait_retreat", "mend_reset", "wall_off")),
			new Opponent("x_Kyran_x", "SMP pressure", "COMBAT_MASTER", "AGGRESSIVE", 88, 1.0,
				List.of("diamond_smp", "netherite_smp", "cart"), join(press, "pop_punish", "pearl_close", "wall_off", "tnt_minecart")),
			// Experts
			new Opponent("Freekee_Fang", "Cart and potion fighter, patient pressure", "EXPERT", "BALANCED", 86, 1.0,
				List.of("cart", "netherite_op"), join(heal, "pearl_close", "mend_reset", "tnt_minecart")),
			new Opponent("ninorc15", "Sword and SMP, plays it tight", "EXPERT", "DEFENSIVE", 85, 1.0,
				List.of("sword", "diamond_smp", "netherite_smp"), join(heal, "pop_punish", "wall_off", "mend_reset")),
			new Opponent("mikagoated", "Mace and cart, reads the opponent", "EXPERT", "ADAPTIVE", 83, 1.0,
				List.of("mace", "spear_mace", "cart"), join(heal, "pearl_close", "lunge_swap", "tnt_minecart")),
			new Opponent("Sweatgod", "Spear mace, never lets up", "EXPERT", "AGGRESSIVE", 82, 1.0,
				List.of("spear_mace", "mace", "uhc"), join(press, "pop_punish", "lunge_swap", "pearl_wind")),
			new Opponent("BLMcheat", "Patient defender", "EXPERT", "DEFENSIVE", 80, 1.0,
				List.of("diamond_smp", "netherite_smp"), join(heal, "mend_reset", "bait_retreat", "wall_off")),
			new Opponent("Sizierung", "Sword fighter, balanced", "EXPERT", "BALANCED", 79, 1.0,
				List.of("sword", "uhc", "diamond_smp"), join(heal, "pop_punish", "bait_retreat")),
			new Opponent("JACKS5ON", "Cart and SMP, balanced", "EXPERT", "BALANCED", 78, 1.0,
				List.of("cart", "diamond_smp", "netherite_smp"), join(heal, "pearl_close", "tnt_minecart")),
			new Opponent("Ricardoxx_", "Aggressive mace player", "EXPERT", "AGGRESSIVE", 77, 1.0,
				List.of("mace", "spear_mace", "netherite_smp"), join(press, "pearl_close", "lunge_swap")),
			new Opponent("TheobaldTheBird", "Adaptive SMP player", "EXPERT", "ADAPTIVE", 76, 1.0,
				List.of("diamond_smp", "netherite_smp", "sword"), join(heal, "pop_punish", "wall_off")),
			new Opponent("DarkKnightAragon", "Defensive UHC and sword", "EXPERT", "DEFENSIVE", 75, 1.0,
				List.of("uhc", "sword", "diamond_smp"), join(heal, "mend_reset", "wall_off")),
			new Opponent("C0RZZ", "Adaptive all-rounder", "EXPERT", "ADAPTIVE", 74, 1.0,
				all, join(heal, "pop_punish", "pearl_close", "bait_retreat")),
			new Opponent("Ymiau", "Mace player, quick hands", "EXPERT", "AGGRESSIVE", 81, 1.0,
				List.of("mace", "spear_mace", "sword"), join(press, "pearl_close", "lunge_swap", "pearl_wind")),
			new Opponent("ZeNain", "Sword and SMP", "EXPERT", "BALANCED", 77, 1.0,
				List.of("sword", "diamond_smp", "netherite_smp"), join(heal, "pop_punish", "bait_retreat")),
			new Opponent("ItzTwilightTT", "Adaptive, plays the matchup", "EXPERT", "ADAPTIVE", 73, 1.0,
				all, join(heal, "pop_punish", "pearl_close")),
			new Opponent("Alepordio", "Careful SMP player", "HARD", "DEFENSIVE", 71, 0.95,
				List.of("diamond_smp", "netherite_smp", "uhc"), join(heal, "mend_reset", "wall_off")),
			new Opponent("Blvckwlf", "Cart and sword", "HARD", "BALANCED", 70, 0.95,
				List.of("cart", "sword", "diamond_smp"), join(heal, "pearl_close", "tnt_minecart")),
			new Opponent("SpawnPlayer", "Aggressive cart player", "HARD", "AGGRESSIVE", 72, 0.95,
				List.of("cart", "netherite_op", "mace"), join(press, "pearl_close", "tnt_minecart")),
			new Opponent("1kiks_", "Defensive SMP", "HARD", "DEFENSIVE", 70, 0.95,
				List.of("diamond_smp", "netherite_smp"), join(heal, "mend_reset", "wall_off")),
			new Opponent("MyDogIsConfused", "Safe, careful fighter", "HARD", "SAFE", 68, 0.95,
				all, join(heal, "mend_reset", "wall_off")),
			new Opponent("Flowtives", "Host and streamer, plays safe", "HARD", "SAFE", 64, 0.9,
				List.of("sword", "uhc", "diamond_smp"), join(heal, "mend_reset", "bait_retreat"))));
	}

	private static List<String> join(List<String> base, String... more) {
		List<String> out = new ArrayList<>(base);
		out.addAll(List.of(more));
		return out;
	}

	/** Bump when the built-in roster changes: an older roster.json is then replaced (the old one is kept as roster.json.old). */
	private static final String ROSTER_VERSION = "4";

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("pvpchamps").resolve("roster.json");
		Path versionFile = file.resolveSibling("roster.version");
		try {
			Files.createDirectories(file.getParent());
			boolean current = Files.exists(versionFile) && Files.readString(versionFile).trim().equals(ROSTER_VERSION);
			if (Files.exists(file) && current) {
				List<Opponent> read = GSON.fromJson(Files.readString(file), new TypeToken<List<Opponent>>() {}.getType());
				if (read != null && !read.isEmpty()) roster = read;
			} else {
				// First run, or the built-in roster is newer than the saved one: write the built-in roster.
				if (Files.exists(file)) Files.move(file, file.resolveSibling("roster.json.old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				Files.writeString(file, GSON.toJson(roster));
				Files.writeString(versionFile, ROSTER_VERSION);
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvpchamps] roster.json unreadable, using the built-in roster: " + e);
		}
	}

	public static List<Opponent> all() { return roster; }

	public static Opponent byName(String name) {
		for (Opponent o : roster) if (o.name().equalsIgnoreCase(name)) return o;
		return null;
	}

	/** Pushes the opponent's difficulty and style into VexBot so the bot of that name fights like them. */
	public static void applyProfile(Opponent o) {
		try {
			DifficultyParams p;
			if (CombatMaster.is(o.difficulty())) {
				p = CombatMaster.params(o.accuracy());
				CombatMaster.applySettings();
			} else {
				p = DifficultyParams.forPreset(BotSettings.Difficulty.valueOf(o.difficulty().toUpperCase()));
				p.accuracy = Math.min(1.0, p.accuracy * o.accuracy());
			}
			BotSettings.botDifficulties.put(o.name(), p);
			BotSettings.setPlayStyle(o.name(), BotSettings.PlayStyle.valueOf(o.style().toUpperCase()));
		} catch (RuntimeException e) {
			System.err.println("[pvpchamps] bad profile for " + o.name() + ": " + e);
		}
	}
}
