package com.relay.pvphub;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** A chest menu whose slots run code when clicked. Nothing in it can be taken out. */
public final class Menu {
	private Menu() {}

	public interface Click { void on(ServerPlayer p); }

	private final static class Gui extends ChestMenu {
		private final Map<Integer, Click> clicks;
		private final int rows;

		Gui(int id, Inventory inv, Container c, int rows, Map<Integer, Click> clicks) {
			super(rows == 6 ? MenuType.GENERIC_9x6 : rows == 4 ? MenuType.GENERIC_9x4 : rows == 2 ? MenuType.GENERIC_9x2 : MenuType.GENERIC_9x3, id, inv, c, rows);
			this.clicks = clicks;
			this.rows = rows;
		}

		@Override
		public void clicked(int slot, int button, ContainerInput input, Player player) {
			if (slot >= 0 && slot < rows * 9 && player instanceof ServerPlayer sp) {
				Click c = clicks.get(slot);
				if (c != null) c.on(sp);
			}
			broadcastFullState();   // nothing here is ever moved: put the client's view back
		}

		@Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
	}

	/** An item with a name and a few lines of text. */
	public static ItemStack icon(ItemStack base, String name, String... lore) {
		base.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		if (lore.length > 0) {
			java.util.List<Component> lines = new java.util.ArrayList<>();
			for (String l : lore) lines.add(Component.literal(l));
			base.set(DataComponents.LORE, new ItemLore(lines));
		}
		return base;
	}

	public static ItemStack icon(Item item, String name, String... lore) { return icon(new ItemStack(item), name, lore); }

	/** Builder for one menu: put items, attach clicks, open. */
	public static final class Builder {
		private final String title;
		private final int rows;
		private final SimpleContainer container;
		private final Map<Integer, Click> clicks = new HashMap<>();

		public Builder(String title, int rows) {
			this.title = title;
			this.rows = rows;
			this.container = new SimpleContainer(rows * 9);
		}

		public Builder set(int slot, ItemStack item) { container.setItem(slot, item); return this; }

		public Builder set(int slot, ItemStack item, Click click) { container.setItem(slot, item); clicks.put(slot, click); return this; }

		public void open(ServerPlayer p) {
			p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new Gui(id, inv, container, rows, clicks), Component.literal(title)));
		}
	}
}
