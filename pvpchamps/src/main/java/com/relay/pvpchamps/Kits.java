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
 * The event's game modes as kits. Each builds a 41-slot array in inventory order: 0-8 hotbar, 9-35 main,
 * 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand. Mode list from PVPHQ/Liquipedia (Sword, Spear & Mace, Diamond SMP, Cart, UHC); exact contents are not published, so items follow the wiki's kit definitions.
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

	public ItemStack[] build(MinecraftServer server) {
		Builder b = new Builder(server);
		switch (this) {
			case SPEAR_MACE -> {
				b.armor(Material.NETHERITE, true);
				b.hotbar(b.weapon(Items.NETHERITE_SPEAR, 3), b.mace(), b.n(Items.WIND_CHARGE, 64), b.n(Items.ENDER_PEARL, 16),
					b.n(Items.GOLDEN_APPLE, 16), b.n(Items.TOTEM_OF_UNDYING, 3), b.n(Items.WATER_BUCKET, 1));
				b.main(b.mace(), b.n(Items.ELYTRA, 1), b.n(Items.FIREWORK_ROCKET, 64), b.n(Items.TOTEM_OF_UNDYING, 1), b.n(Items.ENDER_PEARL, 8));
				b.main(b.heals(4));
				b.offhand(b.n(Items.TOTEM_OF_UNDYING, 1));
			}
			case MACE -> {
				b.armor(Material.NETHERITE, true);
				b.hotbar(b.mace(), b.mace(), b.n(Items.WIND_CHARGE, 64), b.n(Items.ENDER_PEARL, 16), b.n(Items.GOLDEN_APPLE, 16),
					b.n(Items.TOTEM_OF_UNDYING, 3), b.n(Items.WATER_BUCKET, 1));
				b.main(b.n(Items.ELYTRA, 1), b.n(Items.FIREWORK_ROCKET, 64), b.n(Items.TOTEM_OF_UNDYING, 1), b.n(Items.WIND_CHARGE, 64));
				b.main(b.heals(4));
				b.offhand(b.n(Items.TOTEM_OF_UNDYING, 1));
			}
			case CART -> {
				b.armor(Material.NETHERITE, true);
				b.hotbar(b.weapon(Items.NETHERITE_SWORD, 5), b.n(Items.TNT_MINECART, 32), b.n(Items.RAIL, 64), b.n(Items.ACTIVATOR_RAIL, 16),
					b.cartBow(), b.n(Items.ENDER_PEARL, 16), b.n(Items.GOLDEN_APPLE, 16), b.n(Items.TOTEM_OF_UNDYING, 3));
				b.main(b.n(Items.EXPERIENCE_BOTTLE, 64), b.n(Items.ARROW, 64), b.n(Items.TOTEM_OF_UNDYING, 1), b.n(Items.WATER_BUCKET, 1), b.n(Items.TNT_MINECART, 32));
				b.offhand(b.n(Items.SHIELD, 1));
			}
			case NETHERITE_OP -> {
				b.armor(Material.NETHERITE, true);
				b.hotbar(b.weapon(Items.NETHERITE_SWORD, 5), b.potion(Potions.STRONG_HEALING), b.potion(Potions.STRONG_HEALING), b.potion(Potions.STRONG_STRENGTH), b.potion(Potions.STRONG_SWIFTNESS),
					b.n(Items.ENDER_PEARL, 16), b.n(Items.GOLDEN_APPLE, 16), b.n(Items.TOTEM_OF_UNDYING, 2));
				b.main(b.potion(Potions.FIRE_RESISTANCE), b.n(Items.EXPERIENCE_BOTTLE, 64), b.n(Items.TOTEM_OF_UNDYING, 1));
				b.main(b.heals(16)); // 12+ potions marks the kit
				b.offhand(b.n(Items.SHIELD, 1));
			}
			case DIAMOND_SMP -> {
				b.armor(Material.DIAMOND, false);
				b.smp(Items.DIAMOND_SWORD);
			}
			case NETHERITE_SMP -> {
				b.armor(Material.NETHERITE, false);
				b.smp(Items.NETHERITE_SWORD);
			}
		}
		return b.stacks;
	}

	enum Material {
		NETHERITE(Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET),
		DIAMOND(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET);

		final Item[] pieces;

		Material(Item... pieces) {
			this.pieces = pieces;
		}
	}

	private static final class Builder {
		final MinecraftServer server;
		final ItemStack[] stacks = new ItemStack[41];
		int hot, main = 9;

		Builder(MinecraftServer server) {
			this.server = server;
			java.util.Arrays.fill(stacks, ItemStack.EMPTY);
		}

		ItemStack n(Item i, int count) { return new ItemStack(i, count); }

		Holder<Enchantment> ench(ResourceKey<Enchantment> key) {
			return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		}

		ItemStack weapon(Item item, int sharpness) {
			ItemStack s = new ItemStack(item);
			s.enchant(ench(Enchantments.SHARPNESS), sharpness);
			s.enchant(ench(Enchantments.UNBREAKING), 3);
			s.enchant(ench(Enchantments.MENDING), 1);
			if (item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD) s.enchant(ench(Enchantments.SWEEPING_EDGE), 3);
			return s;
		}

		/** The Cart kit's bow: Flame lights the TNT minecarts. */
		ItemStack cartBow() {
			ItemStack s = new ItemStack(Items.BOW);
			s.enchant(ench(Enchantments.FLAME), 1);
			s.enchant(ench(Enchantments.POWER), 5);
			s.enchant(ench(Enchantments.UNBREAKING), 3);
			s.enchant(ench(Enchantments.MENDING), 1);
			return s;
		}

		ItemStack bow() {
			ItemStack s = new ItemStack(Items.BOW);
			s.enchant(ench(Enchantments.POWER), 3);
			return s;
		}

		ItemStack mace() {
			ItemStack s = new ItemStack(Items.MACE);
			s.enchant(ench(Enchantments.DENSITY), 5);
			s.enchant(ench(Enchantments.UNBREAKING), 3);
			s.enchant(ench(Enchantments.MENDING), 1);
			return s;
		}

		ItemStack potion(Holder<net.minecraft.world.item.alchemy.Potion> p) {
			return PotionContents.createItemStack(Items.SPLASH_POTION, p);
		}

		/** Potions do not stack, so a "pack" is one stack per potion. */
		ItemStack[] heals(int count) {
			ItemStack[] out = new ItemStack[count];
			for (int i = 0; i < count; i++) out[i] = potion(Potions.STRONG_HEALING);
			return out;
		}

		void armor(Material m, boolean full) {
			for (int i = 0; i < 4; i++) {
				ItemStack s = new ItemStack(m.pieces[i]);
				s.enchant(ench(Enchantments.PROTECTION), 4);
				s.enchant(ench(Enchantments.UNBREAKING), 3);
				s.enchant(ench(Enchantments.MENDING), 1);
				if (i == 0) s.enchant(ench(Enchantments.FEATHER_FALLING), 4);
				stacks[36 + i] = s;
			}
		}

		void hotbar(ItemStack... items) { for (ItemStack s : items) if (hot < 9) stacks[hot++] = s; }

		void main(ItemStack... items) { for (ItemStack s : items) if (main < 36) stacks[main++] = s; }

		void offhand(ItemStack s) { stacks[40] = s; }

		void smp(Item sword) {
			ItemStack blade = weapon(sword, 5);
			hotbar(blade, n(Items.COBWEB, 16), n(Items.WATER_BUCKET, 2), n(Items.OAK_LOG, 32), n(Items.GOLDEN_APPLE, 16),
				n(Items.ENDER_PEARL, 8), n(Items.EXPERIENCE_BOTTLE, 64), n(Items.TOTEM_OF_UNDYING, 2));
			main(n(Items.BOW, 1), n(Items.ARROW, 64), n(Items.COBWEB, 16), n(Items.TOTEM_OF_UNDYING, 1), n(Items.EXPERIENCE_BOTTLE, 64));
			offhand(n(Items.SHIELD, 1));
		}
	}

	public static List<String> ids() {
		List<String> out = new ArrayList<>();
		for (Kits k : values()) out.add(k.id);
		return out;
	}
}
