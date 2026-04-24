package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
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

            List<ResourceLocation> allGuns = GunSelectionManager.getAllAvailableGuns();
            List<ResourceLocation> validated = new ArrayList<>();
            for (ResourceLocation id : msg.gunIds) {
                if (allGuns.contains(id)) validated.add(id);
                if (validated.size() >= session.getGunSelectionManager().getMaxGunSlots()) break;
            }

            session.getGunSelectionManager().setSelections(player.getUUID(), validated);
            removeGunsFromInventory(player);
            for (ResourceLocation id : validated) {
                player.getInventory().add(GunHelper.buildGun(id));
            }
            player.containerMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }

    private static void removeGunsFromInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem().toString().contains("modern_kinetic_gun")) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }
}