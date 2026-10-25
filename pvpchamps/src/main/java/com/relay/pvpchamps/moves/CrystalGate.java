package com.relay.pvpchamps.moves;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Crystals only go off with the opponent above the bot, even by 0.4 blocks. Vexbot places and detonates crystals on its
 * own whenever it carries some, so the stack is put aside while the opponent is level with or below the bot and put
 * back in the same slot the moment they are above it.
 */
public final class CrystalGate {
	private CrystalGate() {}

	public static void apply(Ctx c) {
		var inv = c.bot.getInventory();
		boolean allowed = c.target.getY() - c.bot.getY() >= 0.4;
		var st = c.st;
		if (allowed) {
			if (st.stash == null) return;
			if (inv.getItem(st.stashSlot).isEmpty()) inv.setItem(st.stashSlot, st.stash);
			st.stash = null; // (a restock already put crystals back: nothing to return)
			return;
		}
		if (st.stash != null) {
			boolean refilled = false; // a restock put a fresh stack in the inventory: the old one aside is stale
			for (int i = 0; i < 36; i++) if (inv.getItem(i).getItem() == Items.END_CRYSTAL) refilled = true;
			if (!refilled) return;
			st.stash = null;
		}
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() == Items.END_CRYSTAL) {
				st.stash = s.copy();
				st.stashSlot = i;
				inv.setItem(i, ItemStack.EMPTY);
				return;
			}
		}
	}
}
