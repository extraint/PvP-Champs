package com.relay.pvphub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The chest menus, laid out like PvPHQ's: kit icons in the middle rows, the number on each icon is how many are playing it. */
public final class Menus {
	private Menus() {}

	private static final Random RNG = new Random();
	private static final int[] KIT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
	private static final int[][] PLAYING = new int[2][Kit.values().length];      // the simulated crowd on each queue

	/** How many are on a kit's queue, in fights and searching: the simulated server plus the real ones. */
	private static int[] playing(Kit k, boolean ranked) {
		int[] row = PLAYING[ranked ? 1 : 0];
		int base = switch (k) { case SWORD -> 90; case NETHERITE_OP -> 70; case CRYSTAL -> 65; case UHC -> 60; case SPEAR_MACE -> 55; case DIAMOND_SMP -> 35; case NETHERITE_SMP -> 30; default -> 15; };
		if (ranked) base = base * 2 / 3;
		if (row[k.ordinal()] == 0) row[k.ordinal()] = base;
		row[k.ordinal()] = Math.max(2, Math.min(99, row[k.ordinal()] + RNG.nextInt(9) - 4 + (base - row[k.ordinal()]) / 8));
		int fighting = row[k.ordinal()], queued = Math.max(0, fighting / 9 + RNG.nextInt(3) - 1) + Queues.size(k, ranked);
		for (Duel d : Duels.active()) if (d.kit == k && d.ranked == ranked) fighting += 2;
		return new int[] {Math.min(99, fighting), queued};
	}

