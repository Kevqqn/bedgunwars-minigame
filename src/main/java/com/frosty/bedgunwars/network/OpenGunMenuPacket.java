package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenGunMenuPacket {

    private final List<ResourceLocation> allGuns;
    private final List<ResourceLocation> currentSelections;

    public OpenGunMenuPacket(List<ResourceLocation> allGuns, List<ResourceLocation> currentSelections) {
        this.allGuns = allGuns;
        this.currentSelections = currentSelections;
    }

    public static void encode(OpenGunMenuPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.allGuns.size());
        for (ResourceLocation gun : msg.allGuns) buf.writeResourceLocation(gun);
        buf.writeInt(msg.currentSelections.size());
        for (ResourceLocation sel : msg.currentSelections) buf.writeResourceLocation(sel);
    }

    public static OpenGunMenuPacket decode(FriendlyByteBuf buf) {
        int gunCount = buf.readInt();
        List<ResourceLocation> guns = new ArrayList<>();
        for (int i = 0; i < gunCount; i++) guns.add(buf.readResourceLocation());
        int selCount = buf.readInt();
        List<ResourceLocation> sels = new ArrayList<>();
        for (int i = 0; i < selCount; i++) sels.add(buf.readResourceLocation());
        return new OpenGunMenuPacket(guns, sels);
    }

    public static void handle(OpenGunMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient();
            com.frosty.bedgunwars.client.GunSelectionScreen.open(msg.allGuns, msg.currentSelections);
        });
        ctx.get().setPacketHandled(true);
    }

    public List<ResourceLocation> getAllGuns() { return allGuns; }
    public List<ResourceLocation> getCurrentSelections() { return currentSelections; }
}