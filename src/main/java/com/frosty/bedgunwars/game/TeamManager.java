package com.frosty.bedgunwars.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
    public static void applyScoreboardTeams(MinecraftServer server, GameSession session) {
        net.minecraft.world.scores.Scoreboard scoreboard = server.getScoreboard();

        // Clean up old teams first
        for (int i = 1; i <= session.getTeamCount(); i++) {
            String teamName = "bgw_team_" + i;
            if (scoreboard.getPlayerTeam(teamName) != null) {
                scoreboard.removePlayerTeam(scoreboard.getPlayerTeam(teamName));
            }
        }

        // Create teams and assign players
        for (UUID uuid : session.getPlayers()) {
            String team = session.getPlayerTeam(uuid);
            if (team == null) continue;

            int teamNum = Integer.parseInt(team.replace("Team ", ""));
            String sbTeamName = "bgw_team_" + teamNum;

            net.minecraft.world.scores.PlayerTeam sbTeam = scoreboard.getPlayerTeam(sbTeamName);
            if (sbTeam == null) sbTeam = scoreboard.addPlayerTeam(sbTeamName);

            // Set team color
            net.minecraft.ChatFormatting color = getTeamFormatting(team);
            sbTeam.setColor(color);
            sbTeam.setPlayerPrefix(Component.literal(getTeamColor(team) + "[" + team + "] §r"));

            // Add player to team
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                scoreboard.addPlayerToTeam(player.getScoreboardName(), sbTeam);
            }
        }
    }

    public static void removeScoreboardTeams(MinecraftServer server, GameSession session) {
        net.minecraft.world.scores.Scoreboard scoreboard = server.getScoreboard();
        for (int i = 1; i <= session.getTeamCount(); i++) {
            String teamName = "bgw_team_" + i;
            net.minecraft.world.scores.PlayerTeam sbTeam = scoreboard.getPlayerTeam(teamName);
            if (sbTeam != null) scoreboard.removePlayerTeam(sbTeam);
        }
    }

    private static net.minecraft.ChatFormatting getTeamFormatting(String team) {
        return switch (team) {
            case "Team 1" -> net.minecraft.ChatFormatting.RED;
            case "Team 2" -> net.minecraft.ChatFormatting.BLUE;
            case "Team 3" -> net.minecraft.ChatFormatting.GREEN;
            case "Team 4" -> net.minecraft.ChatFormatting.YELLOW;
            case "Team 5" -> net.minecraft.ChatFormatting.LIGHT_PURPLE;
            case "Team 6" -> net.minecraft.ChatFormatting.GOLD;
            default       -> net.minecraft.ChatFormatting.WHITE;
        };
    }
}