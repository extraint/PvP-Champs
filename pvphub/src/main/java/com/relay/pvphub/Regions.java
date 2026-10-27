package com.relay.pvphub;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The region selector: which server regions you queue on. Pings are from California. */
public final class Regions {
	private Regions() {}

	public record Region(String id, String city, int ping, String icon) {}

	public static final List<Region> ALL = List.of(
		new Region("NA West", "Los Angeles", 8, "blue_banner"),
		new Region("NA Central", "Dallas", 38, "blue_banner"),
		new Region("NA East", "New York", 66, "blue_banner"),
		new Region("South America", "São Paulo", 168, "green_banner"),
		new Region("EU West", "London", 138, "red_banner"),
		new Region("EU Central", "Frankfurt", 146, "yellow_banner"),
		new Region("EU North", "Stockholm", 160, "light_blue_banner"),
		new Region("Asia", "Tokyo", 108, "white_banner"),
		new Region("Asia SE", "Singapore", 172, "orange_banner"),
		new Region("India", "Mumbai", 226, "orange_banner"),
		new Region("Oceania", "Sydney", 152, "cyan_banner"),
		new Region("Middle East", "Dubai", 210, "brown_banner"));

	public static boolean enabled(Region r) { return Config.get().regions.contains(r.id); }

	public static void toggle(Region r) {
		var list = Config.get().regions;
		if (list.contains(r.id)) { if (list.size() > 1) list.remove(r.id); } else list.add(r.id);
		Config.save();
	}

	/** The enabled region with the lowest ping: where you "are". */
	public static Region home() {
		Region best = null;
		for (Region r : ALL) if (enabled(r) && (best == null || r.ping < best.ping)) best = r;
		return best == null ? ALL.get(0) : best;
	}

	public static void open(ServerPlayer p) {
		Menu.Builder b = new Menu.Builder("Manual Queue", 5);
		b.set(4, Menu.icon(Items.BOOK, "§6Region Selector", "§7Pick the regions you are queued on.", "§7Your ping decides your home region."));
		int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
		for (int i = 0; i < ALL.size() && i < slots.length; i++) {
			Region r = ALL.get(i);
			boolean on = enabled(r);
			b.set(slots[i], Menu.icon(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(r.icon)), "§6" + r.city, "§6| §fRegion: §e" + r.id, "§6| §fYour ping: §e" + r.ping + "ms", "§6| §fStatus: " + (on ? "§aEnabled" : "§cDisabled"), "", "§eClick to toggle this region"),
				x -> { toggle(r); open(x); });
		}
		b.open(p);
	}
}
