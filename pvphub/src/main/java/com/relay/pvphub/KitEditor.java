package com.relay.pvphub;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Kit editing, the PvPHQ way: pick a kit, get its items, move them where you like (hotbar, inventory, armour and offhand
 * all count), then confirm or cancel. What is saved is a layout: for each of your 41 slots, which item of the kit goes
 * there. The kit itself never changes, so a layout can only rearrange it.
 */
public final class KitEditor {
	private KitEditor() {}

	private static final Gson GSON = new Gson();
	private static Map<String, Map<String, int[]>> layouts = new HashMap<>();
	private static final Map<UUID, Kit> EDITING = new HashMap<>();

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("pvphub").resolve("layouts.json"); }

	public static void load() {
		try {
			if (Files.exists(file())) layouts = GSON.fromJson(Files.readString(file()), new TypeToken<Map<String, Map<String, int[]>>>() {}.getType());
		} catch (Exception e) {
			System.err.println("[pvphub] layouts.json unreadable: " + e);
		}
		if (layouts == null) layouts = new HashMap<>();
		EDITING.clear();
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(layouts));
		} catch (Exception e) {
			System.err.println("[pvphub] could not write layouts.json: " + e);
		}
	}

	public static String editing(ServerPlayer p) {
		Kit k = EDITING.get(p.getUUID());
		return k == null ? "a kit" : k.label;
	}

	/** The kit's 41 stacks put where this player wants them (as the kit comes if they never edited it). */
	public static ItemStack[] arrange(ServerPlayer p, Kit kit, ItemStack[] base) {
		Map<String, int[]> mine = layouts.get(p.getUUID().toString());
		int[] map = mine == null ? null : mine.get(kit.id);
		if (map == null || map.length != 41) return base;
		ItemStack[] out = new ItemStack[41];
		java.util.Arrays.fill(out, ItemStack.EMPTY);
		boolean[] used = new boolean[41];
		for (int i = 0; i < 41; i++) {
			int j = map[i];
			if (j >= 0 && j < 41 && !used[j] && !base[j].isEmpty()) { out[i] = base[j]; used[j] = true; }
		}
		for (int j = 0; j < 41; j++) {          // anything the layout does not place (the kit changed since): first free slot
			if (used[j] || base[j].isEmpty()) continue;
			for (int i = 0; i < 36; i++) if (out[i].isEmpty()) { out[i] = base[j]; used[j] = true; break; }
		}
		return out;
	}

	public static void open(ServerPlayer p) {
		Menu.Builder b = new Menu.Builder(Config.get().serverName + " Kits", 5);
		b.set(4, Menu.icon(net.minecraft.world.item.Items.BOOK, "§6Kit Editor", "§7Pick a kit to lay out your inventory."));
		int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
		int i = 0;
		for (Kit k : Kit.values()) {
			boolean custom = layouts.containsKey(p.getUUID().toString()) && layouts.get(p.getUUID().toString()).containsKey(k.id);
			b.set(slots[i++], Menu.icon(k.iconStack(), "§6" + k.label, custom ? "§aYou have a custom layout" : "§7Default layout", "", "§eClick to edit", "§7Shift-click a kit you edited to reset it"),
				x -> { x.closeContainer(); start(x, k); });
		}
		b.open(p);
	}

	public static void start(ServerPlayer p, Kit kit) {
		if (!Hub.inLobby(p)) { p.sendSystemMessage(Component.literal("§cYou can only edit kits in the lobby.")); return; }
		Queues.leave(p, false);
		EDITING.put(p.getUUID(), kit);
		Hub.setState(p, Hub.State.EDIT);
		p.setGameMode(GameType.ADVENTURE);
		kit.give(p);
		p.sendSystemMessage(Component.literal("§6§lKIT EDITOR §7· §f" + kit.label + " §7· arrange your items, then:"));
		p.sendSystemMessage(button("§a§l[CONFIRM KIT EDIT]", "/kiteditor save", "Save this layout and return to spawn")
			.append(Component.literal("  ")).append(button("§c§l[CANCEL KIT EDIT]", "/kiteditor cancel", "Throw away the changes and return to spawn"))
			.append(Component.literal("  ")).append(button("§e§l[RESET]", "/kiteditor reset", "Go back to the kit's default layout")));
	}

	private static MutableComponent button(String text, String command, String hover) {
		return Component.literal(text).withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	/** Reads where each kit item ended up and saves that. */
	public static void confirm(ServerPlayer p) {
		Kit kit = EDITING.remove(p.getUUID());
		if (kit == null || Hub.state(p) != Hub.State.EDIT) { p.sendSystemMessage(Component.literal("§cYou are not editing a kit.")); return; }
		ItemStack[] base = kit.build(p.level().getServer());
		boolean[] used = new boolean[41];
		int[] map = new int[41];
		for (int i = 0; i < 41; i++) {
			map[i] = -1;
			ItemStack s = p.getInventory().getItem(i);
			if (s.isEmpty()) continue;
			for (int pass = 0; pass < 2 && map[i] < 0; pass++)               // same item and count first, then same item
				for (int j = 0; j < 41; j++) {
					if (used[j] || base[j].isEmpty() || !ItemStack.isSameItemSameComponents(base[j], s)) continue;
					if (pass == 0 && base[j].getCount() != s.getCount()) continue;
					map[i] = j; used[j] = true; break;
				}
		}
		layouts.computeIfAbsent(p.getUUID().toString(), k -> new HashMap<>()).put(kit.id, map);
		save();
		p.sendSystemMessage(Component.literal("§aSaved your §f" + kit.label + " §alayout."));
		Hub.toLobby(p);
	}

	public static void cancel(ServerPlayer p) {
		EDITING.remove(p.getUUID());
		if (Hub.state(p) == Hub.State.EDIT) { p.sendSystemMessage(Component.literal("§7Kit edit cancelled.")); Hub.toLobby(p); }
	}

	public static void resetLayout(ServerPlayer p) {
		Kit kit = EDITING.remove(p.getUUID());
		if (kit == null) return;
		Map<String, int[]> mine = layouts.get(p.getUUID().toString());
		if (mine != null) { mine.remove(kit.id); save(); }
		p.sendSystemMessage(Component.literal("§e" + kit.label + " §7is back to its default layout."));
		Hub.toLobby(p);
	}
}
