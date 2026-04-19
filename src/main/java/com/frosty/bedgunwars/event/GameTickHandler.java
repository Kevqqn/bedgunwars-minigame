package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.WinManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

public class GameTickHandler {

    private static int lastAnnouncedSecond = -1;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!GameManager.hasGame()) {
            lastAnnouncedSecond = -1;
            return;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            return;
        }

        if (session.getPhase() == GamePhase.PREPARATION) {
            session.decreasePrepTime();

            int secondsLeft = session.getPrepTimeTicks() / 20;

            if (secondsLeft != lastAnnouncedSecond && secondsLeft % 30 == 0 && secondsLeft > 0) {
                lastAnnouncedSecond = secondsLeft;
                broadcast(event.getServer(), "Preparation time left: " + secondsLeft + "s");
            }

            if (session.getPrepTimeTicks() <= 0) {
                session.setPhase(GamePhase.ACTIVE);
                broadcast(event.getServer(), "Preparation ended. The match is now active!");
            }
            return;
        }

        if (session.getPhase() == GamePhase.ACTIVE) {
            WinManager.checkWinner(session);
            return;
        }

        if (session.getPhase() == GamePhase.WINNER_ANNOUNCED) {
            if (session.getWinnerDelayTicks() == 60) {
                showWinnerTitle(event.getServer(), session.getWinnerName());
            }

            session.decreaseWinnerDelay();

            if (session.getWinnerDelayTicks() <= 0) {
                cleanupAndEnd(event.getServer(), session);
            }
        }
    }

    private void showWinnerTitle(MinecraftServer server, String winnerName) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Winner")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(winnerName)));
        }
    }

    private void cleanupAndEnd(MinecraftServer server, GameSession session) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!session.getPlayers().contains(player.getUUID())) {
                continue;
            }

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

        session.end();
        GameManager.end();
        broadcast(server, "Game ended. Returning to vanilla gameplay.");
    }

    private void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}