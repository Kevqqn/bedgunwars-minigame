package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

            GunSelectionManager gsm = session.getGunSelectionManager();
            UUID uuid = player.getUUID();

            List<ResourceLocation> allGuns     = GunSelectionManager.getAllAvailableGuns();
            List<ResourceLocation> currentGuns = gsm.getGunSelections(uuid);
            List<ResourceLocation> allAtt      = GunHelper.getCompatibleAttachments(currentGuns);
            List<ResourceLocation> allThrow    = GunSelectionManager.getAllAvailableThrowables();
            List<ResourceLocation> currentThrow = gsm.getThrowableSelections(uuid);

            Map<Integer, Map<String, String>> gunAttachments = buildAttachmentMap(uuid, gsm);

            PacketHandler.sendToClient(player, new OpenGunMenuPacket(
                    allGuns, currentGuns,
                    allAtt, new java.util.ArrayList<>(),
                    allThrow, currentThrow,
                    gunAttachments));

            // Also send current loadouts
            LoadoutSyncPacket.send(player,
                    LoadoutManager.get().getLoadouts(uuid));
        });
        ctx.get().setPacketHandled(true);
    }

    static Map<Integer, Map<String, String>> buildAttachmentMap(UUID uuid, GunSelectionManager gsm) {
        Map<Integer, Map<String, String>> result = new HashMap<>();
        gsm.getAllGunAttachments(uuid).forEach((slot, typeMap) -> {
            Map<String, String> stringMap = new HashMap<>();
            typeMap.forEach((type, id) -> stringMap.put(type.name(), id.toString()));
            result.put(slot, stringMap);
        });
        return result;
    }
}