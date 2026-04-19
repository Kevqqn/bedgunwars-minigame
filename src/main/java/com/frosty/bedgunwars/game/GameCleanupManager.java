package com.frosty.bedgunwars.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.UUID;

public class GameCleanupManager {
    public static void restoreAndEnd(MinecraftServer server, GameSession session, String endMessage) {
        BorderManager.restoreBorder(session);

        for (UUID uuid : session.getPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }

            GameSession.PlayerSnapshot snapshot = session.getPlayerSnapshot(uuid);
            if (snapshot != null) {
                snapshot.restore(player);
            } else {
                player.teleportTo(
                        session.getLevel(),
                        session.getBeaconPos().getX() + 0.5,
                        session.getBeaconPos().getY() + 1.0,
                        session.getBeaconPos().getZ() + 0.5,
                        player.getYRot(),
                        player.getXRot()
                );
                player.setGameMode(GameType.SURVIVAL);
            }
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