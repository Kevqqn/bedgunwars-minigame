package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import com.frosty.bedgunwars.game.GamePhase;
import net.minecraft.world.scores.Scoreboard;

public class GameTickHandler {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        GamePhase phase = session.getPhase();

        if (phase == GamePhase.PREPARATION) {
            session.decreasePrepTime();

            int ticksLeft = session.getPrepTimeTicks();

            // Countdown announcements at 60s, 30s, 10s, 5s, 4s, 3s, 2s, 1s
            if (ticksLeft == 60 * 20 || ticksLeft == 30 * 20 || ticksLeft == 10 * 20
                    || ticksLeft == 5 * 20 || ticksLeft == 4 * 20 || ticksLeft == 3 * 20
                    || ticksLeft == 2 * 20 || ticksLeft == 1 * 20) {
                int seconds = ticksLeft / 20;
                broadcast(event.getServer(), "Game starts in " + seconds + " second" + (seconds == 1 ? "!" : "s!"));
            }

            if (ticksLeft <= 0) {
                session.setPhase(GamePhase.ACTIVE);
                broadcast(event.getServer(), "Game has started! Destroy enemy beds!");
            }
        }

        if (phase == GamePhase.WINNER_ANNOUNCED) {
            session.decreaseWinnerDelay();
            if (session.getWinnerDelayTicks() <= 0) {
                clearScoreboard(event.getServer());
            }
        }
    }

    private void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private void clearScoreboard(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getScoreboard().setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, null);
        }
    }
}