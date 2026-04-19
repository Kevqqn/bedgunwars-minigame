package com.frosty.bedgunwars.game;

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

        // Debug safeguard:
        // if the match started with only 1 player, do not auto-end immediately.
        if (session.getMatchStartPlayerCount() <= 1) {
            return;
        }

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
            if (session.isEliminated(uuid)) {
                continue;
            }

            aliveCount++;
            lastAlive = uuid;

            if (aliveCount > 1) {
                return;
            }
        }

        if (aliveCount == 1 && lastAlive != null) {
            String winnerName = session.getPlayerTeam(lastAlive);
            if (winnerName == null) {
                winnerName = "Unknown";
            }
            session.setWinner(winnerName);
        }
    }

    private static void checkTeamWinner(GameSession session) {
        Set<String> aliveTeams = new HashSet<>();

        for (UUID uuid : session.getPlayers()) {
            if (session.isEliminated(uuid)) {
                continue;
            }

            String team = session.getPlayerTeam(uuid);
            if (team != null) {
                aliveTeams.add(team);
            }

            if (aliveTeams.size() > 1) {
                return;
            }
        }

        if (aliveTeams.size() == 1) {
            String winnerTeam = aliveTeams.iterator().next();
            session.setWinner(winnerTeam + " Team");
        }
    }
}