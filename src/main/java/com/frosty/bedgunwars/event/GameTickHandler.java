package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

public class GameTickHandler {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && GameManager.hasGame()) {
            GameSession session = GameManager.getSession();
            if (session.getPhase() == GamePhase.WINNER_ANNOUNCED && session.getWinnerDelayTicks() <= 0) {
                // Match ends, clear scoreboard
                clearScoreboard(event.getServer());
            }
        }
    }

    private void clearScoreboard(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getScoreboard().clearDisplay();
    }
}