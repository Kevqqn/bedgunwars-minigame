package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.minimap.MinimapClientProxy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MinimapStopPacket {

    public static MinimapStopPacket decode(FriendlyByteBuf buf) { return new MinimapStopPacket(); }
    public static void encode(MinimapStopPacket pkt, FriendlyByteBuf buf) {}

    public static void handle(MinimapStopPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(MinimapClientProxy::onGameEnd);
        ctx.get().setPacketHandled(true);
    }
}