package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class KillstreakEffectPacket {
    public enum Effect { UAV_START, UAV_END, AIR_SUPPORT_OPEN }
    public final Effect effect;

    public KillstreakEffectPacket(Effect e) { effect = e; }

    public static void encode(KillstreakEffectPacket p, FriendlyByteBuf buf) { buf.writeEnum(p.effect); }
    public static KillstreakEffectPacket decode(FriendlyByteBuf buf) {
        return new KillstreakEffectPacket(buf.readEnum(Effect.class));
    }

    public static void handle(KillstreakEffectPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            switch (pkt.effect) {
                case UAV_START      -> com.frosty.bedgunwars.minimap.MinimapRenderer.showAllEnemyDots = true;
                case UAV_END        -> com.frosty.bedgunwars.minimap.MinimapRenderer.showAllEnemyDots = false;
                case AIR_SUPPORT_OPEN -> com.frosty.bedgunwars.client.AirSupportMapScreen.open();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}