	private static ItemStack counted(ItemStack s, int n) { s.setCount(Math.max(1, Math.min(99, n))); s.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 99); return s; }

	/** Pick a kit to queue for. */
	public static void kitPick(ServerPlayer p, boolean ranked) { kitPick(p, ranked, List.of(Kit.values()), ranked ? "Ranked Queue" : "Unranked Queue"); }

	static void kitPick(ServerPlayer p, boolean ranked, List<Kit> kits, String title) {
		Menu.Builder b = new Menu.Builder(title, 5);
		b.set(4, counted(Menu.icon(Items.OBSIDIAN, "§6" + title, "§7Pick a kit to queue for"), 1));
		int i = 0;
		for (Kit k : kits) {
			int[] n = playing(k, ranked);
			double elo = Stats.elo(p.getUUID(), p.getGameProfile().name(), k);
			List<String> lore = new ArrayList<>();
			lore.add("§6| §fFighting: §e" + n[0]);
			lore.add("§6| §fQueueing: §e" + n[1]);
			if (ranked) lore.add("§6| §fYour tier: " + Tiers.tag(elo));
			lore.add("");
			lore.add("§eClick to queue");
			b.set(KIT_SLOTS[i++], counted(Menu.icon(k.iconStack(), (ranked ? "§6" : "§a") + k.label, lore.toArray(new String[0])), n[0]),
				x -> { x.closeContainer(); Queues.join(x, k, ranked); });
		}
		b.set(40, Menu.icon(Items.COMPASS, "§eRegion Selector", "§7Queueing on: §f" + String.join(", ", Config.get().regions)), x -> Regions.open(x));
		b.open(p);
	}

	/** The cake: the free-for-all and friends. */
	public static void events(ServerPlayer p) {
		Menu.Builder b = new Menu.Builder("Events", 3);
		b.set(11, Menu.icon(Items.END_CRYSTAL, "§dFFA", "§6| §fPlaying: §e" + (Ffa.count(p.level().getServer()) + 20 + RNG.nextInt(15)), "§7Kit: §f" + Config.get().ffaKit, "", "§eClick to join"), x -> { x.closeContainer(); Ffa.join(x); });
		b.set(13, Menu.icon(Items.SPYGLASS, "§bRanked Fights", "§7Spectate a live fight", "", "§eClick to open"), x -> Spectate.open(x));
		b.set(15, Menu.icon(Items.IRON_SWORD, "§aQuick Duel", "§7A sword duel against whoever", "§7is free right now", "", "§eClick to play"), x -> { x.closeContainer(); Queues.join(x, Kit.SWORD, false); });
		b.open(p);
	}

	/** Your profile: a tier per kit, with the leaderboards behind each. */
	public static void stats(ServerPlayer p) {
		Menu.Builder b = new Menu.Builder("Profile & Stats", 5);
		Stats.Player me = Stats.of(p.getUUID(), p.getGameProfile().name());
		b.set(4, Menu.icon(Items.CREEPER_HEAD, "§6" + p.getGameProfile().name(), "§7Rank: §f" + Config.get().playerRank, "§7Region: §f" + Regions.home().city()));
		int i = 0;
		for (Kit k : Kit.values()) {
			Stats.KitRecord r = Stats.record(p.getUUID(), p.getGameProfile().name(), k);
			b.set(KIT_SLOTS[i++], Menu.icon(k.iconStack(), "§6" + k.label, "§6| §fTier: " + Tiers.tag(r.elo), "§6| §fRecord: §a" + r.wins + "W §c" + r.losses + "L",
				"§6| §fWin streak: §e" + r.streak + " §7(best " + r.bestStreak + ")", "", "§eClick for the top players"), x -> { x.closeContainer(); leaderboard(x, k); });
		}
		b.set(40, Menu.icon(Items.END_CRYSTAL, "§dFFA", "§6| §fKills: §e" + me.ffaKills, "§6| §fDeaths: §e" + me.ffaDeaths, "§6| §fBest streak: §e" + me.ffaBest, "", "§eClick for the top players"), x -> { x.closeContainer(); ffaBoard(x); });
		b.open(p);
	}

	/** The top ten of a kit: you and the bots together. */
	public static void leaderboard(ServerPlayer p, Kit k) {
		List<Map.Entry<String, Double>> all = new ArrayList<>();
		for (String bot : Crowd.names()) all.add(Map.entry(bot, Crowd.elo(bot, k)));
		for (var e : Stats.leaderboard(k, 10)) all.add(Map.entry(e.getKey(), e.getValue().elo));
		all.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
		p.sendSystemMessage(Component.literal("§6§l=== Top " + k.label + " ==="));
		for (int i = 0; i < Math.min(10, all.size()); i++)
			p.sendSystemMessage(Component.literal((i < 3 ? "§6" : "§e") + (i + 1) + ". §f" + all.get(i).getKey() + " " + Tiers.tag(all.get(i).getValue())));
		if (all.isEmpty()) p.sendSystemMessage(Component.literal("§7Nobody yet."));
	}

	public static void ffaBoard(ServerPlayer p) {
		var top = Stats.ffaLeaderboard(10);
		p.sendSystemMessage(Component.literal("§6§l=== Top FFA ==="));
		if (top.isEmpty()) p.sendSystemMessage(Component.literal("§7Nobody has fought yet."));
		int i = 1;
		for (var e : top) p.sendSystemMessage(Component.literal("§e" + i++ + ". §f" + e.name + " §7· " + e.ffaKills + " kills · " + e.ffaDeaths + " deaths · best streak " + e.ffaBest));
	}

	/** What a right-click on a hotbar item, or a click on an NPC, does. */
	public static void action(ServerPlayer p, String action) {
		switch (action) {
			case HubItems.UNRANKED -> kitPick(p, false);
			case HubItems.RANKED -> kitPick(p, true);
			case HubItems.FFA -> Ffa.join(p);
			case HubItems.EVENTS -> events(p);
			case HubItems.STATS -> stats(p);
			case HubItems.SPECTATE -> Spectate.open(p);
			case HubItems.KIT -> KitEditor.open(p);
			case HubItems.REGIONS -> Regions.open(p);
			case HubItems.LEAVE -> Hub.toLobby(p);
			case HubItems.LEAVE_QUEUE -> Queues.leave(p, true);
			case "moneysmp" -> kitPick(p, false, List.of(Kit.DIAMOND_SMP, Kit.NETHERITE_SMP), "Money SMP");
			case "uhc" -> {
				Menu.Builder b = new Menu.Builder("UHC Games", 3);
				b.set(11, Menu.icon(Items.GOLDEN_APPLE, "§aUnranked UHC", "§eClick to queue"), x -> { x.closeContainer(); Queues.join(x, Kit.UHC, false); });
				b.set(15, Menu.icon(Items.GOLDEN_APPLE, "§6Ranked UHC", "§6| §fYour tier: " + Tiers.tag(Stats.elo(p.getUUID(), p.getGameProfile().name(), Kit.UHC)), "§eClick to queue"),
					x -> { x.closeContainer(); Queues.join(x, Kit.UHC, true); });
				b.open(p);
			}
			default -> {}
		}
	}
}
