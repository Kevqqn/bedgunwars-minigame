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
        CHANNEL.registerMessage(id++, MinimapStartPacket.class,
                MinimapStartPacket::encode, MinimapStartPacket::decode, MinimapStartPacket::handle);
        CHANNEL.registerMessage(id++, MinimapStopPacket.class,
                MinimapStopPacket::encode, MinimapStopPacket::decode, MinimapStopPacket::handle);
        CHANNEL.registerMessage(id++, MinimapStopPacket.class,
                MinimapStopPacket::encode, MinimapStopPacket::decode, MinimapStopPacket::handle);
        CHANNEL.registerMessage(id++, TabStatsPacket.class,
                TabStatsPacket::encode, TabStatsPacket::decode, TabStatsPacket::handle);
        CHANNEL.registerMessage(id++, KillstreakStatePacket.class,
                KillstreakStatePacket::encode, KillstreakStatePacket::decode, KillstreakStatePacket::handle);
        CHANNEL.registerMessage(id++, KillstreakEffectPacket.class,
                KillstreakEffectPacket::encode, KillstreakEffectPacket::decode, KillstreakEffectPacket::handle);
        CHANNEL.registerMessage(id++, KillstreakActivatePacket.class,
                KillstreakActivatePacket::encode, KillstreakActivatePacket::decode, KillstreakActivatePacket::handle);
        CHANNEL.registerMessage(id++, AirSupportPointsPacket.class,
                AirSupportPointsPacket::encode, AirSupportPointsPacket::decode, AirSupportPointsPacket::handle);
        CHANNEL.registerMessage(id++, SpawnJetPacket.class,
                SpawnJetPacket::encode, SpawnJetPacket::decode, SpawnJetPacket::handle);
        CHANNEL.registerMessage(id++, LoadoutPacket.class,
                LoadoutPacket::encode, LoadoutPacket::decode, LoadoutPacket::handle);
        CHANNEL.registerMessage(id++, LoadoutSyncPacket.class,
                LoadoutSyncPacket::encode, LoadoutSyncPacket::decode, LoadoutSyncPacket::handle);
    }

    public static void sendToClient(net.minecraft.server.level.ServerPlayer player, Object packet) {
        CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendToAllClients(net.minecraft.server.MinecraftServer server, Object packet) {
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendToClient(p, packet);
        }
    }

    public static void sendToClientByUUID(java.util.UUID uuid,
                                          net.minecraft.server.MinecraftServer server, Object packet) {
        net.minecraft.server.level.ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
        if (sp != null) sendToClient(sp, packet);
    }
}