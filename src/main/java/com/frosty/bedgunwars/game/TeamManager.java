package com.frosty.bedgunwars.game;

import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public class TeamManager {

    public static void assignTeams(GameSession session, List<ServerPlayer> players, int teamCount) {
        List<List<UUID>> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) teams.add(new ArrayList<>());

        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        for (int i = 0; i < shuffled.size(); i++) {
            UUID uuid = shuffled.get(i).getUUID();
            int teamIndex = i % teamCount;
            String teamName = "Team " + (teamIndex + 1);
            session.addPlayer(uuid);
            session.setPlayerTeam(uuid, teamName);
            teams.get(teamIndex).add(uuid);
        }

        // Assign one random bed owner per team
        Random random = new Random();
        for (int i = 0; i < teamCount; i++) {
            List<UUID> teamMembers = teams.get(i);
            if (teamMembers.isEmpty()) continue;
            UUID bedOwner = teamMembers.get(random.nextInt(teamMembers.size()));
            session.setTeamBedOwner("Team " + (i + 1), bedOwner);
        }
    }

    public static boolean isTeamEliminated(GameSession session, String team) {
        for (UUID uuid : session.getPlayers()) {
            if (!team.equals(session.getPlayerTeam(uuid))) continue;
            if (!session.isEliminated(uuid)) return false;
        }
        return true;
    }

    public static boolean isTeamBedBroken(GameSession session, String team) {
        UUID bedOwner = session.getTeamBedOwner(team);
        if (bedOwner == null) return true;
        return session.isBedBroken(bedOwner);
    }

    public static List<String> getAliveTeams(GameSession session) {
        Set<String> alive = new LinkedHashSet<>();
        for (UUID uuid : session.getPlayers()) {
            if (!session.isEliminated(uuid)) {
                String team = session.getPlayerTeam(uuid);
                if (team != null) alive.add(team);
            }
        }
        return new ArrayList<>(alive);
    }

    public static String getTeamColor(String teamName) {
        return switch (teamName) {
            case "Team 1" -> "§c";
            case "Team 2" -> "§9";
            case "Team 3" -> "§a";
            case "Team 4" -> "§e";
            case "Team 5" -> "§d";
            case "Team 6" -> "§6";
            default       -> "§f";
        };
    }
}