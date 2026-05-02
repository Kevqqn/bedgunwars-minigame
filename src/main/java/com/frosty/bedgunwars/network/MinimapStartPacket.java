package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.minimap.MinimapClientProxy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MinimapStartPacket {

    private final int beaconX;
    private final int beaconZ;
    private final int radius;

    public MinimapStartPacket(int beaconX, int beaconZ, int radius) {
        this.beaconX = beaconX;
        this.beaconZ = beaconZ;
        this.radius = radius;
    }

    public static MinimapStartPacket decode(FriendlyByteBuf buf) {
        return new MinimapStartPacket(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void encode(MinimapStartPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.beaconX);
        buf.writeInt(pkt.beaconZ);
        buf.writeInt(pkt.radius);
    }

    public static void handle(MinimapStartPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                MinimapClientProxy.onGameStartClient(pkt.beaconX, pkt.beaconZ, pkt.radius)
        );
        ctx.get().setPacketHandled(true);
    }
}