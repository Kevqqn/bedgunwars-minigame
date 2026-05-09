package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class OpenGunMenuPacket {

    private final List<ResourceLocation> allGuns;
    private final List<ResourceLocation> currentGunSelections;
    private final List<ResourceLocation> allAttachments;
    private final List<ResourceLocation> currentAttachmentSelections;
    private final List<ResourceLocation> allThrowables;
    private final List<ResourceLocation> currentThrowableSelections;
    private final Map<Integer, Map<String, String>> gunAttachments;
    private final boolean activePhase;

    public OpenGunMenuPacket(
            List<ResourceLocation> allGuns,
            List<ResourceLocation> currentGunSelections,
            List<ResourceLocation> allAttachments,
            List<ResourceLocation> currentAttachmentSelections,
            List<ResourceLocation> allThrowables,
            List<ResourceLocation> currentThrowableSelections,
            Map<Integer, Map<String, String>> gunAttachments,
            boolean activePhase) {
        this.allGuns = allGuns;
        this.currentGunSelections = currentGunSelections;
        this.allAttachments = allAttachments;
        this.currentAttachmentSelections = currentAttachmentSelections;
        this.allThrowables = allThrowables;
        this.currentThrowableSelections = currentThrowableSelections;
        this.gunAttachments = gunAttachments;
        this.activePhase = activePhase;
    }

    private static void writeList(FriendlyByteBuf buf, List<ResourceLocation> list) {
        buf.writeInt(list.size());
        for (ResourceLocation id : list) buf.writeResourceLocation(id);
    }

    private static List<ResourceLocation> readList(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<ResourceLocation> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(buf.readResourceLocation());
        return list;
    }

    public static void encode(OpenGunMenuPacket msg, FriendlyByteBuf buf) {
        writeList(buf, msg.allGuns);
        writeList(buf, msg.currentGunSelections);
        writeList(buf, msg.allAttachments);
        writeList(buf, msg.currentAttachmentSelections);
        writeList(buf, msg.allThrowables);
        writeList(buf, msg.currentThrowableSelections);
        buf.writeBoolean(msg.activePhase);
        // Encode gunAttachments map
        buf.writeInt(msg.gunAttachments.size());
        msg.gunAttachments.forEach((slot, typeMap) -> {
            buf.writeInt(slot);
            buf.writeInt(typeMap.size());
            typeMap.forEach((type, id) -> {
                buf.writeUtf(type);
                buf.writeUtf(id);
            });
        });
    }

    public static OpenGunMenuPacket decode(FriendlyByteBuf buf) {
        List<ResourceLocation> allGuns         = readList(buf);
        List<ResourceLocation> currentGuns     = readList(buf);
        List<ResourceLocation> allAtt          = readList(buf);
        List<ResourceLocation> currentAtt      = readList(buf);
        List<ResourceLocation> allThrow        = readList(buf);
        List<ResourceLocation> currentThrow    = readList(buf);
        boolean activePhase = buf.readBoolean();
        int slotCount = buf.readInt();
        Map<Integer, Map<String, String>> gunAttachments = new HashMap<>();
        for (int i = 0; i < slotCount; i++) {
            int slot = buf.readInt();
            int typeCount = buf.readInt();
            Map<String, String> typeMap = new HashMap<>();
            for (int j = 0; j < typeCount; j++) {
                typeMap.put(buf.readUtf(), buf.readUtf());
            }
            gunAttachments.put(slot, typeMap);
        }
        return new OpenGunMenuPacket(allGuns, currentGuns, allAtt, currentAtt,
                allThrow, currentThrow, gunAttachments, activePhase);
    }

    public static void handle(OpenGunMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                com.frosty.bedgunwars.client.GunSelectionScreen.open(
                        msg.allGuns, msg.currentGunSelections,
                        msg.allAttachments, msg.currentAttachmentSelections,
                        msg.allThrowables, msg.currentThrowableSelections,
                        msg.gunAttachments, msg.activePhase));
        ctx.get().setPacketHandled(true);
    }

    public List<ResourceLocation> getAllGuns()                    { return allGuns; }
    public List<ResourceLocation> getCurrentGunSelections()       { return currentGunSelections; }
    public List<ResourceLocation> getAllAttachments()              { return allAttachments; }
    public List<ResourceLocation> getCurrentAttachmentSelections() { return currentAttachmentSelections; }
    public List<ResourceLocation> getAllThrowables()               { return allThrowables; }
    public List<ResourceLocation> getCurrentThrowableSelections()  { return currentThrowableSelections; }
    public Map<Integer, Map<String, String>> getGunAttachments()   { return gunAttachments; }
    public boolean isActivePhase()                                  { return activePhase; }
}