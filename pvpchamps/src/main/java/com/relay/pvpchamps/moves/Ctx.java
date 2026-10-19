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
