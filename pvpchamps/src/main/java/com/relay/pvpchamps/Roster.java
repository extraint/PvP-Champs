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
		// Names are PvP Champs II players; styles, ratings and move lists are invented. Everyone fights aggressively.
		List<String> press = List.of("strafe_pressure", "anti_stall");
		return new ArrayList<>(List.of(
			new Opponent("Coldified", "Champion, all-rounder", "COMBAT_MASTER", "AGGRESSIVE", 95, 1.0,
				all, join(press, "pop_punish", "pearl_close", "bait_retreat", "lunge_swap", "pearl_wind", "wall_off")),
			new Opponent("Swight", "Spear & Mace specialist", "COMBAT_MASTER", "AGGRESSIVE", 92, 1.0,
				List.of("spear_mace", "mace"), join(press, "pop_punish", "pearl_close", "bait_retreat", "lunge_swap", "pearl_wind")),
			new Opponent("ImMixed", "All-rounder", "COMBAT_MASTER", "AGGRESSIVE", 90, 1.0,
				all, join(press, "pop_punish", "bait_retreat", "mend_reset", "wall_off")),
			new Opponent("x_Kyran_x", "SMP pressure", "COMBAT_MASTER", "AGGRESSIVE", 88, 1.0,
				List.of("diamond_smp", "netherite_smp", "cart"), join(press, "pop_punish", "pearl_close", "wall_off", "tnt_minecart")),
			new Opponent("Freekee_Fang", "Cart and potion fighter", "EXPERT", "AGGRESSIVE", 86, 1.0,
				List.of("cart", "netherite_op"), join(press, "pearl_close", "mend_reset", "tnt_minecart")),
			new Opponent("ninorc15", "Sword and SMP", "EXPERT", "AGGRESSIVE", 85, 1.0,
				List.of("sword", "diamond_smp", "netherite_smp"), join(press, "pop_punish", "wall_off")),
			new Opponent("mikagoated", "Mace and cart", "EXPERT", "AGGRESSIVE", 83, 1.0,
				List.of("mace", "spear_mace", "cart"), join(press, "pearl_close", "lunge_swap", "tnt_minecart")),
			new Opponent("Sweatgod", "Spear mace", "EXPERT", "AGGRESSIVE", 82, 1.0,
				List.of("spear_mace", "mace", "uhc"), join(press, "pop_punish", "lunge_swap", "pearl_wind")),
			new Opponent("BLMcheat", "Patient defender, now pushing", "EXPERT", "AGGRESSIVE", 80, 1.0,
				List.of("diamond_smp", "netherite_smp"), join(press, "mend_reset", "bait_retreat", "wall_off")),
			new Opponent("Ironclad", "Qualifier", "HARD", "AGGRESSIVE", 66, 0.9,
				all, join(press, "pearl_close")),
			new Opponent("Nightfall", "Qualifier", "EXPERT", "AGGRESSIVE", 72, 0.95,
				all, join(press, "mend_reset"))));
	}

	private static List<String> join(List<String> base, String... more) {
		List<String> out = new ArrayList<>(base);
		out.addAll(List.of(more));
		return out;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("pvpchamps").resolve("roster.json");
		try {
			Files.createDirectories(file.getParent());
			if (Files.exists(file)) {
				List<Opponent> read = GSON.fromJson(Files.readString(file), new TypeToken<List<Opponent>>() {}.getType());
				if (read != null && !read.isEmpty()) roster = read;
			} else {
				Files.writeString(file, GSON.toJson(roster));
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
