package com.relay.pvpchamps.moves;

import net.minecraft.world.item.Items;
import vexbot.fakeplayer.bot.BotPlayerActionPack;

/**
 * Combat Master heals at 4 hearts: at 8 health or less, with a golden apple in the bot's inventory, it stops and
 * eats. Vexbot's own heal threshold is 5 hearts, so this runs first. The eat takes about 1.6 seconds.
 */
public final class HealAtFourHearts implements Move {
	private int ticks;

	@Override public String id() { return "heal_4_hearts"; }

	@Override
	public boolean start(Ctx c) {
		if (c.st.cooldown(id()) > 0) return MoveEngine.reject(id(), "cooldown");
		if (c.bot.getHealth() > 8f) return MoveEngine.reject(id(), "healthy");
		if (c.count(Items.GOLDEN_APPLE) == 0) return MoveEngine.reject(id(), "no_apple");
		if (c.bot.isUsingItem()) return MoveEngine.reject(id(), "busy");
		ticks = 0;
		return true;
	}

	@Override
	public boolean tick(Ctx c) {
		if (++ticks > 40) return false;
		if (!c.hold(Items.GOLDEN_APPLE)) return false;
		c.pack.stopMovement();
		c.pack.start(BotPlayerActionPack.ActionType.USE, BotPlayerActionPack.Action.continuous());
		return c.bot.isUsingItem() && c.bot.getHealth() < 20f;
	}

	@Override
	public void end(Ctx c) {
		c.pack.stop(BotPlayerActionPack.ActionType.USE);
		c.restoreSlot();
		c.st.setCooldown(id(), 30);
	}
}
