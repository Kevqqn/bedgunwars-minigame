package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestGunMenuPacket {

    public static void encode(RequestGunMenuPacket msg, FriendlyByteBuf buf) {}

    public static RequestGunMenuPacket decode(FriendlyByteBuf buf) {
        return new RequestGunMenuPacket();
    }

    public static void handle(RequestGunMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            GameSession session = GameManager.getSession();
            if (session == null || !session.isActive()) return;
            if (session.getPhase() != GamePhase.PREPARATION) return;
            if (!session.getPlayers().contains(player.getUUID())) return;

            ResourceLocation current = session.getGunSelectionManager().getSelection(player.getUUID());
            PacketHandler.sendToClient(player, new OpenGunMenuPacket(
                    session.getGunSelectionManager().getAvailableGuns(),
                    current
            ));
        });
        ctx.get().setPacketHandled(true);
    }
}