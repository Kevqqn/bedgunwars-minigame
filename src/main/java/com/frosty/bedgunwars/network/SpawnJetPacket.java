package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class SpawnJetPacket {
    public final double x, y, z, dx, dz;
    public final float speed;
    public final int delay; // ticks before spawning client-side

    public SpawnJetPacket(double x, double y, double z,
                          double dx, double dz, float speed, int delay) {
        this.x = x; this.y = y; this.z = z;
        this.dx = dx; this.dz = dz;
        this.speed = speed; this.delay = delay;
    }

    public static void encode(SpawnJetPacket p, FriendlyByteBuf buf) {
        buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
        buf.writeDouble(p.dx); buf.writeDouble(p.dz);
        buf.writeFloat(p.speed); buf.writeInt(p.delay);
    }

    public static SpawnJetPacket decode(FriendlyByteBuf buf) {
        return new SpawnJetPacket(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readInt());
    }

    public static void handle(SpawnJetPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                com.frosty.bedgunwars.client.ClientJetManager.queueJet(
                        pkt.x, pkt.y, pkt.z, pkt.dx, pkt.dz, pkt.speed, pkt.delay));
        ctx.get().setPacketHandled(true);
    }
}