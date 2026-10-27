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

	/** Bumped when the layout changes: an older file gets the new places for the lobby and arenas. */
	public int configVersion = 2;
	public double lobbyX = 0.5, lobbyY = 103, lobbyZ = 0.5;
	public float lobbyYaw = 180f;
	/** First duel colosseum's centre (floor top); the others follow along +x. Each is 255 blocks across. */
	public int arenaX = 3000, arenaY = 120, arenaZ = 0;
	public int duelSlots = 6;
	public int slotSpacing = 320;
	public int ffaX = 3000, ffaY = 120, ffaZ = -400;
	public String ffaKit = "sword";
	/** How fast the colosseums go up in the background (columns a tick; each column is a couple of hundred blocks). */
	public int arenaBuildColumnsPerTick = 120;
	public int countdownSeconds = 5;
	public int requestSeconds = 60;
	/** The look of the simulated server. */
	public String serverName = "PVPHQ";
	public String version = "1.21.11+";
	public String address = "play.pvphq.com";
	public String discord = "discord.gg/pvphq";
	public String store = "store.pvphq.com";
	/** Your rank (OWNER, DEVELOPER, ADMIN, MANAGER, SENIOR MOD, ARBITER, MOD, HELPER, MONEY SMP, CREATOR, HQ+ or DEFAULT). */
	public String playerRank = "HQ+";
	/** The "Online:" count shows this many on top of who is really there. */
	public int simulatedOnline = 3900;
	/** Regions switched on in the region selector (the lowest-ping one is yours). */
	public java.util.List<String> regions = new java.util.ArrayList<>(java.util.List.of("NA West", "NA Central", "NA East", "EU West", "EU Central"));
	/** The bots: how many fill the lobby, how many duels they fight at once, how many arenas they leave free for you. */
	public int crowdBots = 120;
	/** Bots take the other side of a queue when no player is waiting, after this many seconds (0 = at once). */
	public boolean botOpponents = true;
	public int botOpponentDelaySeconds = 2;
	public int crowdDuels = 5;
	public int crowdReserveArenas = 1;

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
		if (current.configVersion < 2) {          // the old small lobby and arenas: move everything to the new layout
			Config fresh = new Config();
			current.configVersion = 2;
			current.lobbyX = fresh.lobbyX; current.lobbyY = fresh.lobbyY; current.lobbyZ = fresh.lobbyZ; current.lobbyYaw = fresh.lobbyYaw;
			current.arenaX = fresh.arenaX; current.arenaY = fresh.arenaY; current.arenaZ = fresh.arenaZ; current.slotSpacing = fresh.slotSpacing; current.duelSlots = fresh.duelSlots;
			current.ffaX = fresh.ffaX; current.ffaY = fresh.ffaY; current.ffaZ = fresh.ffaZ;
			current.serverName = fresh.serverName; current.address = fresh.address; current.discord = fresh.discord;
			current.crowdBots = fresh.crowdBots; current.crowdDuels = fresh.crowdDuels; current.crowdReserveArenas = fresh.crowdReserveArenas;
		}
		if (current.regions == null) current.regions = new Config().regions;
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
