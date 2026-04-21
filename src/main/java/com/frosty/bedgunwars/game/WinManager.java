package com.frosty.bedgunwars.game;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WinManager {

    public static void checkWinner(GameSession session) {
        if (session == null || !session.isActive()) return;
        if (session.getPhase() != GamePhase.ACTIVE) return;

        // Safeguard: single-player testing — don't auto-end immediately
        if (session.getMatchStartPlayerCount() <= 1) return;

        if (session.getMode() == GameModeType.SOLO) {
            checkSoloWinner(session);
        } else {
            checkTeamWinner(session);
        }
    }

    private static void checkSoloWinner(GameSession session) {
        UUID lastAlive = null;
        int aliveCount = 0;

        for (UUID uuid : session.getPlayers()) {
            if (session.isEliminated(uuid)) continue;
            aliveCount++;
            lastAlive = uuid;
            if (aliveCount > 1) return;
        }

        if (aliveCount == 1 && lastAlive != null) {
            String winnerName = session.getPlayerTeam(lastAlive);
            if (winnerName == null) winnerName = "Unknown";
            announceWinner(session, winnerName);
        }
    }

    private static void checkTeamWinner(GameSession session) {
        Set<String> aliveTeams = new HashSet<>();

        for (UUID uuid : session.getPlayers()) {
            if (session.isEliminated(uuid)) continue;
            String team = session.getPlayerTeam(uuid);
            if (team != null) aliveTeams.add(team);
            if (aliveTeams.size() > 1) return;
        }

        if (aliveTeams.size() == 1) {
            String winnerTeam = aliveTeams.iterator().next() + " Team";
            announceWinner(session, winnerTeam);
        }
    }

    private static void announceWinner(GameSession session, String winnerName) {
        session.setWinner(winnerName);

        // Send title screen to all players in the game
        for (UUID uuid : session.getPlayers()) {
            ServerPlayer player = session.getLevel().getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            sendTitle(player, winnerName + " won the game!", "");
        }
    }

    public static void sendTitle(ServerPlayer player, String title, String subtitle) {
        // Timing: fadeIn=10, stay=70, fadeOut=20 ticks
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
    }
}