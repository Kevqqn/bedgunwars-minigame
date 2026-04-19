package com.frosty.bedgunwars.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class GameCleanupManager {
    public static void restoreAndEnd(MinecraftServer server, GameSession session, String endMessage) {
        BorderManager.restoreBorder(session);

        for (UUID uuid : session.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }

            session.restorePlayerState(player);
        }

        session.end();
        GameManager.end();
        broadcast(server, endMessage);
    }

    private static void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}