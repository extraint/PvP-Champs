package com.relay.pvphub;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Block-placing helpers for the lobby and the arenas. */
final class Builder {
	private Builder() {}

	static BlockState b(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	/** No neighbour updates and no drops: this is bulk building. */
	static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 2 | 16);
	}

	static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(level, p.set(x, y, z), state);
	}
}
