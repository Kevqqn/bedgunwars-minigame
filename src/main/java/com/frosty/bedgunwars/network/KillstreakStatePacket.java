package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class KillstreakStatePacket {
    public final int streak, uavStacks, glowStacks, airStacks, juggStacks, uavTicks, glowTicks;

    public KillstreakStatePacket(int streak, int uav, int glow, int air, int jugg, int uavT, int glowT) {
        this.streak = streak; uavStacks = uav; glowStacks = glow; airStacks = air;
        juggStacks = jugg; uavTicks = uavT; glowTicks = glowT;
    }

    public static void encode(KillstreakStatePacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.streak); buf.writeInt(p.uavStacks); buf.writeInt(p.glowStacks);
        buf.writeInt(p.airStacks); buf.writeInt(p.juggStacks);
        buf.writeInt(p.uavTicks); buf.writeInt(p.glowTicks);
    }

    public static KillstreakStatePacket decode(FriendlyByteBuf buf) {
        return new KillstreakStatePacket(buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(KillstreakStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.frosty.bedgunwars.client.KillstreakHudRenderer.updateState(pkt));
        ctx.get().setPacketHandled(true);
    }
}