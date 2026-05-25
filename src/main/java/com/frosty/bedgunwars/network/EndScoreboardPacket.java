package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.client.EndScoreboardClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EndScoreboardPacket {

    // Camera pos 10 blocks above trapped chest
    public final double camX, camY, camZ;
    // Beacon pos camera faces this
    public final double beaconX, beaconY, beaconZ;
    // Piggybacking the existing TabStatsPacket data
    public final TabStatsPacket stats;

    public EndScoreboardPacket(double camX, double camY, double camZ,
                               double beaconX, double beaconY, double beaconZ,
                               TabStatsPacket stats) {
        this.camX = camX; this.camY = camY; this.camZ = camZ;
        this.beaconX = beaconX; this.beaconY = beaconY; this.beaconZ = beaconZ;
        this.stats = stats;
    }

    public static void encode(EndScoreboardPacket pkt, FriendlyByteBuf buf) {
        buf.writeDouble(pkt.camX);
        buf.writeDouble(pkt.camY);
        buf.writeDouble(pkt.camZ);
        buf.writeDouble(pkt.beaconX);
        buf.writeDouble(pkt.beaconY);
        buf.writeDouble(pkt.beaconZ);
        TabStatsPacket.encode(pkt.stats, buf);
    }

    public static EndScoreboardPacket decode(FriendlyByteBuf buf) {
        double camX    = buf.readDouble();
        double camY    = buf.readDouble();
        double camZ    = buf.readDouble();
        double beaconX = buf.readDouble();
        double beaconY = buf.readDouble();
        double beaconZ = buf.readDouble();
        TabStatsPacket stats = TabStatsPacket.decode(buf);
        return new EndScoreboardPacket(camX, camY, camZ, beaconX, beaconY, beaconZ, stats);
    }

    public static void handle(EndScoreboardPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                EndScoreboardClient.start(
                        pkt.camX, pkt.camY, pkt.camZ,
                        pkt.beaconX, pkt.beaconY, pkt.beaconZ,
                        pkt.stats);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}