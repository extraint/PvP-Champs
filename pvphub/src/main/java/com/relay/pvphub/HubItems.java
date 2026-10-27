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

	public static final String UNRANKED = "unranked", RANKED = "ranked", FFA = "ffa", STATS = "stats", SPECTATE = "spectate", LEAVE = "leave", LEAVE_QUEUE = "leave_queue";

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
		p.getInventory().setItem(0, make(Items.IRON_SWORD, "§aUnranked Queue", UNRANKED, "§7Right-click: pick a kit and find a duel", "§7Ratings do not change"));
		p.getInventory().setItem(1, make(Items.DIAMOND_SWORD, "§bRanked Queue", RANKED, "§7Right-click: pick a kit and find a rated duel", "§7Matched by rating and ping"));
		p.getInventory().setItem(2, make(Items.END_CRYSTAL, "§dFFA", FFA, "§7Right-click: jump into the free-for-all"));
		p.getInventory().setItem(4, make(Items.NETHER_STAR, "§6Stats & Leaderboards", STATS, "§7Right-click: ratings, records, top players"));
		p.getInventory().setItem(6, make(Items.COMPASS, "§eSpectate", SPECTATE, "§7Right-click: watch a live duel"));
		if (queued) p.getInventory().setItem(8, make(Items.BARRIER, "§cLeave Queue", LEAVE_QUEUE, "§7Right-click: stop searching"));
		else p.getInventory().setItem(8, make(Items.OAK_DOOR, "§cBack to Lobby", LEAVE, "§7Right-click: return to the lobby"));
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
		for (int i : new int[] {0, 1, 2, 4, 6, 8}) if (actionOf(p.getInventory().getItem(i)) == null) return false;
		return queued == LEAVE_QUEUE.equals(actionOf(p.getInventory().getItem(8)));
	}
}
