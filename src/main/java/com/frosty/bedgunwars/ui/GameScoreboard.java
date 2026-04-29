package com.frosty.bedgunwars.ui;

import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import com.frosty.bedgunwars.game.TeamManager;
import com.frosty.bedgunwars.game.GameModeType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameScoreboard {

    private static final String OBJ_NAME = "bedgunwars";

    public static void update(GameSession session) {
        if (!session.isActive()) return;
        for (UUID uuid : session.getPlayers()) {
            ServerPlayer player = session.getLevel().getServer().getPlayerList().getPlayer(uuid);
            if (player != null) apply(player, session);
        }
    }

    private static void apply(ServerPlayer player, GameSession session) {
        List<String> lines = buildLines(player, session);
        Objective obj = buildDummyObjective();

        player.connection.send(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_REMOVE));
        player.connection.send(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_ADD));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(Scoreboard.DISPLAY_SLOT_SIDEBAR, obj));

        for (int i = 0; i < lines.size(); i++) {
            int score = lines.size() - i;
            player.connection.send(new ClientboundSetScorePacket(
                    net.minecraft.server.ServerScoreboard.Method.CHANGE,
                    OBJ_NAME,
                    lines.get(i),
                    score
            ));
        }
    }

    private static List<String> buildLines(ServerPlayer player, GameSession session) {
        List<String> lines = new ArrayList<>();
        GamePhase phase = session.getPhase();
        int total = session.getPlayers().size();
        int alive = total - session.getEliminatedPlayers().size();

        lines.add("§7§m-----------");

        if (phase == GamePhase.PREPARATION) {
            int secs = session.getPrepTimeTicks() / 20;
            lines.add("§ePhase: §fPreparation");
            lines.add("§eTime: §f" + formatTime(secs));
            lines.add("§7Place your bed!");
        } else if (phase == GamePhase.ACTIVE) {
            int secs = session.getMatchTimeTicks() / 20;
            lines.add("§ePhase: §fMatch");
            lines.add("§eTime: §f" + formatTime(secs));
            lines.add("§eMode: §f" + session.getMode().name());
        } else if (phase == GamePhase.ENDING) {
            int secs = session.getEndgameBorderShrinkTicks() / 20;
            lines.add("§cPhase: §fEndgame");
            lines.add("§cBorder shrinks: §f" + formatTime(secs));
            lines.add("§cNo respawns!");
        } else if (phase == GamePhase.WINNER_ANNOUNCED) {
            lines.add("§6Winner:");
            lines.add("§f" + (session.getWinnerName() != null ? session.getWinnerName() : "?"));
        }

        lines.add("§7§m----------");
        lines.add("§eAlive: §f" + alive + "§7/§f" + total);

        if (session.getMode() == GameModeType.TEAMS) {
            for (String team : TeamManager.getAliveTeams(session)) {
                String color = TeamManager.getTeamColor(team);
                UUID bedOwner = session.getTeamBedOwner(team);
                String bedStatus = bedOwner == null || session.isBedBroken(bedOwner) ? "§c✗" : "§a✓";
                long count = session.getPlayers().stream()
                        .filter(u -> !session.isEliminated(u))
                        .filter(u -> team.equals(session.getPlayerTeam(u)))
                        .count();
                lines.add(color + team + " §f" + count + " " + bedStatus);
            }
        }

        lines.add("§7§m----------");
        lines.add("§eKills");

        // Sort players: alive first, then by kill count descending
        List<UUID> sorted = session.getPlayers().stream()
                .sorted((a, b) -> {
                    boolean aAlive = !session.isEliminated(a);
                    boolean bAlive = !session.isEliminated(b);
                    if (aAlive != bAlive) return aAlive ? -1 : 1;
                    return Integer.compare(session.getKills(b), session.getKills(a));
                })
                .toList();

        for (UUID uuid : sorted) {
            ServerPlayer p = session.getLevel().getServer().getPlayerList().getPlayer(uuid);
            String name = p != null ? p.getName().getString() : uuid.toString().substring(0, 8);
            String status = session.isEliminated(uuid) ? "§8" : "§f";
            int kills = session.getKills(uuid);
            String killStr = kills > 0 ? " §e" + kills + "☠" : " §80";
            lines.add(status + name + killStr);
        }

        lines.add("§7§m-----------");

        UUID uuid = player.getUUID();
        String bedStatus = session.hasPlacedBed(uuid)
                ? (session.isBedBroken(uuid) ? "§cDestroyed" : "§aSafe")
                : "§7Not placed";
        lines.add("§eBed: " + bedStatus);
        lines.add("§7§m----------");

        return lines;
    }

    public static void remove(MinecraftServer server) {
        Objective obj = buildDummyObjective();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_REMOVE));
        }
    }

    private static Objective buildDummyObjective() {
        return new Objective(
                new Scoreboard(),
                OBJ_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal("§6§lMatch"),
                ObjectiveCriteria.RenderType.INTEGER
        );
    }

    private static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return m + ":" + String.format("%02d", s);
    }
}