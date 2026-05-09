package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.*;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class SelectAttachmentPacket {

    private final int gunSlot;
    private final AttachmentType attachmentType;
    private final ResourceLocation attachmentId; // null = remove

    public SelectAttachmentPacket(int gunSlot, AttachmentType type, ResourceLocation attachmentId) {
        this.gunSlot = gunSlot;
        this.attachmentType = type;
        this.attachmentId = attachmentId;
    }

    public static void encode(SelectAttachmentPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.gunSlot);
        buf.writeUtf(msg.attachmentType.name());
        buf.writeBoolean(msg.attachmentId != null);
        if (msg.attachmentId != null) buf.writeResourceLocation(msg.attachmentId);
    }

    public static SelectAttachmentPacket decode(FriendlyByteBuf buf) {
        int slot = buf.readInt();
        AttachmentType type = AttachmentType.valueOf(buf.readUtf());
        boolean hasId = buf.readBoolean();
        ResourceLocation id = hasId ? buf.readResourceLocation() : null;
        return new SelectAttachmentPacket(slot, type, id);
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
            UUID uuid = player.getUUID();
            List<ResourceLocation> guns = gsm.getGunSelections(uuid);
            if (msg.gunSlot >= guns.size()) return;

            // Find the gun in inventory at hotbar slot matching gun slot index
            ResourceLocation targetGunId = guns.get(msg.gunSlot);
            ItemStack gunStack = ItemStack.EMPTY;
            int foundSlot = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty() && s.getItem() instanceof IGun iGunCheck) {
                    if (targetGunId.equals(iGunCheck.getGunId(s))) {
                        gunStack = s;
                        foundSlot = i;
                        break;
                    }
                }
            }
            if (gunStack.isEmpty() || foundSlot == -1) return;
            if (!(gunStack.getItem() instanceof IGun iGun)) return;

            if (msg.attachmentId == null) {
                // Remove attachment
                iGun.unloadAttachment(gunStack, msg.attachmentType);
                gsm.removeAttachment(uuid, msg.gunSlot, msg.attachmentType);
            } else {
                try {
                    ItemStack attachStack = AttachmentItemBuilder.create().setId(msg.attachmentId).build();
                    if (attachStack.isEmpty()) return;
                    if (!iGun.allowAttachment(gunStack, attachStack)) return;
                    // installAttachment handles replacing the existing same-type slot
                    iGun.installAttachment(gunStack, attachStack);
                    gsm.setAttachment(uuid, msg.gunSlot, msg.attachmentType, msg.attachmentId);
                } catch (Exception ignored) {}
            }

            player.containerMenu.broadcastChanges();

            // Resend updated menu so client attachment map refreshes
            List<ResourceLocation> allGuns  = GunSelectionManager.getAllAvailableGuns();
            List<ResourceLocation> currentGuns = gsm.getGunSelections(uuid);
            List<ResourceLocation> allAtt   = GunHelper.getCompatibleAttachments(currentGuns);
            List<ResourceLocation> allThrow = GunSelectionManager.getAllAvailableThrowables();
            List<ResourceLocation> currentThrow = gsm.getThrowableSelections(uuid);
            PacketHandler.sendToClient(player, new OpenGunMenuPacket(
                    allGuns, currentGuns,
                    allAtt, new ArrayList<>(),
                    allThrow, currentThrow,
                    RequestGunMenuPacket.buildAttachmentMap(uuid, gsm), false));
        });
        ctx.get().setPacketHandled(true);
    }
}