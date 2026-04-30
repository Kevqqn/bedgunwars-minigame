package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
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

            List<ResourceLocation> allGuns = GunSelectionManager.getAllAvailableGuns();
            List<ResourceLocation> currentGuns = session.getGunSelectionManager().getGunSelections(player.getUUID());
            List<ResourceLocation> compatibleAttachments = GunHelper.getCompatibleAttachments(currentGuns);
            List<ResourceLocation> currentAttachments = session.getGunSelectionManager().getAttachmentSelections(player.getUUID());
            List<ResourceLocation> throwables = GunSelectionManager.getAllAvailableThrowables();
            List<ResourceLocation> currentThrowables = session.getGunSelectionManager().getThrowableSelections(player.getUUID());

            PacketHandler.sendToClient(player, new OpenGunMenuPacket(
                    allGuns, currentGuns,
                    compatibleAttachments, currentAttachments,
                    throwables, currentThrowables));
        });
        ctx.get().setPacketHandled(true);
    }
}
