package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GunHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.util.List;

public class SelectGunPacket {

    private final ResourceLocation gunId;

    public SelectGunPacket(ResourceLocation gunId) {
        this.gunId = gunId;
    }

    public static void encode(SelectGunPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.gunId);
    }

    public static SelectGunPacket decode(FriendlyByteBuf buf) {
        return new SelectGunPacket(buf.readResourceLocation());
    }

    public static void handle(SelectGunPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            GameSession session = GameManager.getSession();
            if (session == null || !session.isActive()) return;
            if (session.getPhase() != GamePhase.PREPARATION) return;
            if (!session.getPlayers().contains(player.getUUID())) return;

            List<ResourceLocation> available = session.getGunSelectionManager().getAvailableGuns();
            if (!available.contains(msg.gunId)) return;

            session.getGunSelectionManager().setSelection(player.getUUID(), msg.gunId);

            // Remove any existing gun from inventory, then give the selected one
            removeGunsFromInventory(player);
            ItemStack gun = GunHelper.buildGun(msg.gunId);
            player.getInventory().add(gun);
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