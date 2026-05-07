package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DeathFeedPacket {

    public final String killerName;
    public final String victimName;
    public final String gunNamespace;
    public final String gunPath;

    public DeathFeedPacket(String killerName, String victimName,
                           String gunNamespace, String gunPath) {
        this.killerName  = killerName;
        this.victimName  = victimName;
        this.gunNamespace = gunNamespace;
        this.gunPath     = gunPath;
    }

    public static void encode(DeathFeedPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.killerName);
        buf.writeUtf(p.victimName);
        buf.writeUtf(p.gunNamespace);
        buf.writeUtf(p.gunPath);
    }

    public static DeathFeedPacket decode(FriendlyByteBuf buf) {
        return new DeathFeedPacket(
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(DeathFeedPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                com.frosty.bedgunwars.client.DeathFeedRenderer.addEntry(
                        pkt.killerName, pkt.victimName,
                        pkt.gunNamespace, pkt.gunPath)
        );
        ctx.get().setPacketHandled(true);
    }
}