package com.relay.spectate;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * For other server mods (the PvP Champs Vexbot extension uses this): drive a player's spectator HUD without
 * them typing commands. Does nothing for players who don't have the client mod.
 */
public final class RelayServerApi {
	private RelayServerApi() {}

	/** Start the camera and HUD for a spectator watching {@code a} vs {@code b}. Empty strings leave a label hidden. */
	public static boolean watch(ServerPlayer viewer, String a, String b, String kit, String round, String event, String pick) {
		return send(viewer, new RelayNet.AutoSpectate(a, b, nz(kit), nz(round), nz(event), nz(pick), false));
	}

	/** HUD and round breakdown for a player who is fighting; their camera is never touched. */
	public static boolean play(ServerPlayer fighter, String self, String opponent, String kit, String round, String event) {
		return send(fighter, new RelayNet.AutoSpectate(self, opponent, nz(kit), nz(round), nz(event), "", true));
	}

	public static boolean stop(ServerPlayer viewer) {
		return send(viewer, new RelayNet.AutoSpectate("", "", "", "", "", "", false));
	}

	/** True if this player has the client mod and can be driven. */
	public static boolean canDrive(ServerPlayer p) {
		return ServerPlayNetworking.canSend(p, RelayNet.AutoSpectate.TYPE);
	}

	private static boolean send(ServerPlayer p, RelayNet.AutoSpectate payload) {
		if (!canDrive(p)) return false;
		ServerPlayNetworking.send(p, payload);
		return true;
	}

	private static String nz(String s) { return s == null ? "" : s; }
}
