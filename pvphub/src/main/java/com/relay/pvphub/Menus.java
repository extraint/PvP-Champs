package com.relay.pvphub;

import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** The chest menus the hotbar items open. */
public final class Menus {
	private Menus() {}

	/** Pick a kit to queue for. */
	public static void kitPick(ServerPlayer p, boolean ranked) {
		Menu.Builder b = new Menu.Builder(ranked ? "Ranked queue: pick a kit" : "Unranked queue: pick a kit", 3);
		int slot = 9;
		for (Kit k : Kit.values()) {
			double elo = Stats.elo(p.getUUID(), p.getGameProfile().name(), k);
			b.set(slot++, Menu.icon(k.iconStack(), (ranked ? "§b" : "§a") + k.label,
				ranked ? "§7Your rating: §f" + Math.round(elo) + " §7(" + Stats.rankName(elo) + ")" : "§7Just for fun",
				"§7In queue: §f" + Queues.size(k, ranked), "§eClick to search"),
				x -> { x.closeContainer(); Queues.join(x, k, ranked); });
		}
		b.open(p);
	}

	/** Your ratings, with a way into the leaderboards. */
	public static void stats(ServerPlayer p) {
		Menu.Builder b = new Menu.Builder("Stats & leaderboards", 4);
		int slot = 0;
		for (Kit k : Kit.values()) {
			Stats.KitRecord r = Stats.record(p.getUUID(), p.getGameProfile().name(), k);
			b.set(slot++, Menu.icon(k.iconStack(), "§f" + k.label, "§7Rating §f" + Math.round(r.elo) + " §7· " + Stats.rankName(r.elo),
				"§7Record §a" + r.wins + "W §c" + r.losses + "L", "§7Win streak §f" + r.streak + " §7(best " + r.bestStreak + ")", "§eClick for the top players"),
				x -> { x.closeContainer(); leaderboard(x, k); });
		}
		Stats.Player me = Stats.of(p.getUUID(), p.getGameProfile().name());
		b.set(31, Menu.icon(Items.END_CRYSTAL, "§dFFA", "§7Kills §f" + me.ffaKills + " §7· Deaths §f" + me.ffaDeaths, "§7Best streak §f" + me.ffaBest, "§eClick for the top players"),
			x -> { x.closeContainer(); ffaBoard(x); });
		b.open(p);
	}

	public static void leaderboard(ServerPlayer p, Kit k) {
		List<Map.Entry<String, Stats.KitRecord>> top = Stats.leaderboard(k, 10);
		p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6=== Top " + k.label + " ==="));
		if (top.isEmpty()) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Nobody has a ranked game yet."));
		int i = 1;
		for (var e : top) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e" + i++ + ". §f" + e.getKey() + " §7· " + Math.round(e.getValue().elo) + " " + Stats.rankName(e.getValue().elo) + " · " + e.getValue().wins + "W " + e.getValue().losses + "L"));
	}

	public static void ffaBoard(ServerPlayer p) {
		var top = Stats.ffaLeaderboard(10);
		p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6=== Top FFA ==="));
		if (top.isEmpty()) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Nobody has fought yet."));
		int i = 1;
		for (var e : top) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e" + i++ + ". §f" + e.name + " §7· " + e.ffaKills + " kills · " + e.ffaDeaths + " deaths · best streak " + e.ffaBest));
	}

	/** What a right-click on a hotbar item does. */
	public static void action(ServerPlayer p, String action) {
		switch (action) {
			case HubItems.UNRANKED -> kitPick(p, false);
			case HubItems.RANKED -> kitPick(p, true);
			case HubItems.FFA -> Ffa.join(p);
			case HubItems.STATS -> stats(p);
			case HubItems.SPECTATE -> Spectate.open(p);
			case HubItems.LEAVE -> Hub.toLobby(p);
			case HubItems.LEAVE_QUEUE -> Queues.leave(p, true);
			default -> {}
		}
	}
}
