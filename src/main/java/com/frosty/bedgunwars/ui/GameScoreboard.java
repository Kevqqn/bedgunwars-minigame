package com.frosty.bedgunwars.ui;

import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

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
        Scoreboard scoreboard = player.getScoreboard();
        Objective obj = scoreboard.getObjective(OBJ_NAME);

        if (obj == null) {
            obj = scoreboard.addObjective(
                    OBJ_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("§6§lGame"),
                    ObjectiveCriteria.RenderType.INTEGER
            );
        }

        scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, obj);
        clear(scoreboard, obj);

        GamePhase phase = session.getPhase();
        int total = session.getPlayers().size();
        int alive = total - session.getEliminatedPlayers().size();
        int line = 15;

        add(scoreboard, obj, "§7§m-----------", line--);

        if (phase == GamePhase.PREPARATION) {
            int secs = session.getPrepTimeTicks() / 20;
            add(scoreboard, obj, "§ePhase: §fPreparation", line--);
            add(scoreboard, obj, "§eTime: §f" + formatTime(secs), line--);
            add(scoreboard, obj, "§7Place your bed!", line--);
        } else if (phase == GamePhase.ACTIVE) {
            int secs = session.getMatchTimeTicks() / 20;
            add(scoreboard, obj, "§ePhase: §fMatch", line--);
            add(scoreboard, obj, "§eTime: §f" + formatTime(secs), line--);
            add(scoreboard, obj, "§eMode: §f" + session.getMode().name(), line--);
        } else if (phase == GamePhase.ENDING) {
            int secs = session.getEndgameBorderShrinkTicks() / 20;
            add(scoreboard, obj, "§cPhase: §fEndgame", line--);
            add(scoreboard, obj, "§cBorder shrinks: §f" + formatTime(secs), line--);
            add(scoreboard, obj, "§cNo respawns!", line--);
        } else if (phase == GamePhase.WINNER_ANNOUNCED) {
            add(scoreboard, obj, "§6Winner:", line--);
            add(scoreboard, obj, "§f" + (session.getWinnerName() != null ? session.getWinnerName() : "?"), line--);
        }

        add(scoreboard, obj, "§7§m-----------", line--);
        add(scoreboard, obj, "§eAlive: §f" + alive + "§7/§f" + total, line--);

        if (session.getMode().name().equals("TEAMS")) {
            long redAlive = session.getPlayers().stream()
                    .filter(u -> !session.isEliminated(u))
                    .filter(u -> "RED".equals(session.getPlayerTeam(u)))
                    .count();
            long blueAlive = session.getPlayers().stream()
                    .filter(u -> !session.isEliminated(u))
                    .filter(u -> "BLUE".equals(session.getPlayerTeam(u)))
                    .count();
            add(scoreboard, obj, "§cRed: §f" + redAlive, line--);
            add(scoreboard, obj, "§9Blue: §f" + blueAlive, line--);
        }

        add(scoreboard, obj, "§7§m-----------", line--);
        String bedStatus = session.hasPlacedBed(player.getUUID())
                ? (session.isBedBroken(player.getUUID()) ? "§cDestroyed" : "§aSafe")
                : "§7Not placed";
        add(scoreboard, obj, "§eBed: " + bedStatus, line--);
        add(scoreboard, obj, "§7§m-----------", line);
    }

    public static void remove(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Scoreboard scoreboard = player.getScoreboard();
            Objective obj = scoreboard.getObjective(OBJ_NAME);
            if (obj != null) {
                scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, null);
                scoreboard.removeObjective(obj);
            }
        }
    }

    private static void add(Scoreboard scoreboard, Objective obj, String text, int score) {
        scoreboard.getOrCreatePlayerScore(text, obj).setScore(score);
    }

    private static void clear(Scoreboard scoreboard, Objective obj) {
        for (String entry : new java.util.ArrayList<>(scoreboard.getObjectiveNames())) {
            scoreboard.resetPlayerScore(entry, obj);
        }
    }

    private static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return m + ":" + String.format("%02d", s);
    }
}