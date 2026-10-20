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

	/** @param difficulty VexBot preset (EASY..PERFECT); @param style VexBot play style; @param moves ids of the extra moves this bot uses. */
	public record Opponent(String name, String title, String difficulty, String style, int rating, double accuracy,
			List<String> kits, List<String> moves) {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static List<Opponent> roster = defaults();

	private static List<Opponent> defaults() {
		List<String> all = Kits.ids();
		return new ArrayList<>(List.of(
			new Opponent("Swight", "Spear & Mace specialist", "EXPERT", "AGGRESSIVE", 92, 1.0,
				List.of("spear_mace", "mace"), List.of("pop_punish", "pearl_close", "bait_retreat", "lunge_swap", "pearl_wind")),
			new Opponent("ImMixed", "All-rounder", "EXPERT", "ADAPTIVE", 90, 1.0,
				all, List.of("pop_punish", "bait_retreat", "mend_reset", "wall_off")),
			new Opponent("Freekee_Fang", "Cart and potion fighter", "HARD", "BALANCED", 84, 1.0,
				List.of("cart", "netherite_op"), List.of("pearl_close", "mend_reset", "tnt_minecart")),
			new Opponent("x_Kyran_x", "SMP pressure", "EXPERT", "AGGRESSIVE", 88, 1.0,
				List.of("diamond_smp", "netherite_smp", "cart"), List.of("pop_punish", "pearl_close", "wall_off", "tnt_minecart")),
			new Opponent("BLMcheat", "Patient defender", "HARD", "DEFENSIVE", 80, 1.0,
				List.of("diamond_smp", "netherite_smp"), List.of("mend_reset", "bait_retreat")),
			new Opponent("Ironclad", "Qualifier", "MEDIUM", "BALANCED", 66, 0.9,
				all, List.of("pearl_close")),
			new Opponent("Nightfall", "Qualifier", "HARD", "SAFE", 72, 0.95,
				all, List.of("mend_reset"))));
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
			DifficultyParams p = DifficultyParams.forPreset(BotSettings.Difficulty.valueOf(o.difficulty().toUpperCase()));
			p.accuracy = Math.min(1.0, p.accuracy * o.accuracy());
			BotSettings.botDifficulties.put(o.name(), p);
			BotSettings.setPlayStyle(o.name(), BotSettings.PlayStyle.valueOf(o.style().toUpperCase()));
		} catch (RuntimeException e) {
			System.err.println("[pvpchamps] bad profile for " + o.name() + ": " + e);
		}
	}
}
