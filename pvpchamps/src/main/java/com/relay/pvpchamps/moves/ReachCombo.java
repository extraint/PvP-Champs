package com.relay.pvpchamps.moves;

/**
 * The reach combo from PvP videos: hit at the very edge of reach (about 3 blocks), then move so the opponent cannot
 * hit back, and repeat. The bot holds a ring of 2.7 to 3.05 blocks: closer than that it backs off, further and it
 * sprints in. Each hit is followed by a w-tap (sprint dropped for a tick) so the knockback is full, and the strafe
 * side swaps so the opponent's aim has to keep chasing. The combo ends when the opponent lands a hit, raises a
 * shield, or after its hit cap. Skill scales with the bot's tier: a longer chain, a tighter ring, crit-timed swings
 * and a shorter pause between combos.
 */
public final class ReachCombo implements Move {
	private int t, hits, tapLeft, lastHurt;
	private float dir;
	private double skill;     // 0..1 from the bot's tier in this kit: HT1 is 1
	private int maxHits;
	private double inner;

	/** 0 to 1 from this bot's MCTiers points in the kit being played (log scale: 1 point is 0, 60 points is 1). */
	static double skillOf(Ctx c) {
		var m = com.relay.pvpchamps.MatchManager.current();
		if (m == null || m.kit == null) return 0.5;
		String tier = com.relay.pvpchamps.Tiers.tier(c.bot.getGameProfile().name(), m.kit);
		return Math.log(Math.max(1, com.relay.pvpchamps.Tiers.points(tier))) / Math.log(60);
	}

	@Override public String id() { return "reach_combo"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.carriesMace()) return MoveEngine.reject(id(), "mace_kit");
		if (c.dist < 2.6 || c.dist > 3.3) return MoveEngine.reject(id(), "range");
		if (c.target.isBlocking()) return MoveEngine.reject(id(), "shield_up");
		if (c.bot.getAttackStrengthScale(0.5f) < 0.9f) return MoveEngine.reject(id(), "not_charged");
		if (c.bot.hurtTime > 0) return MoveEngine.reject(id(), "being_hit");
		skill = skillOf(c);
		maxHits = 3 + (int) Math.round(5 * skill);      // 3 hits for a weak tier, 8 for HT1
		inner = 2.7 + 0.28 * skill;                      // the better the tier, the closer to the very edge of reach it holds
		t = 0;
		hits = 0;
		tapLeft = 0;
		lastHurt = 0;
		dir = c.st.rng.nextBoolean() ? 1f : -1f;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++t > 80 || hits >= maxHits || !c.target.isAlive() || c.dist > 6.5) return false;
		if (c.bot.hurtTime > 8 || c.target.isBlocking()) return false; // they got a hit in, or they blocked: the combo is over
		c.face(c.target.getEyePosition().subtract(0, 0.3, 0));
		// Keep to the ring: in when too far, back off when too close, strafe either way.
		float fwd = c.dist > 3.05 ? 1f : c.dist < inner ? -1f : 0.2f;
		boolean tap = tapLeft > 0;
		if (tap) tapLeft--;
		c.run(fwd, !tap && fwd > 0);
		c.pack.setStrafing(dir * 0.8f);
		boolean ready = c.bot.getAttackStrengthScale(0.5f) >= 0.95f;
		// Top tiers time the swing on the way down from a hop, which is a critical hit.
		boolean crit = skill < 0.8 || c.bot.fallDistance > 0.05f || !c.bot.onGround() && c.bot.getDeltaMovement().y < 0;
		if (skill >= 0.8 && c.bot.onGround() && ready && c.dist <= 3.4) c.jumpOnce();
		if (ready && crit && c.dist <= 3.0 && c.dist >= 2.0) {
			c.attackOnce();
			hits++;
			tapLeft = 2; // w-tap: drop the sprint so the next hit launches them
			dir = -dir;  // swap the strafe side after every hit
		}
		if (c.bot.onGround() && c.dist > 3.4 && c.st.rng.nextInt(6) == 0) c.jumpOnce();
		return true;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stopMovement();
		c.st.setCooldown(id(), 10 - (int) Math.round(8 * skill)); // HT1 chains the next combo almost at once
	}
}
