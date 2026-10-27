package com.relay.pvpchamps;

import java.util.List;

/**
 * Who should win a series, as a probability. Each fighter's strength is their rating in the kit (a roster bot's from its tier,
 * a real player's from their results) nudged by their recent form (match win rate once they have played a few), a team is
 * its members' average, and the chance of winning one game becomes the chance of taking a best-of-three.
 */
public final class Forecast {
	private Forecast() {}

	private static double strength(String name, Kits kit) {
		double r = Rankings.rating(name, kit);
		int[] rec = WebStats.matchRecord(name);
		int games = rec[0] + rec[1];
		if (games >= 3) r += ((double) rec[0] / games - 0.5) * 80;      // up to 40 rating points either way for a hot or cold streak
		return r;
	}

	private static double side(List<String> names, Kits kit) {
		double sum = 0;
		for (String n : names) sum += strength(n, kit);
		return sum / names.size();
	}

	/** The chance that side A takes a best-of-three against side B in this kit. */
	public static double seriesWin(List<String> a, List<String> b, Kits kit, int bestOf) {
		double p = 1.0 / (1.0 + Math.pow(10, (side(b, kit) - side(a, kit)) / 400.0));
		if (bestOf <= 1) return p;
		return bestOf >= 5 ? p * p * p * (10 - 15 * p + 6 * p * p) : p * p * (3 - 2 * p);
	}
}
