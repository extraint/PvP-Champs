package com.relay.pvpchamps.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@link com.relay.pvpchamps.Ping} reach a player's network channel to add latency to it. */
@Mixin(Connection.class)
public interface ConnectionAccess {
	@Accessor("channel")
	Channel pvpchamps$channel();
}
