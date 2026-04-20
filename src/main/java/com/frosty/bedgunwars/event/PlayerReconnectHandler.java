package com.frosty.bedgunwars.event;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.Component;

import java.util.UUID;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GamePhase;
// bruh
@Mod.EventBusSubscriber
public class PlayerReconnectHandler {

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        UUID uuid = player.getUUID();

        GamePhase phase = session.getPhase();

        if (phase == GamePhase.IDLE || phase == GamePhase.ENDING) return;

        if (session.isEliminated(uuid)) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("You were eliminated"));
            return;
        }

        if (session.getPlayers().contains(uuid)) {
            restoreToMatch(session, player);
            return;
        }
    }

    private static void restoreToMatch(GameSession session, ServerPlayer player) {
        if (session.getPendingRespawnPlayers().contains(player.getUUID())) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("Respawning soon"));
            return;
        }

        player.setGameMode(GameType.SURVIVAL);
        player.sendSystemMessage(Component.literal("Rejoined match"));
    }

    private static void restoreFromSnapshot(GameSession session, ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Rejoined"));
    }
}