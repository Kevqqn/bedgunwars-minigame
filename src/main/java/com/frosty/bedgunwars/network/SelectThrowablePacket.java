package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.*;
import com.frosty.bedgunwars.game.GunHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

public class SelectThrowablePacket {
    private final List<ResourceLocation> throwableIds;

    public SelectThrowablePacket(List<ResourceLocation> ids) {
        this.throwableIds = ids;
    }

    public static void encode(SelectThrowablePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.throwableIds.size());
        for (ResourceLocation id : msg.throwableIds) buf.writeResourceLocation(id);
    }

    public static SelectThrowablePacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add(buf.readResourceLocation());
        return new SelectThrowablePacket(ids);
    }

    public static void handle(SelectThrowablePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            GameSession session = GameManager.getSession();
            if (session == null || !session.isActive()) return;
            if (session.getPhase() != GamePhase.PREPARATION) return;
            if (!session.getPlayers().contains(player.getUUID())) return;

            GunSelectionManager gsm = session.getGunSelectionManager();
            List<ResourceLocation> all = GunSelectionManager.getAllAvailableThrowables();
            List<ResourceLocation> validated = new ArrayList<>();
            for (ResourceLocation id : msg.throwableIds) {
                if (all.contains(id)) validated.add(id);
                if (validated.size() >= gsm.getMaxThrowablePicks()) break;
            }
            gsm.setThrowableSelections(player.getUUID(), validated);
            removeThrowablesFromInventory(player);
            for (ResourceLocation id : validated) {
                ItemStack stack = GunHelper.buildThrowable(id);
                if (!stack.isEmpty()) player.getInventory().add(stack);
            }
            player.containerMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }

    private static void removeThrowablesFromInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            try {
                if (me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndex(stack).isPresent()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            } catch (Exception ignored) {}
        }
    }
}