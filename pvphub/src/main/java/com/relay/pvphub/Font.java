package com.relay.pvphub;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Big block letters for signs: a 5 by 7 pixel font, any scale, with an optional dark outline behind. */
final class Font {
	private Font() {}

	private static final Map<Character, String[]> GLYPHS = Map.ofEntries(
		Map.entry('A', new String[] {".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
		Map.entry('C', new String[] {".####", "#....", "#....", "#....", "#....", "#....", ".####"}),
		Map.entry('E', new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#####"}),
		Map.entry('F', new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#...."}),
		Map.entry('H', new String[] {"#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
		Map.entry('I', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"}),
		Map.entry('K', new String[] {"#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"}),
		Map.entry('M', new String[] {"#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"}),
		Map.entry('N', new String[] {"#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"}),
		Map.entry('O', new String[] {".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
		Map.entry('P', new String[] {"####.", "#...#", "#...#", "####.", "#....", "#....", "#...."}),
		Map.entry('Q', new String[] {".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"}),
		Map.entry('R', new String[] {"####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"}),
		Map.entry('S', new String[] {".####", "#....", "#....", ".###.", "....#", "....#", "####."}),
		Map.entry('T', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}),
		Map.entry('U', new String[] {"#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
		Map.entry('V', new String[] {"#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."}),
		Map.entry('D', new String[] {"####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."}),
		Map.entry('L', new String[] {"#....", "#....", "#....", "#....", "#....", "#....", "#####"}),
		Map.entry('B', new String[] {"####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."}),
		Map.entry('G', new String[] {".####", "#....", "#....", "#.###", "#...#", "#...#", ".###."}),
		Map.entry('W', new String[] {"#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"}),
		Map.entry('Y', new String[] {"#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."}),
		Map.entry(' ', new String[] {".....", ".....", ".....", ".....", ".....", ".....", "....."}));

	/** Width in blocks of {@code text} at {@code scale}. */
	static int width(String text, int scale) { return (text.length() * 6 - 1) * scale; }

	/** The lit pixels of {@code text} as {along, up} block offsets, centred along the text, from its bottom. */
	static java.util.List<int[]> pixels(String text, int scale) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		int start = -width(text, scale) / 2;
		for (int i = 0; i < text.length(); i++) {
			String[] g = GLYPHS.getOrDefault(Character.toUpperCase(text.charAt(i)), GLYPHS.get(' '));
			for (int row = 0; row < 7; row++)
				for (int col = 0; col < 5; col++) {
					if (g[row].charAt(col) != '#') continue;
					for (int sx = 0; sx < scale; sx++)
						for (int sy = 0; sy < scale; sy++) out.add(new int[] {start + (i * 6 + col) * scale + sx, (6 - row) * scale + sy});
				}
		}
		return out;
	}

	/** Writes {@code text} centred on (cx, z) with its bottom at y, running along +x and read from the +z side; the outline goes one block towards -z. */
	static void write(ServerLevel level, String text, int cx, int y, int z, int scale, BlockState face, BlockState back, boolean alongX) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int[] q : pixels(text, scale)) {
			Builder.set(level, p.set(cx + q[0], y + q[1], z), face);
			if (back != null) Builder.set(level, p.set(cx + q[0], y + q[1], z - 1), back);
		}
	}
}
