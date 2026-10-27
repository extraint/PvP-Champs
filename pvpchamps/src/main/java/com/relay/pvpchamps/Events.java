package com.relay.pvpchamps;

import java.util.List;
import net.minecraft.server.MinecraftServer;

/** What happens around a match: one place the matches report to, so rankings, predictions and highlights stay in step. */
public final class Events {
	private Events() {}

	/** A series has begun. */
	public static void matchStarted(MinecraftServer server, Bout match) {
		Predictions.start(server, match.sides(), match.describe(), match.kit(), match.bestOf());
	}

	/** The first round of a series is over. */
	public static void firstRoundOver() { Predictions.close(); }

	/** A series was played to the end (not cancelled). */
	public static void matchEnded(MinecraftServer server, List<String> winners, List<String> losers, Kits kit, String label, int ws, int ls) {
		Rankings.record(winners, losers, kit);
		Predictions.resolve(server, winners);
		Highlights.add("match", String.join(" + ", winners) + " beat " + String.join(" + ", losers) + " " + ws + "-" + ls + " (" + kit.label + ")", label);
	}
}
