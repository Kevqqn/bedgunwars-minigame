package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.LoadoutManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LoadoutPacket {

    public enum Action { SAVE, APPLY, DELETE, RENAME, SAVE_OVER }

    public final Action action;
    public final int index;
    public final String name;

    public LoadoutPacket(Action action, int index, String name) {
        this.action = action; this.index = index; this.name = name;
    }

    public static void encode(LoadoutPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeInt(p.index);
        buf.writeUtf(p.name != null ? p.name : "");
    }

    public static LoadoutPacket decode(FriendlyByteBuf buf) {
        return new LoadoutPacket(buf.readEnum(Action.class), buf.readInt(), buf.readUtf());
    }

    public static void handle(LoadoutPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            GameSession session = GameManager.getSession();
            if (session == null) return;
            var gsm = session.getGunSelectionManager();
            var lm = LoadoutManager.get();
            switch (pkt.action) {
                case SAVE   -> lm.saveLoadout(player.getUUID(), pkt.name, gsm);
                case DELETE -> lm.deleteLoadout(player.getUUID(), pkt.index);
                case RENAME -> lm.renameLoadout(player.getUUID(), pkt.index, pkt.name);
                case SAVE_OVER -> lm.saveOverLoadout(player.getUUID(), pkt.index, gsm);
                case APPLY  -> {
                    if (session.getPhase() == com.frosty.bedgunwars.game.GamePhase.ACTIVE) {
                        lm.applyLoadout(player.getUUID(), pkt.index, gsm);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§e[Loadout] §fApplied. Takes effect on next respawn."));
                        break;
                    }
                    java.util.List<net.minecraft.resources.ResourceLocation> oldGuns =
                            new java.util.ArrayList<>(gsm.getGunSelections(player.getUUID()));

                    lm.applyLoadout(player.getUUID(), pkt.index, gsm);

                    java.util.List<net.minecraft.resources.ResourceLocation> guns =
                            gsm.getGunSelections(player.getUUID());

                    // Remove old guns from inventory
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                        if (!s.isEmpty() && s.getItem() instanceof com.tacz.guns.api.item.IGun)
                            player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                    }
                    // Give new guns with attachments
                    for (int i = 0; i < guns.size(); i++) {
                        net.minecraft.resources.ResourceLocation id = guns.get(i);
                        net.minecraft.world.item.ItemStack stack = com.frosty.bedgunwars.game.GunHelper.buildGun(id);
                        if (stack.isEmpty()) continue;
                        if (stack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                            var atts = gsm.getGunAttachments(player.getUUID(), i);
                            for (var e : atts.entrySet()) {
                                try {
                                    net.minecraft.world.item.ItemStack att =
                                            com.tacz.guns.api.item.builder.AttachmentItemBuilder
                                                    .create().setId(e.getValue()).build();
                                    if (!att.isEmpty() && iGun.allowAttachment(stack, att))
                                        iGun.installAttachment(stack, att);
                                } catch (Exception ignored) {}
                            }
                            com.tacz.guns.api.TimelessAPI.getCommonGunIndex(id).ifPresent(idx -> {
                                int max = idx.getGunData().getAmmoAmount();
                                if (max > 0) iGun.setCurrentAmmoCount(stack, max);
                            });
                        }
                        player.getInventory().add(stack);
                    }
                    // Remove ALL ammo from inventory to prevent duplication
                    // (covers both old and new gun types)
                    java.util.List<net.minecraft.resources.ResourceLocation> allAvailGuns =
                            com.frosty.bedgunwars.game.GunSelectionManager.getAllAvailableGuns();
                    com.frosty.bedgunwars.game.GunHelper.removeAllGunAmmo(player, allAvailGuns);
                    // Give fresh ammo reserves for new loadout only
                    com.frosty.bedgunwars.game.GunHelper.giveAmmoReserves(player, guns, false);
                    java.util.List<net.minecraft.resources.ResourceLocation> throwables =
                            gsm.getThrowableSelections(player.getUUID());

                    // Remove existing throwables from inventory using LrTactical API to match correctly
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                        if (s.isEmpty()) continue;
                        try {
                            var disp = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableDisplay(s);
                            if (disp.isPresent())
                                player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                        } catch (Exception ignored) {}
                    }
                    // Give new throwables
                    for (net.minecraft.resources.ResourceLocation tid : throwables) {
                        net.minecraft.world.item.ItemStack stack = com.frosty.bedgunwars.game.GunHelper.buildThrowable(tid);
                        if (!stack.isEmpty()) player.getInventory().add(stack);
                    }

                    player.containerMenu.broadcastChanges();
                    // Resend menu with updated state
                    java.util.List<net.minecraft.resources.ResourceLocation> allGuns =
                            com.frosty.bedgunwars.game.GunSelectionManager.getAllAvailableGuns();
                    java.util.List<net.minecraft.resources.ResourceLocation> allAtt =
                            com.frosty.bedgunwars.game.GunHelper.getCompatibleAttachments(guns);
                    java.util.List<net.minecraft.resources.ResourceLocation> allThrow =
                            com.frosty.bedgunwars.game.GunSelectionManager.getAllAvailableThrowables();
                    PacketHandler.sendToClient(player, new OpenGunMenuPacket(
                            allGuns, guns, allAtt, new java.util.ArrayList<>(),
                            allThrow, throwables,
                            RequestGunMenuPacket.buildAttachmentMap(player.getUUID(), gsm), false));
                }
            }
            // Send updated loadout list back to client
            LoadoutSyncPacket.send(player, lm.getLoadouts(player.getUUID()));
        });
        ctx.get().setPacketHandled(true);
    }
}