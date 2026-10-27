package com.relay.pvphub;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Watching a duel: spectator mode, riding the camera of one of the fighters. */
public final class Spectate {
	private Spectate() {}

	public static void watch(ServerPlayer viewer, Duel d) {
		if (d.isFinished()) return;
		Hub.toLobbyQuietly(viewer);
		Queues.leave(viewer, false);
		viewer.getInventory().clearContent();
		HubItems.giveSpectator(viewer);
		d.watchers.add(viewer.getUUID());
		Hub.setState(viewer, Hub.State.SPECTATE);
		viewer.setGameMode(GameType.SPECTATOR);
		viewer.setCamera(d.p[0]);
		viewer.sendSystemMessage(Component.literal("§7Watching §f" + d.p[0].getGameProfile().name() + " §7vs §f" + d.p[1].getGameProfile().name() + " §7· /hub to leave"));
	}

	public static void stop(ServerPlayer viewer) {
		if (Hub.state(viewer) != Hub.State.SPECTATE) return;
		for (Duel d : Duels.active()) d.watchers.remove(viewer.getUUID());
		viewer.setCamera(viewer);
		Hub.setState(viewer, Hub.State.LOBBY);
	}

	public static void open(ServerPlayer p) {
		var duels = Duels.active();
		Menu.Builder b = new Menu.Builder("Ranked Fights", 6);
		int i = 0;
		for (Duel d : duels) {
			if (i >= 45) break;
			String a = d.p[0].getGameProfile().name(), c = d.p[1].getGameProfile().name();
			double ea = Tiers.elo(d.p[0], d.kit), ec = Tiers.elo(d.p[1], d.kit);
			b.set(i++, Menu.icon(d.kit.iconStack(), "§f" + a + " " + Tiers.tag(ea) + " §c§lVS §f" + c + " " + Tiers.tag(ec),
				"§6| §fKit: §e" + d.kit.label, "§6| §fScore: §e" + d.score[0] + " - " + d.score[1], "§6| §f" + (d.ranked ? "Ranked" : "Unranked") + (d.bestOf > 1 ? " · best of " + d.bestOf : ""), "", "§eClick to spectate"),
				x -> { x.closeContainer(); Duel live = Duels.of(d.p[0]); if (live != null) watch(x, live); });
		}
		if (duels.isEmpty()) b.set(22, Menu.icon(net.minecraft.world.item.Items.BARRIER, "§7No fights right now", Arenas.building().isEmpty() ? "§7Check back in a moment" : "§7" + Arenas.building()));
		b.set(49, Menu.icon(net.minecraft.world.item.Items.CLOCK, "§6Gamemodes: §fAll", "§7" + duels.size() + " live fights"));
		b.open(p);
	}
}
