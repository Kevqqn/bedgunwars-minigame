package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.client.MvpCutsceneClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MvpEndPacket {

    public static void encode(MvpEndPacket p, FriendlyByteBuf buf) {}

    public static MvpEndPacket decode(FriendlyByteBuf buf) {
        return new MvpEndPacket();
    }

    public static void handle(MvpEndPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                com.frosty.bedgunwars.client.MvpCutsceneClient.end();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}