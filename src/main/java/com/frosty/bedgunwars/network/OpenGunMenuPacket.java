package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.GunSelectionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenGunMenuPacket {

    private final List<ResourceLocation> guns;
    private final ResourceLocation currentSelection;

    public OpenGunMenuPacket(List<ResourceLocation> guns, ResourceLocation currentSelection) {
        this.guns = guns;
        this.currentSelection = currentSelection;
    }

    public static void encode(OpenGunMenuPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.guns.size());
        for (ResourceLocation gun : msg.guns) {
            buf.writeResourceLocation(gun);
        }
        buf.writeBoolean(msg.currentSelection != null);
        if (msg.currentSelection != null) {
            buf.writeResourceLocation(msg.currentSelection);
        }
    }

    public static OpenGunMenuPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<ResourceLocation> guns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            guns.add(buf.readResourceLocation());
        }
        ResourceLocation current = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new OpenGunMenuPacket(guns, current);
    }

    public static void handle(OpenGunMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraftforge.api.distmarker.Dist dist = net.minecraftforge.fml.loading.FMLEnvironment.dist;
            if (dist.isClient()) {
                com.frosty.bedgunwars.client.GunSelectionScreen.open(msg.guns, msg.currentSelection);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public List<ResourceLocation> getGuns() { return guns; }
    public ResourceLocation getCurrentSelection() { return currentSelection; }
}