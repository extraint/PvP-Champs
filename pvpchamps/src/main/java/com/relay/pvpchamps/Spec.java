package com.relay.pvpchamps;

import net.minecraft.server.level.ServerPlayer;

/** Calls into Relay Spectator, tolerating an older jar that lacks the server API. */
final class Spec {
	private static boolean warned;

	private Spec() {}

	static boolean canDrive(ServerPlayer p) {
		try { return com.relay.spectate.RelayServerApi.canDrive(p); }
		catch (LinkageError e) { warn(e); return false; }
	}

	static void stop(ServerPlayer p) {
		try { com.relay.spectate.RelayServerApi.stop(p); }
		catch (LinkageError e) { warn(e); }
	}

	static void watch(ServerPlayer p, String a, String b, String kit, String round, String event, String extra) {
		try { com.relay.spectate.RelayServerApi.watch(p, a, b, kit, round, event, extra); }
		catch (LinkageError e) { warn(e); }
	}

	static void play(ServerPlayer p, String a, String b, String kit, String round, String event) {
		try { com.relay.spectate.RelayServerApi.play(p, a, b, kit, round, event); }
		catch (LinkageError e) { warn(e); }
	}

	static void roundEnd(ServerPlayer p, String loser) {
		try { com.relay.spectate.RelayServerApi.roundEnd(p, loser); }
		catch (LinkageError e) { warn(e); }
	}

	private static void warn(LinkageError e) {
		if (warned) return;
		warned = true;
		System.err.println("[pvpchamps] Relay Spectator is too old (missing server API): update relay-spectator to the latest jar. " + e);
	}
}
