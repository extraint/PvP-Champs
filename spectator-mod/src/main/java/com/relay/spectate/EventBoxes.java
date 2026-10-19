package com.relay.spectate;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Broadcast furniture from the PvP Champs layout: a round label bar bottom-left ("Main Event - Upper Round 1")
 * and an event box top-right. Both show only once set with /spectate round and /spectate event.
 */
public final class EventBoxes implements HudElement {
	private static final int GOLD = 0xFFD9A441, PANEL = 0xE8101010, WHITE = 0xFFFFFFFF, DARK = 0xFF1A1208;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker tracker) {
		if (!RelayClient.hudVisible()) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();

		String round = RelayClient.roundLabel();
		if (round != null) {
			int w = Math.min(font.width(round) + 14, W / 2);
			float sc = Math.min(1f, (w - 14f) / font.width(round));
			g.fill(4, H - 22, 4 + w, H - 4, GOLD);
			g.fill(5, H - 21, 3 + w, H - 5, 0xFF8A5A1E);
			text(g, font, round, 11, H - 17 + (1 - sc) * 3, sc, WHITE);
		}

		String event = RelayClient.eventLabel();
		if (event != null) {
			int barRight = (W + TopBar.barWidth(W)) / 2;
			int x1 = barRight + 6, x2 = W - 4;
			if (x2 - x1 < 50) return; // no room beside the bar
			g.fill(x1 - 2, 1, x2 + 2, 39, GOLD);
			g.fill(x1, 3, x2, 37, PANEL);
			float sc = Math.min(1.2f, (x2 - x1 - 10f) / Math.max(1, font.width(event)));
			text(g, font, event, x1 + ((x2 - x1) - font.width(event) * sc) / 2f, 20 - 4.5f * sc, sc, WHITE);
		}
	}

	private static void text(GuiGraphicsExtractor g, Font f, String s, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(f, s, 0, 0, color, true);
		g.pose().popMatrix();
	}
}
