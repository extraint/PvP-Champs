package com.relay.pvpchamps;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The event's game modes as kits, following the Minecraft Wiki's "PvP kit types" (the kits recognised by servers and
 * tier testing): item lists and enchantments are taken from there. Each builds a 41-slot array in inventory order:
 * 0-8 hotbar, 9-35 main, 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand (the shield).
 */
public enum Kits {
	SWORD("sword", "Sword"),
	UHC("uhc", "UHC"),
	SPEAR_MACE("spear_mace", "Spear & Mace"),
	MACE("mace", "Mace"),
	CART("cart", "Cart"),
	NETHERITE_OP("netherite_op", "Netherite OP"),
	DIAMOND_SMP("diamond_smp", "Diamond SMP"),
	NETHERITE_SMP("netherite_smp", "Netherite SMP");

	public final String id, label;

	Kits(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public static Kits byId(String id) {
		for (Kits k : values()) if (k.id.equalsIgnoreCase(id) || k.label.equalsIgnoreCase(id)) return k;
		return null;
	}

	public static List<String> ids() {
		List<String> out = new ArrayList<>();
		for (Kits k : values()) out.add(k.id);
		return out;
	}

	public ItemStack[] build(MinecraftServer server) {
		B b = new B(server);
		switch (this) {
			case SWORD -> {
				// Sword PvP: diamond sword and diamond armor, Protection IV / Unbreaking III.
				b.set(0, b.e(Items.DIAMOND_SWORD, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.armor(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET, false);
			}
			case UHC -> {
				b.set(0, b.e(Items.DIAMOND_SWORD, E.SHARPNESS, 1));
				b.set(1, b.e(Items.DIAMOND_AXE, E.SHARPNESS, 1));
				b.set(2, b.e(Items.BOW, E.POWER, 1));
				b.set(3, b.e(Items.CROSSBOW, E.PIERCING, 1));
				b.set(4, b.n(Items.COBWEB, 8));
				b.set(5, b.n(Items.WATER_BUCKET, 1));
				b.set(6, b.n(Items.LAVA_BUCKET, 1));
				b.set(7, b.n(Items.OAK_LOG, 64));
				b.set(8, b.n(Items.GOLDEN_APPLE, 6));
				b.set(9, b.e(Items.DIAMOND_PICKAXE, E.EFFICIENCY, 5));
				b.set(10, b.n(Items.ARROW, 32));
				b.set(11, b.n(Items.COOKED_BEEF, 32));
				b.set(12, b.n(Items.WATER_BUCKET, 1));
				b.set(13, b.n(Items.LAVA_BUCKET, 1));
				b.armor(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET, false);
				b.shield();
			}
			case CART -> {
				b.add(b.e(Items.DIAMOND_SWORD, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.add(b.e(Items.DIAMOND_AXE, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.add(b.e(Items.BOW, E.POWER, 5, E.UNBREAKING, 3, E.FLAME, 1));
				b.singles(Items.TNT_MINECART, 8);
				b.add(b.n(Items.RAIL, 64));
				b.add(b.n(Items.ENDER_PEARL, 16));
				b.add(b.n(Items.ENDER_PEARL, 16));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.singles(Items.TOTEM_OF_UNDYING, 2);
				b.add(b.n(Items.OAK_LOG, 64));
				b.add(b.n(Items.COOKED_BEEF, 64));
				b.add(b.n(Items.ARROW, 64));
				b.fillPotions(b.nonHealing());
				b.armor(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET, false);
				b.shieldPlain(E.UNBREAKING, 3);
			}
			case NETHERITE_OP -> {
				b.add(b.e(Items.NETHERITE_SWORD, E.SHARPNESS, 5, E.UNBREAKING, 3, E.MENDING, 1));
				b.add(b.e(Items.NETHERITE_AXE, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.add(b.potion(Potions.STRONG_STRENGTH));
				b.add(b.potion(Potions.STRONG_SWIFTNESS));
				b.add(b.n(Items.ENDER_PEARL, 16));
				b.add(b.n(Items.ENDER_PEARL, 16));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.add(b.n(Items.COOKED_BEEF, 64));
				b.singles(Items.TOTEM_OF_UNDYING, 2);
				b.add(b.n(Items.EXPERIENCE_BOTTLE, 64));
				b.placePotions(b.instantHealing(20));
				b.fillPotions(b.nonHealing());
				b.armor(Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET, true);
				b.shield();
			}
			case DIAMOND_SMP -> {
				b.add(b.e(Items.DIAMOND_SWORD, E.SHARPNESS, 5, E.SWEEPING_EDGE, 3, E.UNBREAKING, 3, E.FIRE_ASPECT, 2));
				b.add(b.e(Items.DIAMOND_AXE, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.add(b.e(Items.CROSSBOW, E.QUICK_CHARGE, 3, E.UNBREAKING, 3, E.MULTISHOT, 1));
				b.add(b.e(Items.BOW, E.POWER, 5, E.UNBREAKING, 3, E.PUNCH, 2, E.FLAME, 1));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.add(b.n(Items.EXPERIENCE_BOTTLE, 64));
				b.add(b.tippedHarming(64));
				b.add(b.n(Items.OAK_LOG, 64));
				b.singles(Items.TOTEM_OF_UNDYING, 2);
				b.placePotions(b.instantHealing(4));
				b.fillPotions(b.nonHealing());
				b.armor(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET, true);
				b.shield();
			}
			case NETHERITE_SMP -> {
				b.add(b.e(Items.NETHERITE_SWORD, E.SHARPNESS, 5, E.SWEEPING_EDGE, 3, E.UNBREAKING, 3, E.FIRE_ASPECT, 2, E.KNOCKBACK, 1));
				b.add(b.e(Items.NETHERITE_AXE, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.add(b.e(Items.CROSSBOW, E.QUICK_CHARGE, 3, E.UNBREAKING, 3, E.MULTISHOT, 1));
				b.add(b.e(Items.BOW, E.POWER, 5, E.UNBREAKING, 3, E.PUNCH, 2, E.FLAME, 1));
				b.add(b.n(Items.ENDER_PEARL, 16));
				b.add(b.n(Items.ENDER_PEARL, 16));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.add(b.n(Items.EXPERIENCE_BOTTLE, 64));
				b.add(b.tippedHarming(64));
				b.add(b.n(Items.OAK_LOG, 64));
				b.singles(Items.TOTEM_OF_UNDYING, 2);
				b.fillPotions(b.nonHealing());
				b.armor(Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET, true);
				b.shield();
			}
			case MACE, SPEAR_MACE -> {
				boolean mace = this == MACE;
				b.add(b.e(Items.NETHERITE_SWORD, E.SHARPNESS, 5, E.SWEEPING_EDGE, 3, E.UNBREAKING, 3, E.KNOCKBACK, 1));
				b.add(b.e(Items.NETHERITE_AXE, E.SHARPNESS, 5, E.UNBREAKING, 3));
				if (!mace) b.add(b.e(Items.NETHERITE_SPEAR, E.SHARPNESS, 5, E.LUNGE, 3, E.UNBREAKING, 3));
				b.add(b.e(Items.MACE, E.DENSITY, 5, E.UNBREAKING, 3, E.WIND_BURST, 1));
				b.add(b.e(Items.MACE, E.BREACH, 4, E.UNBREAKING, 3));
				b.add(b.n(Items.WIND_CHARGE, 64));
				b.add(b.n(Items.ENDER_PEARL, 16));
				b.add(b.n(Items.ENDER_PEARL, 16));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.add(b.n(Items.GOLDEN_APPLE, 64));
				b.singles(Items.TOTEM_OF_UNDYING, 2);
				b.add(b.n(Items.EXPERIENCE_BOTTLE, 64));
				if (mace) {
					b.add(b.n(Items.FIREWORK_ROCKET, 64));
				}
				b.fillPotions(b.nonHealing());
				b.armor(Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, mace ? Items.ELYTRA : Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET, true);
				b.shield();
			}
		}
		return b.stacks;
	}

	private static final class E {
		static final ResourceKey<Enchantment> SHARPNESS = Enchantments.SHARPNESS, UNBREAKING = Enchantments.UNBREAKING,
			MENDING = Enchantments.MENDING, PROTECTION = Enchantments.PROTECTION, FEATHER_FALLING = Enchantments.FEATHER_FALLING,
			BLAST_PROTECTION = Enchantments.BLAST_PROTECTION, DEPTH_STRIDER = Enchantments.DEPTH_STRIDER,
			RESPIRATION = Enchantments.RESPIRATION, AQUA_AFFINITY = Enchantments.AQUA_AFFINITY,
			SWEEPING_EDGE = Enchantments.SWEEPING_EDGE, FIRE_ASPECT = Enchantments.FIRE_ASPECT, KNOCKBACK = Enchantments.KNOCKBACK,
			POWER = Enchantments.POWER, PUNCH = Enchantments.PUNCH, FLAME = Enchantments.FLAME,
			QUICK_CHARGE = Enchantments.QUICK_CHARGE, MULTISHOT = Enchantments.MULTISHOT, PIERCING = Enchantments.PIERCING,
			EFFICIENCY = Enchantments.EFFICIENCY, DENSITY = Enchantments.DENSITY, BREACH = Enchantments.BREACH,
			WIND_BURST = Enchantments.WIND_BURST, LUNGE = Enchantments.LUNGE;
	}

	private static final class B {
		final MinecraftServer server;
		final ItemStack[] stacks = new ItemStack[41];
		int next = 0;

		B(MinecraftServer server) {
			this.server = server;
			java.util.Arrays.fill(stacks, ItemStack.EMPTY);
		}

		void set(int slot, ItemStack s) { stacks[slot] = s; }

		/** Puts the item in the next empty slot, hotbar first. */
		void add(ItemStack s) {
			while (next < 36 && !stacks[next].isEmpty()) next++;
			if (next < 36) stacks[next++] = s;
		}

		/** Items that do not stack (totems, minecarts): one per slot. */
		void singles(Item item, int count) {
			for (int i = 0; i < count; i++) add(new ItemStack(item, 1));
		}

		ItemStack n(Item i, int count) { return new ItemStack(i, count); }

		/** An item with enchantments given as alternating key, level. */
		ItemStack e(Item item, Object... pairs) {
			ItemStack s = new ItemStack(item);
			for (int i = 0; i < pairs.length; i += 2) {
				@SuppressWarnings("unchecked") ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) pairs[i];
				Holder<Enchantment> h = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
				s.enchant(h, (Integer) pairs[i + 1]);
			}
			return s;
		}

		ItemStack potion(Holder<net.minecraft.world.item.alchemy.Potion> p) {
			return PotionContents.createItemStack(Items.SPLASH_POTION, p);
		}

		ItemStack tippedHarming(int count) {
			ItemStack s = PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.HARMING);
			s.setCount(count);
			return s;
		}

		/** Instant healing splashes, one per slot since potions do not stack. */
		List<Holder<net.minecraft.world.item.alchemy.Potion>> instantHealing(int count) {
			List<Holder<net.minecraft.world.item.alchemy.Potion>> out = new ArrayList<>();
			for (int i = 0; i < count; i++) out.add(Potions.STRONG_HEALING);
			return out;
		}

		/** Everything the kits use to fill spare slots, with no instant health. */
		List<Holder<net.minecraft.world.item.alchemy.Potion>> nonHealing() {
			return List.of(Potions.STRONG_STRENGTH, Potions.STRONG_SWIFTNESS, Potions.FIRE_RESISTANCE, Potions.STRONG_REGENERATION);
		}

		/** Places each potion once, in the next empty slots. */
		void placePotions(List<Holder<net.minecraft.world.item.alchemy.Potion>> list) {
			for (Holder<net.minecraft.world.item.alchemy.Potion> p : list) add(potion(p));
		}

		/** Fills the empty slots with splash potions, cycling through the list. */
		void fillPotions(List<Holder<net.minecraft.world.item.alchemy.Potion>> list) {
			int i = 0;
			while (next < 36) {
				add(potion(list.get(i % list.size())));
				i++;
			}
		}

		void armor(Item boots, Item legs, Item chest, Item helm, boolean mending) {
			Item[] pieces = {boots, legs, chest, helm};
			for (int i = 0; i < 4; i++) {
				Object[] pairs = mending
					? new Object[] {E.PROTECTION, 4, E.UNBREAKING, 3, E.MENDING, 1}
					: new Object[] {E.PROTECTION, 4, E.UNBREAKING, 3};
				if (i == 0) pairs = concat(pairs, E.FEATHER_FALLING, 4);
				if (pieces[i] == Items.ELYTRA) pairs = new Object[] {E.UNBREAKING, 3, E.MENDING, 1};
				stacks[36 + i] = e(pieces[i], pairs);
			}
		}

		private static Object[] concat(Object[] a, Object... b) {
			Object[] out = java.util.Arrays.copyOf(a, a.length + b.length);
			System.arraycopy(b, 0, out, a.length, b.length);
			return out;
		}

		void shield() { stacks[40] = e(Items.SHIELD, E.UNBREAKING, 3, E.MENDING, 1); }

		void shieldPlain(Object... pairs) { stacks[40] = e(Items.SHIELD, pairs); }
	}
}
