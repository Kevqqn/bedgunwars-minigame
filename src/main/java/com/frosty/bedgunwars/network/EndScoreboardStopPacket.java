package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class EndScoreboardStopPacket {
    public static void encode(EndScoreboardStopPacket pkt, FriendlyByteBuf buf) {}
    public static EndScoreboardStopPacket decode(FriendlyByteBuf buf) { return new EndScoreboardStopPacket(); }
    public static void handle(EndScoreboardStopPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.frosty.bedgunwars.BedGunWars.LOGGER.info("[Scoreboard] EndScoreboardStopPacket received");
                com.frosty.bedgunwars.client.EndScoreboardClient.end();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}