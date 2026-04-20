package com.frosty.bedgunwars.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.network.chat.Component;

import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GamePhase;

import java.util.UUID;

public class GameScoreboard {

    private static final String OBJ_NAME = "bedgunwars";

    public static void update(GameSession session) {
        for (UUID uuid : session.getPlayers()) {
            ServerPlayer player = session.getLevel().getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                apply(player, session);
            }
        }
    }

    private static void apply(ServerPlayer player, GameSession session) {
        Scoreboard scoreboard = player.getScoreboard();

        Objective obj = scoreboard.getObjective(OBJ_NAME);

        if (obj == null) {
            obj = scoreboard.addObjective(
                    OBJ_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.literal("BedGunWars"),
                    net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER
            );
        }

        scoreboard.setDisplayObjective(1, obj);

        clear(scoreboard, obj);

        int line = 5;

        add(scoreboard, obj, "Phase: " + session.getPhase().name(), line--);

        int total = session.getPlayers().size();
        int eliminated = session.getEliminatedPlayers().size();
        int alive = total - eliminated;

        add(scoreboard, obj, "Alive: " + alive, line--);
        add(scoreboard, obj, "Players: " + total, line--);

        if (session.getPhase() == GamePhase.PREPARATION) {
            add(scoreboard, obj, "Prep...", line--);
        }

        if (session.getPhase() == GamePhase.ACTIVE) {
            add(scoreboard, obj, "Fight!", line--);
        }
    }

    private static void add(Scoreboard scoreboard, Objective obj, String text, int score) {
        scoreboard.getOrCreatePlayerScore(text, obj).setScore(score);
    }

    private static void clear(Scoreboard scoreboard, Objective obj) {
        for (String entry : scoreboard.getObjectiveNames()) {
            scoreboard.resetPlayerScore(entry, obj);
        }
    }
}