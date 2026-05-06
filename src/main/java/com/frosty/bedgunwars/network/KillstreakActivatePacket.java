package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.KillstreakType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class KillstreakActivatePacket {
    public final KillstreakType type;
    public KillstreakActivatePacket(KillstreakType t) { type = t; }

    public static void encode(KillstreakActivatePacket p, FriendlyByteBuf buf) { buf.writeEnum(p.type); }
    public static KillstreakActivatePacket decode(FriendlyByteBuf buf) {
        return new KillstreakActivatePacket(buf.readEnum(KillstreakType.class));
    }

    public static void handle(KillstreakActivatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !GameManager.hasGame()) return;
            GameSession session = GameManager.getSession();
            if (session == null) return;
            session.getKillstreakManager().activate(
                    player.getUUID(), pkt.type, player.getServer(), session);
        });
        ctx.get().setPacketHandled(true);
    }
}