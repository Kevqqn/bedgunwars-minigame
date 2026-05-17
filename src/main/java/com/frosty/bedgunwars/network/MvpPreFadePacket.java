package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class MvpPreFadePacket {

//    public final UUID winnerUUID;
//    public final String winnerName;
//
//    public MvpPreFadePacket(UUID winnerUUID, String winnerName) {
//        this.winnerUUID = winnerUUID;
//        this.winnerName = winnerName;
//    }

    public static void encode(MvpPreFadePacket pkt, FriendlyByteBuf buf) {}
    public static MvpPreFadePacket decode(FriendlyByteBuf buf) { return new MvpPreFadePacket(); }
    public MvpPreFadePacket() {}

    public static void handle(MvpPreFadePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.frosty.bedgunwars.client.MvpCutsceneClient.startPreFade();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}