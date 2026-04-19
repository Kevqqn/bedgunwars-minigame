package com.frosty.bedgunwars.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WinManager {

    public static void checkWinner(GameSession session) {
        if (session == null || !session.isActive()) {
            return;
        }

        if (session.getPhase() != GamePhase.ACTIVE) {
            return;
        }

        if (session.getMode() == GameModeType.SOLO) {
            checkSoloWinner(session);
        } else {
            checkTeamWinner(session);
        }
    }

    private static void checkSoloWinner(GameSession session) {
        Set<UUID> alive = new HashSet<>();

        for (UUID uuid : session.getPlayers()) {
            if (!session.isEliminated(uuid)) {
                alive.add(uuid);
            }
        }

        if (alive.size() == 1) {
            UUID winnerId = alive.iterator().next();
            ServerPlayer winner = session.getLevel().getServer().getPlayerList().getPlayer(winnerId);
            String winnerName = winner != null ? winner.getGameProfile().getName() : "Unknown";
            session.setWinner(winnerName);
        }
    }

    private static void checkTeamWinner(GameSession session) {
        Set<String> aliveTeams = new HashSet<>();

        for (UUID uuid : session.getPlayers()) {
            if (!session.isEliminated(uuid)) {
                String team = session.getPlayerTeam(uuid);
                if (team != null) {
                    aliveTeams.add(team);
                }
            }
        }

        if (aliveTeams.size() == 1) {
            String teamName = aliveTeams.iterator().next();
            session.setWinner(teamName + " Team");
        }
    }
}