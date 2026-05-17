package com.frosty.bedgunwars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShowTipPacket {

    public final String text;

    public ShowTipPacket(String text) {
        this.text = text;
    }

    public static void encode(ShowTipPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.text);
    }

    public static ShowTipPacket decode(FriendlyByteBuf buf) {
        return new ShowTipPacket(buf.readUtf());
    }

    public static void handle(ShowTipPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            String text = com.frosty.bedgunwars.client.ClientTips.resolveKeybindsPublic(pkt.text);
            com.frosty.bedgunwars.client.ClientTips.showResolved(text);
        });
        ctx.get().setPacketHandled(true);
    }
}