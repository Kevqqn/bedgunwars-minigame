package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.BossBarManager;
import com.frosty.bedgunwars.game.GameCleanupManager;
import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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
            int initialTicks = session.getInitialPrepTicks();
            int secondsLeft = ticksLeft / 20;

            float progress = initialTicks > 0 ? (float) ticksLeft / initialTicks : 0f;
            BossBarManager.show(event.getServer(), "Preparation: " + secondsLeft + "s remaining", progress);

            if (ticksLeft == 60 * 20 || ticksLeft == 30 * 20 || ticksLeft == 10 * 20
                    || ticksLeft == 5 * 20 || ticksLeft == 4 * 20 || ticksLeft == 3 * 20
                    || ticksLeft == 2 * 20 || ticksLeft == 1 * 20) {
                broadcast(event.getServer(), "Game starts in " + secondsLeft + " second" + (secondsLeft == 1 ? "!" : "s!"));
            }

            if (ticksLeft <= 0) {
                session.setPhase(GamePhase.ACTIVE);
                broadcast(event.getServer(), "Game has started! Destroy enemy beds!");
            }
        }

        else if (phase == GamePhase.ACTIVE) {
            session.decreaseMatchTime();
            int ticksLeft = session.getMatchTimeTicks();
            int initialTicks = session.getInitialMatchTicks();
            int secondsLeft = ticksLeft / 20;

            float progress = initialTicks > 0 ? (float) ticksLeft / initialTicks : 0f;
            BossBarManager.show(event.getServer(), "Match: " + formatTime(secondsLeft) + " remaining", progress);

            if (ticksLeft == 60 * 20 || ticksLeft == 30 * 20 || ticksLeft == 10 * 20
                    || ticksLeft == 5 * 20 || ticksLeft == 4 * 20 || ticksLeft == 3 * 20
                    || ticksLeft == 2 * 20 || ticksLeft == 1 * 20) {
                broadcast(event.getServer(), "Match ends in " + secondsLeft + " second" + (secondsLeft == 1 ? "!" : "s!"));
            }

            if (ticksLeft <= 0) {
                GameCleanupManager.restoreAndEnd(event.getServer(), session, "Time's up! No winner — game over.");
            }
        }

        else if (phase == GamePhase.WINNER_ANNOUNCED) {
            session.decreaseWinnerDelay();
            if (session.getWinnerDelayTicks() <= 0) {
                GameCleanupManager.restoreAndEnd(
                        event.getServer(), session,
                        session.getWinnerName() + " wins! Game over."
                );
            }
        }
    }

    private String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return m + ":" + String.format("%02d", s);
    }

    private void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}