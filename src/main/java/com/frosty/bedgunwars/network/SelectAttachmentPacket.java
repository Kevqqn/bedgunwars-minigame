package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.*;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class SelectAttachmentPacket {
    private final List<ResourceLocation> attachmentIds;

    public SelectAttachmentPacket(List<ResourceLocation> ids) { this.attachmentIds = ids; }

    public static void encode(SelectAttachmentPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.attachmentIds.size());
        for (ResourceLocation id : msg.attachmentIds) buf.writeResourceLocation(id);
    }

    public static SelectAttachmentPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add(buf.readResourceLocation());
        return new SelectAttachmentPacket(ids);
    }

    public static void handle(SelectAttachmentPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            GameSession session = GameManager.getSession();
            if (session == null || !session.isActive()) return;
            if (session.getPhase() != GamePhase.PREPARATION) return;
            if (!session.getPlayers().contains(player.getUUID())) return;

            GunSelectionManager gsm = session.getGunSelectionManager();
            List<ResourceLocation> all = GunSelectionManager.getAllAvailableAttachments();
            List<ResourceLocation> validated = new ArrayList<>();
            for (ResourceLocation id : msg.attachmentIds) {
                if (all.contains(id)) validated.add(id);
                if (validated.size() >= gsm.getMaxAttachmentPicks()) break;
            }
            gsm.setAttachmentSelections(player.getUUID(), validated);
            removeFromInventory(player);
            for (ResourceLocation id : validated) {
                try {
                    ItemStack stack = AttachmentItemBuilder.create().setId(id).build();
                    if (!stack.isEmpty()) player.getInventory().add(stack);
                } catch (Exception ignored) {}
            }
            player.containerMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }

    private static void removeFromInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof IAttachment) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }
}