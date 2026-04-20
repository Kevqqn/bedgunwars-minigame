package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.Component;

import java.util.UUID;

@Mod.EventBusSubscriber
public class PlayerReconnectHandler {
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        UUID uuid = player.getUUID();

        if (session.isEliminated(uuid)) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("You were eliminated"));
            return;
        }

        if (session.getPlayers().contains(uuid)) {
            if (session.getPendingRespawnPlayers().contains(uuid)) {
                player.setGameMode(GameType.SPECTATOR);
                player.sendSystemMessage(Component.literal("Respawning soon"));
            } else {
                player.setGameMode(GameType.SURVIVAL);
                player.sendSystemMessage(Component.literal("Rejoined match"));
            }
        }
    }
}