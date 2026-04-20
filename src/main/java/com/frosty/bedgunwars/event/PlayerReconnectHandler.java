package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.PlayerSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraft.world.level.GameType;

public class PlayerReconnectHandler {
    @SubscribeEvent
    public static void onPlayerReconnect(PlayerLoggedInEvent event) {
        ServerPlayer player = event.getEntity();
        GameSession session = GameManager.getSession();

        if (session == null || !session.isActive()) {
            return;
        }

        if (session.isEliminated(player.getUUID())) {
            // Reconnected player was eliminated - set to spectator
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("You were eliminated, and are now in spectator mode."));
        } else {
            // Restore player to match if they were in the game
            session.restorePlayerState(player);
            player.setGameMode(GameType.SURVIVAL);
            player.sendSystemMessage(Component.literal("Welcome back to the match!"));
        }
    }
}