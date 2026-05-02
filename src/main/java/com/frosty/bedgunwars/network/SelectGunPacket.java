package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.*;
import com.tacz.guns.api.item.IGun;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import com.frosty.bedgunwars.game.GunHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class SelectGunPacket {

    private final List<ResourceLocation> gunIds;

    public SelectGunPacket(List<ResourceLocation> gunIds) {
        this.gunIds = gunIds;
    }

    public static void encode(SelectGunPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.gunIds.size());
        for (ResourceLocation id : msg.gunIds) buf.writeResourceLocation(id);
    }

    public static SelectGunPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add(buf.readResourceLocation());
        return new SelectGunPacket(ids);
    }

    public static void handle(SelectGunPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            GameSession session = GameManager.getSession();
            if (session == null || !session.isActive()) return;
            if (session.getPhase() != GamePhase.PREPARATION) return;
            if (!session.getPlayers().contains(player.getUUID())) return;

            GunSelectionManager gsm = session.getGunSelectionManager();
            UUID uuid = player.getUUID();

            List<ResourceLocation> validated = new ArrayList<>();
            for (ResourceLocation id : msg.gunIds) {
                validated.add(id);
                if (validated.size() >= gsm.getMaxGunSlots()) break;
            }

            // Clear attachments for any gun slots that changed
            List<ResourceLocation> oldGuns = gsm.getGunSelections(uuid);
            for (int i = 0; i < oldGuns.size(); i++) {
                if (i >= validated.size() || !oldGuns.get(i).equals(validated.get(i))) {
                    gsm.clearGunAttachments(uuid, i);
                }
            }

            gsm.setGunSelections(uuid, validated);

            removeGunsFromInventory(player);
            for (ResourceLocation id : validated) {
                ItemStack stack = GunHelper.buildGun(id);
                if (!stack.isEmpty()) player.getInventory().add(stack);
            }
            player.containerMenu.broadcastChanges();

            // Resend updated menu with new compatible attachments
            List<ResourceLocation> allGuns  = GunSelectionManager.getAllAvailableGuns();
            List<ResourceLocation> allAtt   = GunHelper.getCompatibleAttachments(validated);
            List<ResourceLocation> allThrow = GunSelectionManager.getAllAvailableThrowables();
            List<ResourceLocation> currentThrow = gsm.getThrowableSelections(uuid);
            PacketHandler.sendToClient(player, new OpenGunMenuPacket(
                    allGuns, validated,
                    allAtt, new ArrayList<>(),
                    allThrow, currentThrow,
                    RequestGunMenuPacket.buildAttachmentMap(uuid, gsm)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void removeGunsFromInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof IGun) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }
}