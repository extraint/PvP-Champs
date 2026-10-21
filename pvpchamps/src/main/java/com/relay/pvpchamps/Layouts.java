package com.relay.pvpchamps;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A player's own arrangement of a kit. Saving records which item sits in each hotbar/inventory slot; the next time
 * the kit is handed to that player, the items are moved into those slots. The kit's contents never change, only
 * where things go. Stored per player and kit in config/pvpchamps/layouts.
 */
public final class Layouts {
	private Layouts() {}

	private static final Gson GSON = new Gson();

	/** A player who is arranging a kit: what they had before, restored when they finish. */
	private record Editing(Kits kit, ItemStack[] stash) {}

	private static final Map<UUID, Editing> EDITING = new java.util.HashMap<>();

	public static boolean editing(ServerPlayer p) { return EDITING.containsKey(p.getUUID()); }

	/** Swaps the player's inventory for the kit (in their saved layout) so they can rearrange it. */
	public static void edit(ServerPlayer p, Kits kit) {
		ItemStack[] stash = new ItemStack[41];
		for (int i = 0; i < 41; i++) stash[i] = p.getInventory().getItem(i).copy();
		EDITING.put(p.getUUID(), new Editing(kit, stash));
		ItemStack[] built = kit.build(p.level().getServer());
		vexbot.kit.KitManager.apply(p, apply(p, kit, built));
	}

	/** Ends editing: optionally saves the arrangement, then gives the player's own inventory back. */
	public static Kits finish(ServerPlayer p, boolean save) throws IOException {
		Editing e = EDITING.remove(p.getUUID());
		if (e == null) return null;
		if (save) save(p, e.kit());
		p.getInventory().clearContent(); // KitManager.apply only fills slots, so empty the kit out first
		vexbot.kit.KitManager.apply(p, e.stash());
		return e.kit();
	}

	private static Path file(UUID player, Kits kit) {
		return FabricLoader.getInstance().getConfigDir().resolve("pvpchamps").resolve("layouts").resolve(player + "_" + kit.id + ".json");
	}

	private static String id(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }

	/** Saves the player's current slots 0-35 as their layout for this kit. @return number of slots saved. */
	public static int save(ServerPlayer p, Kits kit) throws IOException {
		Map<Integer, String> slots = new LinkedHashMap<>();
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (!s.isEmpty()) slots.put(i, id(s));
		}
		Path f = file(p.getUUID(), kit);
		Files.createDirectories(f.getParent());
		Files.writeString(f, GSON.toJson(slots));
		return slots.size();
	}

	public static boolean reset(ServerPlayer p, Kits kit) throws IOException {
		return Files.deleteIfExists(file(p.getUUID(), kit));
	}

	/** The kit with the player's saved arrangement applied, or the kit unchanged when they have none. */
	public static ItemStack[] apply(ServerPlayer p, Kits kit, ItemStack[] stacks) {
		Path f = file(p.getUUID(), kit);
		if (!Files.exists(f)) return stacks;
		Map<Integer, String> saved;
		try {
			saved = GSON.fromJson(Files.readString(f), new TypeToken<Map<Integer, String>>() {}.getType());
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvpchamps] unreadable layout " + f + ": " + e);
			return stacks;
		}
		ItemStack[] out = new ItemStack[stacks.length];
		java.util.Arrays.fill(out, ItemStack.EMPTY);
		for (int i = 36; i < stacks.length; i++) out[i] = stacks[i]; // armour and offhand stay as the kit has them
		boolean[] used = new boolean[36];
		List<Integer> slotsInOrder = new ArrayList<>(saved.keySet());
		java.util.Collections.sort(slotsInOrder);
		for (int slot : slotsInOrder) {
			if (slot < 0 || slot >= 36) continue;
			for (int i = 0; i < 36; i++) {
				if (used[i] || stacks[i].isEmpty() || !id(stacks[i]).equals(saved.get(slot)) || !out[slot].isEmpty()) continue;
				out[slot] = stacks[i];
				used[i] = true;
				break;
			}
		}
		// Anything the saved layout had no slot for goes in the first free slots.
		int next = 0;
		for (int i = 0; i < 36; i++) {
			if (used[i] || stacks[i].isEmpty()) continue;
			while (next < 36 && !out[next].isEmpty()) next++;
			if (next < 36) out[next++] = stacks[i];
		}
		return out;
	}
}
