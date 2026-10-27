package com.relay.pvphub;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Floating text (text displays) and the lobby NPCs (mannequins), all tagged "pvphub" so they can be cleared and rebuilt. */
final class Holo {
	private Holo() {}

	private static final Map<String, double[]> WHERE = new HashMap<>();

	static void command(MinecraftServer server, String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
	}

	static void clearAll(MinecraftServer server) {
		command(server, "kill @e[tag=pvphub]");
		WHERE.clear();
	}

	/** A text display that always faces the viewer. */
	static void text(MinecraftServer server, String id, double x, double y, double z, Component text) {
		ServerLevel level = Hub.world(server);
		Display.TextDisplay d = find(level, id, x, y, z);
		if (d == null) {
			command(server, String.format(java.util.Locale.ROOT, "summon minecraft:text_display %.2f %.2f %.2f {Tags:[\"pvphub\",\"pvphub_holo_%s\"],billboard:\"center\",background:0,shadow:1b,line_width:600,view_range:3.0f}", x, y, z, id));
			d = find(level, id, x, y, z);
			WHERE.put(id, new double[] {x, y, z});
		}
		if (d != null) ((com.relay.pvphub.mixin.TextDisplayAccess) d).pvphub$setText(text);
	}

	/** Updates the text of a display made earlier (does nothing if it is not there). */
	static void update(MinecraftServer server, String id, Component text) {
		double[] w = WHERE.get(id);
		if (w == null) return;
		text(server, id, w[0], w[1], w[2], text);
	}

	private static Display.TextDisplay find(ServerLevel level, String id, double x, double y, double z) {
		String tag = "pvphub_holo_" + id;
		for (Entity e : level.getEntities((Entity) null, new AABB(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1), e -> e.entityTags().contains(tag)))
			if (e instanceof Display.TextDisplay t) return t;
		return null;
	}

	/** A mannequin that opens something when clicked (its action rides in a tag), with its name and description floating above. */
	static void npc(MinecraftServer server, String action, double x, double y, double z, float yaw, String skin, String item, Component... lines) {
		command(server, String.format(java.util.Locale.ROOT,
			"summon minecraft:mannequin %.2f %.2f %.2f {Tags:[\"pvphub\",\"pvphub_npc\",\"npc_%s\"],immovable:1b,hide_description:1b,Invulnerable:1b,Silent:1b,NoGravity:1b,Rotation:[%.1ff,0f],profile:{name:\"%s\"},equipment:{mainhand:{id:\"minecraft:%s\",count:1}}}",
			x, y, z, action, yaw, skin, item));
		Component all = Component.empty();
		for (int i = 0; i < lines.length; i++) { if (i > 0) all = all.copy().append("\n"); all = all.copy().append(lines[i]); }
		text(server, "npc_" + action, x, y + 2.15, z, all);
	}

	static String actionOf(Entity e) {
		for (String t : e.entityTags()) if (t.startsWith("npc_")) return t.substring(4);
		return null;
	}
}
