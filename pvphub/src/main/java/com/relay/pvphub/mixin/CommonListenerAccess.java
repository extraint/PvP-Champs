package com.relay.pvphub.mixin;

import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerCommonPacketListenerImpl.class)
public interface CommonListenerAccess {
	@Accessor("latency")
	void pvphub$setLatency(int latency);
}
