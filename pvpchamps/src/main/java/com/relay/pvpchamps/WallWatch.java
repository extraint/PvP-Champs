package com.relay.pvpchamps;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Diagnostics: logs a fighter that stays pinned against the arena wall, with what it was doing, so the cause can be found. */
public final class WallWatch {
	private final Map<String, Integer> near = new HashMap<>();

	/**
	 * A fighter outside the mud circle (over the wall, onto the stands or out of the arena: ender pearls and wind charges get
	 * there) goes back inside, a few blocks in from the wall, with their momentum cleared. Returns true if they were moved.
	 */
	public static boolean contain(ServerPlayer p, Vec3 centre) {
		if (p == null || !Arena.exists() || !p.isAlive()) return false;
		double dx = p.getX() - centre.x, dz = p.getZ() - centre.z, d = Math.hypot(dx, dz);
		if (d <= Arena.FLOOR_R + 1.0) return false;
		double k = (Arena.FLOOR_R - 6.0) / d;
		System.err.println("[pvpchamps] CONTAIN " + p.getGameProfile().name() + " was " + String.format("%.0f", d) + " blocks from the centre (y " + String.format("%.0f", p.getY() - centre.y) + " above the floor): back inside");
		p.teleportTo((net.minecraft.server.level.ServerLevel) p.level(), centre.x + dx * k, centre.y, centre.z + dz * k, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
		p.setDeltaMovement(Vec3.ZERO);
		p.fallDistance = 0;
		return true;
	}

	/** The blocks at a fighter's feet and head and the four around them, for working out what is holding it. */
	private static String around(ServerPlayer p) {
		var level = p.level();
		var pos = p.blockPosition();
		StringBuilder sb = new StringBuilder();
		sb.append("feet=").append(id(level.getBlockState(pos))).append(" head=").append(id(level.getBlockState(pos.above()))).append(" below=").append(id(level.getBlockState(pos.below())));
		sb.append(" sides=");
		for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) sb.append(id(level.getBlockState(pos.relative(d)))).append('/');
		return sb.toString();
	}

	private static String id(net.minecraft.world.level.block.state.BlockState s) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	/** Call every fight tick for each living fighter. */
	public void check(String name, ServerPlayer p, ServerPlayer foe, Vec3 centre, String kit, boolean bot) {
		if (p == null || !Arena.exists()) return;
		double d = Math.hypot(p.getX() - centre.x, p.getZ() - centre.z);
		if (d < Arena.FLOOR_R - 2.5) { near.remove(name); return; }
		int n = near.merge(name, 1, Integer::sum);
		if (n % 60 == 0) {
			System.err.println("[pvpchamps] WALL " + name + " " + kit + " d=" + String.format("%.1f", d) + " for " + n / 20 + "s move=" + (bot ? com.relay.pvpchamps.moves.MoveEngine.currentMove(name) : "player")
				+ " foeDist=" + (foe == null ? "-" : String.format("%.1f", foe.distanceTo(p))) + " hp=" + String.format("%.0f", p.getHealth())
				+ " vel=" + String.format("%.2f", p.getDeltaMovement().horizontalDistance()) + " using=" + (p.isUsingItem() ? p.getUseItem().getItem() : "no") + " main=" + p.getMainHandItem().getItem() + " off=" + p.getOffhandItem().getItem() + " around=" + around(p));
		}
	}
}
