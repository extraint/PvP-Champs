package com.relay.pvpchamps.moves;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import vexbot.fakeplayer.bot.BotPlayerActionPack;

/** Everything a move needs for one tick, plus small helpers for driving the bot like a player would. */
public final class Ctx {
	public final ServerPlayer bot;
	public final LivingEntity target;
	public final BotPlayerActionPack pack;
	public final MoveEngine.State st;
	public final double dist;

	Ctx(ServerPlayer bot, LivingEntity target, BotPlayerActionPack pack, MoveEngine.State st) {
		this.bot = bot;
		this.target = target;
		this.pack = pack;
		this.st = st;
		this.dist = bot.position().distanceTo(target.position());
	}

	public Vec3 targetEye() { return target.getEyePosition(); }

	/** Inventory slot holding this item (hotbar first), or -1. */
	public int find(Item item) {
		var inv = bot.getInventory();
		for (int i = 0; i < 36; i++) if (inv.getItem(i).getItem() == item) return i;
		return -1;
	}

	public int count(Item item) {
		int n = 0;
		var inv = bot.getInventory();
		for (int i = 0; i < 41; i++) if (inv.getItem(i).getItem() == item) n += inv.getItem(i).getCount();
		return n;
	}

	/**
	 * Puts the item in hand: selects it if it is on the hotbar, otherwise swaps it into hotbar slot 8 first.
	 * Remembers the slot that was selected so {@link #restoreSlot()} can go back to the weapon.
	 */
	public boolean hold(Item item) {
		int slot = find(item);
		if (slot < 0) return false;
		var inv = bot.getInventory();
		if (st.prevSlot < 0) st.prevSlot = inv.getSelectedSlot();
		if (slot > 8) {
			ItemStack moved = inv.getItem(slot), old = inv.getItem(8);
			inv.setItem(8, moved);
			inv.setItem(slot, old);
			slot = 8;
		}
		pack.setSlot(slot + 1); // the action pack counts hotbar slots from 1
		return true;
	}

	public void restoreSlot() {
		if (st.prevSlot >= 0) pack.setSlot(st.prevSlot + 1);
		st.prevSlot = -1;
	}

	public void face(Vec3 point) { pack.lookAt(point); }

	public void useOnce() { pack.start(BotPlayerActionPack.ActionType.USE, BotPlayerActionPack.Action.once()); }

	public void attackOnce() { pack.start(BotPlayerActionPack.ActionType.ATTACK, BotPlayerActionPack.Action.once()); }

	public void jumpOnce() { pack.start(BotPlayerActionPack.ActionType.JUMP, BotPlayerActionPack.Action.once()); }

	public void run(float forward, boolean sprint) {
		pack.setForward(forward);
		pack.setSprinting(sprint);
	}

	/**
	 * The yaw to run along to get away from {@code from}: straight away if that is open, otherwise the nearest
	 * direction with no wall in the way and no drop at the end of it. Tries wider angles one side then the other.
	 */
	public float openYawAwayFrom(Vec3 from) {
		Vec3 away = bot.position().subtract(from);
		float base = (float) Math.toDegrees(Math.atan2(-away.x, away.z));
		int[] offsets = {0, 25, -25, 50, -50, 80, -80, 110, -110, 145, -145, 180};
		float best = base;
		double bestScore = -1;
		for (int off : offsets) {
			float yaw = base + off;
			double rad = Math.toRadians(yaw);
			Vec3 dir = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
			double room = openRun(dir, 7.0);
			// Prefer open room, and among equally open directions the one closest to straight away.
			double score = room - Math.abs(off) * 0.01;
			if (room >= 7.0) return yaw;
			if (score > bestScore) { bestScore = score; best = yaw; }
		}
		return best;
	}

	/** How many blocks (up to max) the bot could run along dir before a wall or a drop of more than 2 blocks. */
	private double openRun(Vec3 dir, double max) {
		var level = bot.level();
		for (double d = 1.0; d <= max; d += 1.0) {
			Vec3 foot = bot.position().add(dir.scale(d));
			var lo = new net.minecraft.core.BlockPos((int) Math.floor(foot.x), (int) Math.floor(bot.getY() + 0.1), (int) Math.floor(foot.z));
			boolean wall = !level.getBlockState(lo).getCollisionShape(level, lo).isEmpty()
				&& !level.getBlockState(lo.above()).getCollisionShape(level, lo.above()).isEmpty();
			if (wall) return d - 1.0;
			boolean ground = false;
			for (int dy = 0; dy <= 2 && !ground; dy++) {
				var b = lo.below(dy);
				ground = !level.getBlockState(b).getCollisionShape(level, b).isEmpty();
			}
			if (!ground) return d - 1.0; // a hole or cliff
		}
		return max;
	}

	/** Backpedals from the opponent while facing them; sidesteps instead when a wall or drop is right behind. */
	public void runBack(boolean sprint) {
		Vec3 away = bot.position().subtract(target.position()).multiply(1, 0, 1);
		if (away.lengthSqr() < 1e-4) { run(-1f, sprint); return; }
		Vec3 dir = away.normalize();
		if (openRun(dir, 3.0) >= 2.0) { run(-1f, sprint); return; }
		Vec3 left = new Vec3(dir.z, 0, -dir.x), right = left.scale(-1);
		boolean goRight = openRun(right, 5.0) >= openRun(left, 5.0);
		run(0f, sprint);
		pack.setStrafing(goRight ? -1f : 1f);
	}

	/** True for mace kits: the bot's own mace logic (wind charge, dive, smash) must run, not a ground melee loop. */
	public boolean carriesMace() { return count(net.minecraft.world.item.Items.MACE) > 0; }

	public float hp() { return bot.getHealth() + bot.getAbsorptionAmount(); }

	/** Average remaining durability of the armour being worn, 0 to 1. */
	public double armourFraction() {
		double sum = 0;
		int n = 0;
		for (var slot : new net.minecraft.world.entity.EquipmentSlot[] { net.minecraft.world.entity.EquipmentSlot.HEAD,
				net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
				net.minecraft.world.entity.EquipmentSlot.FEET }) {
			ItemStack s = bot.getItemBySlot(slot);
			if (s.isEmpty() || !s.isDamageableItem()) continue;
			sum += 1.0 - (double) s.getDamageValue() / s.getMaxDamage();
			n++;
		}
		return n == 0 ? 1 : sum / n;
	}
}
