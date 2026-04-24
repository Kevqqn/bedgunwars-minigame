package com.frosty.bedgunwars.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("bedgunwars", "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenGunMenuPacket.class,
                OpenGunMenuPacket::encode, OpenGunMenuPacket::decode, OpenGunMenuPacket::handle);
        CHANNEL.registerMessage(id++, SelectGunPacket.class,
                SelectGunPacket::encode, SelectGunPacket::decode, SelectGunPacket::handle);
        CHANNEL.registerMessage(id++, RequestGunMenuPacket.class,
                RequestGunMenuPacket::encode, RequestGunMenuPacket::decode, RequestGunMenuPacket::handle);
        CHANNEL.registerMessage(id++, SelectAttachmentPacket.class,
                SelectAttachmentPacket::encode, SelectAttachmentPacket::decode, SelectAttachmentPacket::handle);
        CHANNEL.registerMessage(id++, SelectThrowablePacket.class,
                SelectThrowablePacket::encode, SelectThrowablePacket::decode, SelectThrowablePacket::handle);
    }

    public static void sendToClient(net.minecraft.server.level.ServerPlayer player, Object packet) {
        CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}