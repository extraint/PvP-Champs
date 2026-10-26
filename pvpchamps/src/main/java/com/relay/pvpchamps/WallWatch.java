package com.relay.pvpchamps;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Diagnostics: logs a fighter that stays pinned against the arena wall, with what it was doing, so the cause can be found. */
public final class WallWatch {
	private final Map<String, Integer> near = new HashMap<>();

	/** Call every fight tick for each living fighter. */
	public void check(String name, ServerPlayer p, ServerPlayer foe, Vec3 centre, String kit, boolean bot) {
		if (p == null || !Arena.exists()) return;
		double d = Math.hypot(p.getX() - centre.x, p.getZ() - centre.z);
		if (d < Arena.FLOOR_R - 2.5) { near.remove(name); return; }
		int n = near.merge(name, 1, Integer::sum);
		if (n % 60 == 0) {
			System.err.println("[pvpchamps] WALL " + name + " " + kit + " d=" + String.format("%.1f", d) + " for " + n / 20 + "s move=" + (bot ? com.relay.pvpchamps.moves.MoveEngine.currentMove(name) : "player")
				+ " foeDist=" + (foe == null ? "-" : String.format("%.1f", foe.distanceTo(p))) + " hp=" + String.format("%.0f", p.getHealth())
				+ " vel=" + String.format("%.2f", p.getDeltaMovement().horizontalDistance()));
		}
	}
}
