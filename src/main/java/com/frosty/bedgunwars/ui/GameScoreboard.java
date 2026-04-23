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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameScoreboard {

    private static final String OBJ_NAME = "bedgunwars";

    public static void update(GameSession session) {
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

        if (session.getMode().name().equals("TEAMS")) {
            long redAlive = session.getPlayers().stream()
                    .filter(u -> !session.isEliminated(u))
                    .filter(u -> "RED".equals(session.getPlayerTeam(u)))
                    .count();
            long blueAlive = session.getPlayers().stream()
                    .filter(u -> !session.isEliminated(u))
                    .filter(u -> "BLUE".equals(session.getPlayerTeam(u)))
                    .count();
            lines.add("§cRed: §f" + redAlive);
            lines.add("§9Blue: §f" + blueAlive);
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