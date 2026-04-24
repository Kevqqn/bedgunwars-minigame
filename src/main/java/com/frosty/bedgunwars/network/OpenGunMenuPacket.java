package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenGunMenuPacket {

    private final List<ResourceLocation> allGuns;
    private final List<ResourceLocation> currentGunSelections;
    private final List<ResourceLocation> allAttachments;
    private final List<ResourceLocation> currentAttachmentSelections;
    private final List<ResourceLocation> allThrowables;
    private final List<ResourceLocation> currentThrowableSelections;

    public OpenGunMenuPacket(
            List<ResourceLocation> allGuns,
            List<ResourceLocation> currentGunSelections,
            List<ResourceLocation> allAttachments,
            List<ResourceLocation> currentAttachmentSelections,
            List<ResourceLocation> allThrowables,
            List<ResourceLocation> currentThrowableSelections) {
        this.allGuns = allGuns;
        this.currentGunSelections = currentGunSelections;
        this.allAttachments = allAttachments;
        this.currentAttachmentSelections = currentAttachmentSelections;
        this.allThrowables = allThrowables;
        this.currentThrowableSelections = currentThrowableSelections;
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
    }

    public static OpenGunMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenGunMenuPacket(
                readList(buf), readList(buf),
                readList(buf), readList(buf),
                readList(buf), readList(buf));
    }

    public static void handle(OpenGunMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                com.frosty.bedgunwars.client.GunSelectionScreen.open(
                        msg.allGuns, msg.currentGunSelections,
                        msg.allAttachments, msg.currentAttachmentSelections,
                        msg.allThrowables, msg.currentThrowableSelections));
        ctx.get().setPacketHandled(true);
    }

    public List<ResourceLocation> getAllGuns()                   { return allGuns; }
    public List<ResourceLocation> getCurrentGunSelections()      { return currentGunSelections; }
    public List<ResourceLocation> getAllAttachments()             { return allAttachments; }
    public List<ResourceLocation> getCurrentAttachmentSelections(){ return currentAttachmentSelections; }
    public List<ResourceLocation> getAllThrowables()              { return allThrowables; }
    public List<ResourceLocation> getCurrentThrowableSelections() { return currentThrowableSelections; }
}