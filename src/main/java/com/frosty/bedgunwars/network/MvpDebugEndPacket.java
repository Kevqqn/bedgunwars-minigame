package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class MvpDebugEndPacket {
    public static void encode(MvpDebugEndPacket pkt, FriendlyByteBuf buf) {}
    public static MvpDebugEndPacket decode(FriendlyByteBuf buf) { return new MvpDebugEndPacket(); }
    public static void handle(MvpDebugEndPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.frosty.bedgunwars.client.MvpCutsceneClient.clearHoldBlack();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}