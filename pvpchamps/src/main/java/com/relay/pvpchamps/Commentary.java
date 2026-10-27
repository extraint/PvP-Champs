package com.relay.pvpchamps;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/**
 * An announcer for the running fight: kills, totem pops, big hits and players on low health, in chat for everyone who has not
 * muted it and in the website's live feed. The big moments are also recorded as {@link Highlights}.
 */
public final class Commentary {
	private Commentary() {}

	private record Entry(long at, String kind, String text, String match) {}

	private static final ArrayDeque<Entry> FEED = new ArrayDeque<>();   // newest first
	/** What a player sees: everything in chat, only the big moments in chat (the rest on the action bar), all of it on the action bar, or nothing. */
	public enum Mode { FULL, LITE, BAR, OFF }

	private static final Map<UUID, Mode> MODES = new HashMap<>();
	private static boolean enabled = true;
	private static Bout last;
	private static final Map<String, Integer> totems = new HashMap<>(), said = new HashMap<>();
	private static final Set<String> low = new HashSet<>();
	private static int ticks;
	private static final Set<Integer> crystalIds = new HashSet<>();

	/** How many end crystals have been placed since this fight began. */
	public static int crystalCount() { return crystalIds.size(); }

	public static void init() { WebStats.provide("/api/feed", () -> { synchronized (FEED) { return new ArrayList<>(FEED); } }); }

	public static void setEnabled(boolean on) { enabled = on; }

	public static void setMode(ServerPlayer p, Mode m) { MODES.put(p.getUUID(), m); }

	public static Mode modeOf(ServerPlayer p) { return MODES.getOrDefault(p.getUUID(), Mode.LITE); }

	public static boolean toggleMute(ServerPlayer p) {
		if (modeOf(p) == Mode.OFF) { MODES.remove(p.getUUID()); return false; }
		MODES.put(p.getUUID(), Mode.OFF);
		return true;
	}

	private static boolean fighting(String name) {
		Bout b = MatchManager.current();
		return b != null && b.fighterNames().stream().anyMatch(n -> n.equalsIgnoreCase(name));
	}

	private static String hearts(float hp) { return String.format("%.1f", hp / 2f); }

	/** Posts a line. {@code coloured} is what chat shows, {@code plain} what the website and highlights keep. */
	private static void say(MinecraftServer server, String kind, String coloured, String plain, boolean highlight, String actor) {
		boolean big = highlight || kind.equals("kill");
		if (!enabled) return;
		if (actor != null) {                                    // the same kind of line for the same fighter at most every two seconds
			String key = kind + ":" + actor;
			Integer prev = said.get(key);
			if (prev != null && ticks - prev < (kind.equals("low") ? 400 : 40)) return;   // a low-health call at most every 20 seconds per fighter
			said.put(key, ticks);
		}
		Bout b = MatchManager.current();
		String match = b == null ? "" : b.describe();
		synchronized (FEED) {
			FEED.addFirst(new Entry(System.currentTimeMillis(), kind, plain, match));
			while (FEED.size() > 80) FEED.removeLast();
		}
		if (highlight) Highlights.add(kind, plain, match);
		Component c = Component.literal(coloured);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (vexbot.api.VexBotApi.isBot(server, p.getGameProfile().name())) continue;
			switch (modeOf(p)) {
				case FULL -> p.sendSystemMessage(c);
				case LITE -> p.sendSystemMessage(c, !big);     // the big moments stay in chat, the rest is a line above the hotbar
				case BAR -> p.sendSystemMessage(c, true);
				case OFF -> { }
			}
		}
	}

	/** A fighter died; {@code killer} is null for an explosion or a fall. */
	public static void kill(MinecraftServer server, ServerPlayer victim, ServerPlayer killer) {
		String v = victim.getGameProfile().name();
		if (!fighting(v)) return;
		String k = killer == null ? null : killer.getGameProfile().name();
		String weapon = killer == null || killer.getMainHandItem().isEmpty() ? "" : " with " + killer.getMainHandItem().getHoverName().getString();
		String plain = k == null || k.equalsIgnoreCase(v) ? v + " is down" : k + " takes down " + v + weapon;
		say(server, "kill", "§c☠ §f" + plain, plain, true, null);
	}

	/** Damage landed on a fighter: calls out the big ones. */
	public static void damage(LivingEntity victim, DamageSource src, float taken) {
		if (!(victim instanceof ServerPlayer v) || taken < 8f) return;
		String vn = v.getGameProfile().name();
		if (!fighting(vn)) return;
		ServerPlayer a = src.getEntity() instanceof ServerPlayer sp ? sp : null;
		String an = a == null ? null : a.getGameProfile().name();
		boolean mace = a != null && a.getMainHandItem().is(Items.MACE);
		String how = mace && taken >= 10f ? "a mace smash" : a == null ? "an explosion" : a.getMainHandItem().isEmpty() ? "a hit" : a.getMainHandItem().getHoverName().getString();
		String plain = (an == null || an.equalsIgnoreCase(vn) ? vn + " takes" : an + " lands") + " " + hearts(taken) + " hearts" + (an == null || an.equalsIgnoreCase(vn) ? " from " : " on " + vn + " with ") + how;
		say(v.level().getServer(), "hit", "§6⚔ §f" + plain, plain, taken >= 12f, vn);
	}

	private static int totemCount(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < 41; i++) { var st = p.getInventory().getItem(i); if (st.is(Items.TOTEM_OF_UNDYING)) n += st.getCount(); }
		return n;
	}

	/** Totem pops and low health, noticed by watching the fighters. */
	public static void tick(MinecraftServer server) {
		ticks++;
		Bout b = MatchManager.current();
		if (b != last) { totems.clear(); low.clear(); said.clear(); crystalIds.clear(); last = b; }
		if (b == null) return;
		if (ticks % 5 == 0 && Arena.exists()) {        // end crystals in the arena: counts how many the fighters place
			var v = Arena.spot();
			for (var e : b.level().getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,
					new net.minecraft.world.phys.AABB(v.x - 45, v.y - 5, v.z - 45, v.x + 45, v.y + 40, v.z + 45))) crystalIds.add(e.getId());
		}
		if (ticks % 2 != 0) return;
		for (String n : b.fighterNames()) {
			ServerPlayer p = server.getPlayerList().getPlayerByName(n);
			if (p == null || !p.isAlive()) continue;
			int now = totemCount(p);
			Integer prev = totems.put(n, now);
			if (prev != null && now < prev && p.getHealth() > 0) {
				String plain = n + " pops a totem (" + now + " left)";
				say(server, "totem", "§e✦ §f" + plain, plain, now <= 1, n);
			}
			float hp = p.getHealth() + p.getAbsorptionAmount();
			if (hp <= 6f && low.add(n)) {
				String plain = n + " is down to " + hearts(hp) + " hearts";
				say(server, "low", "§c♥ §f" + plain, plain, false, n);
			} else if (hp > 12f) low.remove(n);
		}
	}
}
