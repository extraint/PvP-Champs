package com.relay.pvphub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/pvphub/config.json: where the lobby and the arenas are, and the timings. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public double lobbyX = 0.5, lobbyY = 151, lobbyZ = 0.5;
	public float lobbyYaw = 0f;
	/** First duel arena's centre (floor top); the others follow along +x. */
	public int arenaX = 1000, arenaY = 100, arenaZ = 0;
	public int duelSlots = 6;
	public int slotSpacing = 120;
	public int ffaX = 1000, ffaY = 100, ffaZ = 400;
	public String ffaKit = "sword";
	public int countdownSeconds = 5;
	public int requestSeconds = 60;
	/** With VexBot installed: how many bots keep the lobby busy (0 = none), how many duels they fight at once, how many arenas they leave free for players. */
	public int crowdBots = 100;
	public int crowdDuels = 3;
	public int crowdReserveArenas = 2;
	/** Whether the lobby platform has been built (it is built once, then left alone so you can change it). */
	public boolean lobbyBuilt = false;

	private static Config current = new Config();

	public static Config get() { return current; }

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("pvphub").resolve("config.json"); }

	public static void load() {
		try {
			Files.createDirectories(file().getParent());
			if (Files.exists(file())) current = GSON.fromJson(Files.readString(file()), Config.class);
			if (current == null) current = new Config();
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvphub] config.json unreadable, using defaults: " + e);
			current = new Config();
		}
		save();   // writes new settings into an older file
	}

	public static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(current));
		} catch (IOException e) {
			System.err.println("[pvphub] could not write config.json: " + e);
		}
	}
}
