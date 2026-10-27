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
		int rows = Math.max(1, Math.min(6, (duels.size() + 8) / 9));
		Menu.Builder b = new Menu.Builder("Live duels", rows);
		int i = 0;
		for (Duel d : duels) {
			if (i >= rows * 9) break;
			String a = d.p[0].getGameProfile().name(), c = d.p[1].getGameProfile().name();
			b.set(i++, Menu.icon(d.kit.iconStack(), "§f" + a + " §7vs §f" + c, "§7" + (d.ranked ? "Ranked " : "Unranked ") + d.kit.label, "§7Score " + d.score[0] + "-" + d.score[1], "§eClick to watch"),
				x -> { x.closeContainer(); Duel live = Duels.of(d.p[0]); if (live != null) watch(x, live); });
		}
		if (duels.isEmpty()) b.set(4, Menu.icon(net.minecraft.world.item.Items.BARRIER, "§7No duels right now"));
		b.open(p);
	}
}
