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

/**
 * The moments worth rewatching, recorded as the commentary sees them: kills, totem pops and huge hits, each with the match
 * and round it happened in. This is a timeline you can browse on the website and in chat, not a video replay.
 */
public final class Highlights {
	private Highlights() {}

	public static final class Entry {
		public long at;
		public String type, text, match;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<Entry> LIST = new ArrayList<>();      // newest first
	private static final int MAX = 300;

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("pvpchamps").resolve("highlights.json"); }

	public static synchronized void load() {
		try {
			if (Files.exists(file())) {
				List<Entry> read = GSON.fromJson(Files.readString(file()), new TypeToken<List<Entry>>() {}.getType());
				if (read != null) { LIST.clear(); LIST.addAll(read); }
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvpchamps] highlights.json unreadable: " + e);
		}
		WebStats.provide("/api/highlights", () -> { synchronized (Highlights.class) { return new ArrayList<>(LIST); } });
	}

	public static synchronized void add(String type, String text, String match) {
		Entry e = new Entry();
		e.at = System.currentTimeMillis();
		e.type = type; e.text = text; e.match = match;
		LIST.add(0, e);
		while (LIST.size() > MAX) LIST.remove(LIST.size() - 1);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(LIST));
		} catch (IOException ex) {
			System.err.println("[pvpchamps] could not write highlights.json: " + ex);
		}
	}

	public static synchronized List<Entry> recent(int n) { return new ArrayList<>(LIST.subList(0, Math.min(n, LIST.size()))); }
}
