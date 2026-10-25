package com.relay.pvpchamps;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Crystal kit's restock shulker: a shulker box placed next to a fighter disappears and refills their kit. Shared by
 * the 1v1 and 2v2 matches.
 */
final class Restock {
	private Restock() {}

	/** If a shulker box sits within four blocks of the fighter, removes it and refills the fighter's kit. */
	static void placed(ServerLevel level, ServerPlayer p, net.minecraft.world.item.ItemStack[] want) {
		net.minecraft.core.BlockPos at = p.blockPosition();
		for (int dx = -4; dx <= 4; dx++)
			for (int dz = -4; dz <= 4; dz++)
				for (int dy = -2; dy <= 3; dy++) {
					net.minecraft.core.BlockPos pos = at.offset(dx, dy, dz);
					if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
						level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
						run(p, want);
						return;
					}
				}
	}

	private static boolean noRestock(net.minecraft.world.item.ItemStack w) {
		var item = w.getItem();
		return item == net.minecraft.world.item.Items.RESPAWN_ANCHOR || item == net.minecraft.world.item.Items.TOTEM_OF_UNDYING;
	}

	private static String stockKey(net.minecraft.world.item.ItemStack w) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(w.getItem()) + "|"
			+ w.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
	}

	/**
	 * Refills the kit by what each stack is, not by where it sits: a fighter who has rearranged the hotbar keeps their
	 * layout, and nothing is overwritten (the old slot-by-slot refill wrote the kit's glowstone over anchors that had
	 * been moved into its slot). Anchors, totems, worn armour and the offhand are never touched;
	 * tools, potions and the shulker box are renewed or given back; stackable items are topped up to the kit's count.
	 */
	static void run(ServerPlayer p, net.minecraft.world.item.ItemStack[] want) {
		var inv = p.getInventory();
		// Worn armour and the offhand are left exactly as they are: a restock refills items only.
		java.util.Map<String, java.util.List<Integer>> groups = new java.util.LinkedHashMap<>();
		for (int i = 0; i < 36; i++) {
			var w = want[i];
			if (!w.isEmpty() && !noRestock(w)) groups.computeIfAbsent(stockKey(w), k -> new java.util.ArrayList<>()).add(i);
		}
		java.util.List<java.util.Map.Entry<net.minecraft.world.item.ItemStack, Integer>> pending = new java.util.ArrayList<>(); // missing stacks and their kit slots
		for (var g : groups.values()) {
			var sample = want[g.get(0)];
			String key = stockKey(sample);
			java.util.List<Integer> mine = new java.util.ArrayList<>();
			for (int i = 0; i < 36; i++) if (!inv.getItem(i).isEmpty() && stockKey(inv.getItem(i)).equals(key)) mine.add(i);
			if (sample.getMaxStackSize() == 1) { // tools, potions, the shulker box: one per kit entry, renewed in place
				for (int k = 0; k < g.size(); k++) {
					var fresh = want[g.get(k)].copy();
					if (k < mine.size()) inv.setItem(mine.get(k), fresh);
					else pending.add(java.util.Map.entry(fresh, g.get(k)));
				}
			} else { // stackable: top up to the kit's total
				int need = 0, have = 0;
				for (int i : g) need += want[i].getCount();
				for (int i : mine) have += inv.getItem(i).getCount();
				int deficit = need - have;
				if (deficit > 0) System.err.println("[pvpchamps] restock " + p.getGameProfile().name() + ": " + key + " need " + need + " have " + have);
				for (int i : mine) {
					var st = inv.getItem(i);
					int add = Math.min(deficit, st.getMaxStackSize() - st.getCount());
					if (add > 0) { st.grow(add); deficit -= add; }
				}
				for (int i = 0; deficit > 0 && i < g.size(); i++) {
					var fresh = sample.copy();
					fresh.setCount(Math.min(deficit, sample.getMaxStackSize()));
					deficit -= fresh.getCount();
					pending.add(java.util.Map.entry(fresh, g.get(i)));
				}
			}
		}
		// Every missing stack goes back to its own kit slot first, so one cannot take another's place; only then the leftovers
		// look for any empty slot (and drop at the fighter's feet if there is none).
		java.util.List<java.util.Map.Entry<net.minecraft.world.item.ItemStack, Integer>> left = new java.util.ArrayList<>();
		for (var e : pending) {
			if (inv.getItem(e.getValue()).isEmpty()) inv.setItem(e.getValue(), e.getKey());
			else left.add(e);
		}
		for (var e : left) place(inv, e.getKey(), e.getValue());
		p.inventoryMenu.broadcastChanges();
		p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aRestocked."), true);
	}

	/** Puts a stack in its kit slot if that is empty, otherwise the first empty slot; if the inventory is full it is dropped. */
	private static void place(net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.item.ItemStack stack, int preferred) {
		if (inv.getItem(preferred).isEmpty()) { inv.setItem(preferred, stack); return; }
		for (int i = 0; i < 36; i++) if (inv.getItem(i).isEmpty()) { inv.setItem(i, stack); return; }
		inv.player.drop(stack, false);
	}

}
