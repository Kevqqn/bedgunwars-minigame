package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class MvpSkinPrefetchPacket {

    public final UUID winnerUUID;
    public final String winnerName;

    public MvpSkinPrefetchPacket(UUID winnerUUID, String winnerName) {
        this.winnerUUID = winnerUUID;
        this.winnerName = winnerName;
    }

    public static void encode(MvpSkinPrefetchPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.winnerUUID);
        buf.writeUtf(pkt.winnerName);
    }

    public static MvpSkinPrefetchPacket decode(FriendlyByteBuf buf) {
        return new MvpSkinPrefetchPacket(buf.readUUID(), buf.readUtf());
    }

    public static void handle(MvpSkinPrefetchPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.frosty.bedgunwars.client.MvpCutsceneClient.startSkinPrefetch(pkt.winnerUUID, pkt.winnerName);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}