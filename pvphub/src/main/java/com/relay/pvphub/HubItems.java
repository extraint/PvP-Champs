package com.relay.pvphub;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** The hotbar items of the lobby. Each carries an action name in its custom data; right-clicking one runs it. */
public final class HubItems {
	private HubItems() {}

	public static final String UNRANKED = "unranked", RANKED = "ranked", FFA = "ffa", EVENTS = "events", STATS = "stats", SPECTATE = "spectate", KIT = "kit", REGIONS = "regions", LEAVE = "leave", LEAVE_QUEUE = "leave_queue";

	private static ItemStack make(net.minecraft.world.item.Item item, String name, String action, String... lore) {
		ItemStack s = Menu.icon(item, name, lore);
		CompoundTag tag = new CompoundTag();
		tag.putString("pvphub", action);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return s;
	}

	public static String actionOf(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		if (d == null) return null;
		return d.copyTag().getString("pvphub").orElse(null);
	}

	/** The lobby hotbar. While queued, the last slot leaves the queue. */
	public static void giveLobby(ServerPlayer p, boolean queued) {
		p.getInventory().clearContent();
		p.getInventory().setItem(0, make(Items.IRON_SWORD, "§aUnranked Queue §7(Right Click)", UNRANKED, "§7Queue a duel just for fun"));
		p.getInventory().setItem(1, make(Items.GOLDEN_SWORD, "§6Ranked Queue §7(Right Click)", RANKED, "§7Queue a rated duel", "§7Climb from LT5 to HT1"));
		p.getInventory().setItem(2, make(Items.CAKE, "§dEvents §7(Right Click)", EVENTS, "§7FFA and more"));
		p.getInventory().setItem(4, make(Items.CREEPER_HEAD, "§aProfile & Stats §7(Right Click)", STATS, "§7Your tiers, records and the leaderboards"));
		p.getInventory().setItem(6, make(Items.BOOK, "§eKit Editor §7(Right Click)", KIT, "§7Lay out your kits"));
		p.getInventory().setItem(7, make(Items.SPYGLASS, "§bRanked Fights §7(Right Click)", SPECTATE, "§7Spectate a live fight"));
		if (queued) p.getInventory().setItem(8, make(Items.BARRIER, "§cLeave Queue §7(Right Click)", LEAVE_QUEUE, "§7Stop searching"));
		else p.getInventory().setItem(8, make(Items.COMPASS, "§eRegion Selector §7(Right Click)", REGIONS, "§7Choose the regions you queue on"));
		p.inventoryMenu.broadcastFullState();
	}

	/** Spectators only get the way back. */
	public static void giveSpectator(ServerPlayer p) {
		p.getInventory().clearContent();
		p.getInventory().setItem(8, make(Items.OAK_DOOR, "§cBack to Lobby", LEAVE, "§7Right-click: return to the lobby"));
		p.inventoryMenu.broadcastFullState();
	}

	/** True if the player's inventory is exactly the lobby's (checked once a second to put back anything dropped or moved). */
	public static boolean intact(ServerPlayer p, boolean queued) {
		for (int i : new int[] {0, 1, 2, 4, 6, 7, 8}) if (actionOf(p.getInventory().getItem(i)) == null) return false;
		return queued == LEAVE_QUEUE.equals(actionOf(p.getInventory().getItem(8)));
	}
}
