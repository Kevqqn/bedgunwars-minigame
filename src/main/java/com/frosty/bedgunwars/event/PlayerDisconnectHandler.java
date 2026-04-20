package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GameCleanupManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

public class PlayerDisconnectHandler {
    @SubscribeEvent
    public static void onPlayerDisconnect(PlayerLoggedOutEvent event) {
        ServerPlayer player = event.getEntity();
        GameSession session = GameManager.getSession();

        if (session == null || !session.isActive()) {
            return;
        }

        if (player.getUUID().equals(session.getHostUuid())) {
            // Host disconnected - end the session properly
            GameCleanupManager.restoreAndEnd(event.getEntity().getServer(), session, "Host disconnected. Game ended.");
        } else {
            // Non-host player disconnected - handle elimination or other logic
            session.eliminatePlayer(player.getUUID());
        }
    }
}