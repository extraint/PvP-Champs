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

	@SuppressWarnings("unchecked")
	public ItemStack[] build(MinecraftServer server) {
		B b = new B(server);
		switch (this) {
			case SWORD -> {
				// Sword PvP: diamond sword and diamond armor, Protection IV / Unbreaking III.
				b.set(0, b.e(Items.DIAMOND_SWORD, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.armor(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET,
					new int[][] {{4, 3}}, false);
			}
			case NETHERITE_OP -> {
				// OP PvP: the sword variant with an axe, food, shield and potions, in netherite.
				b.set(0, b.e(Items.NETHERITE_SWORD, E.SHARPNESS, 5, E.UNBREAKING, 3, E.MENDING, 1));
				b.set(1, b.e(Items.NETHERITE_AXE, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.set(2, b.potion(Potions.STRONG_STRENGTH));
				b.set(3, b.potion(Potions.STRONG_SWIFTNESS));
				b.set(4, b.n(Items.ENDER_PEARL, 16));
				b.set(5, b.n(Items.GOLDEN_APPLE, 32));
				b.set(6, b.n(Items.COOKED_BEEF, 64));
				b.set(7, b.n(Items.TOTEM_OF_UNDYING, 1));
				b.set(8, b.n(Items.EXPERIENCE_BOTTLE, 64));
				b.fill(9, b.heals(Potions.STRONG_HEALING, 14));
				b.armor(Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET,
					new int[][] {{4, 3}}, true);
				b.shield();
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
				b.armor(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET,
					new int[][] {{2, 0}}, false);
				b.shield();
			}
			case CART -> {
				b.set(0, b.e(Items.DIAMOND_SWORD, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.set(1, b.e(Items.DIAMOND_AXE, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.set(2, b.e(Items.BOW, E.POWER, 5, E.UNBREAKING, 3, E.FLAME, 1));
				b.set(3, b.n(Items.TNT_MINECART, 16));
				b.set(4, b.n(Items.RAIL, 64));
				b.set(5, b.n(Items.ENDER_PEARL, 16));
				b.set(6, b.n(Items.GOLDEN_APPLE, 16));
				b.set(7, b.n(Items.TOTEM_OF_UNDYING, 1));
				b.set(8, b.n(Items.OAK_LOG, 64));
				b.set(9, b.e(Items.DIAMOND_PICKAXE, E.EFFICIENCY, 5, E.UNBREAKING, 3));
				b.set(10, b.n(Items.ARROW, 64));
				b.set(11, b.n(Items.COOKED_BEEF, 32));
				b.set(12, b.n(Items.TNT_MINECART, 16));
				b.set(13, b.n(Items.TOTEM_OF_UNDYING, 1));
				// Helmet, chestplate and boots Protection IV; leggings Blast Protection IV.
				b.set(36, b.e(Items.DIAMOND_BOOTS, E.PROTECTION, 4, E.FEATHER_FALLING, 4, E.UNBREAKING, 3));
				b.set(37, b.e(Items.DIAMOND_LEGGINGS, E.BLAST_PROTECTION, 4, E.UNBREAKING, 3));
				b.set(38, b.e(Items.DIAMOND_CHESTPLATE, E.PROTECTION, 4, E.UNBREAKING, 3));
				b.set(39, b.e(Items.DIAMOND_HELMET, E.PROTECTION, 4, E.UNBREAKING, 3));
				b.shieldPlain(E.UNBREAKING, 3);
			}
			case DIAMOND_SMP, NETHERITE_SMP -> {
				boolean neth = this == NETHERITE_SMP;
				Item sword = neth ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD, axe = neth ? Items.NETHERITE_AXE : Items.DIAMOND_AXE;
				Object[] swordEnch = neth
					? new Object[] {E.SHARPNESS, 5, E.SWEEPING_EDGE, 3, E.UNBREAKING, 3, E.FIRE_ASPECT, 2, E.KNOCKBACK, 1}
					: new Object[] {E.SHARPNESS, 5, E.SWEEPING_EDGE, 3, E.UNBREAKING, 3, E.FIRE_ASPECT, 2};
				b.set(0, b.e(sword, swordEnch));
				b.set(1, b.e(axe, E.SHARPNESS, 5, E.UNBREAKING, 3));
				b.set(2, b.e(Items.CROSSBOW, E.QUICK_CHARGE, 3, E.UNBREAKING, 3, E.MULTISHOT, 1));
				b.set(3, b.e(Items.BOW, E.POWER, 5, E.UNBREAKING, 3, E.PUNCH, 2, E.FLAME, 1));
				b.set(4, b.n(Items.ENDER_PEARL, 16));
				b.set(5, b.n(Items.GOLDEN_APPLE, 16));
				b.set(6, b.potion(Potions.STRONG_STRENGTH));
				b.set(7, b.potion(Potions.STRONG_SWIFTNESS));
				b.set(8, b.n(Items.EXPERIENCE_BOTTLE, 64));
				b.set(9, b.potion(Potions.FIRE_RESISTANCE));
				b.set(10, b.tippedHarming(64));
				b.set(11, b.n(Items.TOTEM_OF_UNDYING, 1));
				b.set(12, b.potion(Potions.STRONG_STRENGTH));
				b.set(13, b.potion(Potions.STRONG_SWIFTNESS));
				Item[] a = neth
					? new Item[] {Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET}
					: new Item[] {Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET};
				b.set(36, b.e(a[0], E.PROTECTION, 4, E.FEATHER_FALLING, 4, E.DEPTH_STRIDER, 3, E.UNBREAKING, 3, E.MENDING, 1));
				b.set(37, b.e(a[1], E.PROTECTION, 4, E.UNBREAKING, 3, E.MENDING, 1));
				b.set(38, b.e(a[2], E.PROTECTION, 4, E.UNBREAKING, 3, E.MENDING, 1));
				b.set(39, b.e(a[3], E.PROTECTION, 4, E.RESPIRATION, 3, E.AQUA_AFFINITY, 1, E.UNBREAKING, 3, E.MENDING, 1));
				b.shield();
			}
			case MACE, SPEAR_MACE -> {
				boolean spear = this == SPEAR_MACE;
				int i = 0;
				b.set(i++, b.e(Items.NETHERITE_SWORD, E.SHARPNESS, 5, E.SWEEPING_EDGE, 3, E.UNBREAKING, 3, E.KNOCKBACK, 1));
				b.set(i++, b.e(Items.NETHERITE_AXE, E.SHARPNESS, 5, E.UNBREAKING, 3));
				if (spear) b.set(i++, b.e(Items.NETHERITE_SPEAR, E.SHARPNESS, 5, E.LUNGE, 3, E.UNBREAKING, 3));
				b.set(i++, b.e(Items.MACE, E.DENSITY, 5, E.UNBREAKING, 3, E.WIND_BURST, 1));
				b.set(i++, b.e(Items.MACE, E.BREACH, 4, E.UNBREAKING, 3));
				b.set(i++, b.n(Items.WIND_CHARGE, 64));
				b.set(i++, b.n(Items.ENDER_PEARL, 16));
				b.set(i++, b.n(Items.GOLDEN_APPLE, 16));
				if (i < 9) b.set(i++, b.n(Items.TOTEM_OF_UNDYING, 1));
				b.set(9, b.n(Items.EXPERIENCE_BOTTLE, 64));
				b.set(10, b.n(Items.TOTEM_OF_UNDYING, 1));
				b.set(11, b.n(Items.WIND_CHARGE, 64));
				b.set(36, b.e(Items.NETHERITE_BOOTS, E.PROTECTION, 4, E.FEATHER_FALLING, 4, E.UNBREAKING, 3, E.MENDING, 1));
				b.set(37, b.e(Items.NETHERITE_LEGGINGS, E.PROTECTION, 4, E.UNBREAKING, 3, E.MENDING, 1));
				b.set(38, b.e(Items.NETHERITE_CHESTPLATE, E.PROTECTION, 4, E.UNBREAKING, 3, E.MENDING, 1));
				b.set(39, b.e(Items.NETHERITE_HELMET, E.PROTECTION, 4, E.UNBREAKING, 3, E.MENDING, 1));
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

		B(MinecraftServer server) {
			this.server = server;
			java.util.Arrays.fill(stacks, ItemStack.EMPTY);
		}

		void set(int slot, ItemStack s) { stacks[slot] = s; }

		void fill(int from, ItemStack[] items) { for (int i = 0; i < items.length && from + i < 36; i++) stacks[from + i] = items[i]; }

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

		/** Potions do not stack, so a pack is one stack per potion. */
		ItemStack[] heals(Holder<net.minecraft.world.item.alchemy.Potion> p, int count) {
			ItemStack[] out = new ItemStack[count];
			for (int i = 0; i < count; i++) out[i] = potion(p);
			return out;
		}

		void armor(Item boots, Item legs, Item chest, Item helm, int[][] prot, boolean mending) {
			Item[] pieces = {boots, legs, chest, helm};
			for (int i = 0; i < 4; i++) {
				ItemStack s = new ItemStack(pieces[i]);
				ResourceKey<Enchantment> p = E.PROTECTION;
				Holder.Reference<Enchantment> ph = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(p);
				if (prot[0][0] > 0) s.enchant(ph, prot[0][0]);
				if (prot[0][1] > 0) s.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(E.UNBREAKING), prot[0][1]);
				if (mending) s.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(E.MENDING), 1);
				stacks[36 + i] = s;
			}
		}

		void shield() { stacks[40] = e(Items.SHIELD, E.UNBREAKING, 3, E.MENDING, 1); }

		void shieldPlain(Object... pairs) { stacks[40] = e(Items.SHIELD, pairs); }
	}
}
