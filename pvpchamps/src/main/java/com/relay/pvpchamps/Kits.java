package com.relay.pvpchamps;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The event's game modes as kits. The contents live in {@code pvpchamps_kits.json} (inside the jar), one entry per
 * kit: every hotbar/inventory slot with its item, count, enchantments, potion and shulker contents, plus the worn
 * armour and the offhand. Each kit builds a 41-slot array in inventory order: 0-8 hotbar, 9-35 main, 36 boots,
 * 37 leggings, 38 chestplate, 39 helmet, 40 offhand.
 */
public enum Kits {
	SWORD("sword", "Sword"),
	UHC("uhc", "UHC"),
	SPEAR_MACE("spear_mace", "Spear & Mace"),
	CRYSTAL("crystal", "Crystal"),
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

	private static JsonObject spec;

	private static synchronized JsonObject spec() {
		if (spec == null) {
			try (InputStream in = Kits.class.getResourceAsStream("/pvpchamps_kits.json")) {
				if (in == null) throw new IllegalStateException("pvpchamps_kits.json is missing from the jar");
				spec = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			} catch (IOException e) {
				throw new IllegalStateException("could not read pvpchamps_kits.json", e);
			}
		}
		return spec;
	}

	public ItemStack[] build(MinecraftServer server) {
		ItemStack[] out = new ItemStack[41];
		java.util.Arrays.fill(out, ItemStack.EMPTY);
		JsonObject kit = spec().getAsJsonObject(id);
		for (JsonElement e : kit.getAsJsonArray("slots")) {
			JsonObject o = e.getAsJsonObject();
			int slot = o.get("slot").getAsInt();
			if (slot >= 0 && slot < 36) out[slot] = stack(server, o);
		}
		JsonObject eq = kit.getAsJsonObject("equipment");
		String[] names = {"feet", "legs", "chest", "head", "offhand"};
		for (int i = 0; i < names.length; i++) {
			if (eq.has(names[i])) out[36 + i] = stack(server, eq.getAsJsonObject(names[i]));
		}
		return out;
	}

	private static ItemStack stack(MinecraftServer server, JsonObject o) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(o.get("item").getAsString()));
		int count = o.has("count") ? o.get("count").getAsInt() : 1;
		ItemStack s;
		if (o.has("potion")) {
			Holder<Potion> potion = server.registryAccess().lookupOrThrow(Registries.POTION)
				.getOrThrow(ResourceKey.create(Registries.POTION, Identifier.withDefaultNamespace(o.get("potion").getAsString())));
			s = PotionContents.createItemStack(item, potion);
			s.setCount(count);
		} else if (o.has("effects")) { // custom effects, e.g. a tipped arrow that gives 30 seconds (the arrow shows 1/8 of the ticks given)
			java.util.List<MobEffectInstance> list = new ArrayList<>();
			for (JsonElement ee : o.getAsJsonArray("effects")) {
				JsonObject eo = ee.getAsJsonObject();
				Holder<MobEffect> h = server.registryAccess().lookupOrThrow(Registries.MOB_EFFECT)
					.getOrThrow(ResourceKey.create(Registries.MOB_EFFECT, Identifier.withDefaultNamespace(eo.get("id").getAsString())));
				list.add(new MobEffectInstance(h, eo.get("ticks").getAsInt(), eo.has("amplifier") ? eo.get("amplifier").getAsInt() : 0));
			}
			s = new ItemStack(item, count);
			s.set(DataComponents.POTION_CONTENTS, new PotionContents(java.util.Optional.empty(), java.util.Optional.empty(), list, java.util.Optional.empty()));
		} else {
			s = new ItemStack(item, count);
		}
		if (o.has("name")) s.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(o.get("name").getAsString()));
		if (o.has("enchants")) {
			for (var en : o.getAsJsonObject("enchants").entrySet()) {
				Holder<Enchantment> h = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(en.getKey())));
				s.enchant(h, en.getValue().getAsInt());
			}
		}
		if (o.has("container")) {
			List<ItemStack> inside = new ArrayList<>();
			JsonArray arr = o.getAsJsonArray("container");
			for (JsonElement c : arr) inside.add(stack(server, c.getAsJsonObject()));
			s.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
		}
		return s;
	}
}
