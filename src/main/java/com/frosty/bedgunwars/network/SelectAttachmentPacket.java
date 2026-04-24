package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

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
            removeFromInventory(player, all);
            for (ResourceLocation id : validated) {
                var item = ForgeRegistries.ITEMS.getValue(id);
                if (item != null) player.getInventory().add(new ItemStack(item));
            }
            player.containerMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }

    private static void removeFromInventory(ServerPlayer player, List<ResourceLocation> catalogue) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && catalogue.contains(id)) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }
}