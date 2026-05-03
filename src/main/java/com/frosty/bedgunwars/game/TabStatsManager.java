package com.frosty.bedgunwars.game;

import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.network.TabStatsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds and pushes TabStatsPacket to all players in the session.
 * Called on every kill, death, money change, bed break, and elimination.
 */
public class TabStatsManager {

    public static void push(MinecraftServer server, GameSession session) {
        if (session == null || !session.isActive()) return;

        String mode = session.getMode().name();
        List<TabStatsPacket.PlayerEntry> entries = new ArrayList<>();

        for (UUID uuid : session.getPlayers()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            String name = sp != null ? sp.getName().getString()
                    : uuid.toString().substring(0, 8);

            String team = session.getMode() == GameModeType.TEAMS
                    ? session.getPlayerTeam(uuid) : null;
            int teamColor = team != null ? teamNameToColor(team) : 0xFFFFFFFF;

            int kills   = session.getKills(uuid);
            int deaths  = session.getDeaths(uuid);
            int money   = session.getMoney(uuid);
            boolean alive = !session.isEliminated(uuid);

            byte bedStatus;
            if (!session.hasPlacedBed(uuid))      bedStatus = TabStatsPacket.BED_NONE;
            else if (session.isBedBroken(uuid))   bedStatus = TabStatsPacket.BED_BROKEN;
            else                                   bedStatus = TabStatsPacket.BED_INTACT;

            entries.add(new TabStatsPacket.PlayerEntry(
                    uuid, name, team, teamColor,
                    kills, deaths, money, bedStatus, alive));
        }

        TabStatsPacket pkt = new TabStatsPacket(mode, entries);
        for (UUID uuid : session.getPlayers()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) PacketHandler.sendToClient(sp, pkt);
        }
    }

    private static int teamNameToColor(String team) {
        return switch (team) {
            case "Team 1" -> 0xFFFF5555;
            case "Team 2" -> 0xFF5555FF;
            case "Team 3" -> 0xFF55FF55;
            case "Team 4" -> 0xFFFFFF55;
            case "Team 5" -> 0xFFFF55FF;
            case "Team 6" -> 0xFFFFAA00;
            default       -> 0xFFFFFFFF;
        };
    }
}