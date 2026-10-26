package com.relay.pvpchamps.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerCommonPacketListenerImpl.class)
public interface CommonListenerAccess {
	@Accessor("connection")
	Connection pvpchamps$connection();

	@Accessor("latency")
	void pvpchamps$setLatency(int latency);
}
