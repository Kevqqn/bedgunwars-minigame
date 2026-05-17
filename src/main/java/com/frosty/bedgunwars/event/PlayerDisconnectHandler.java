package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

@Mod.EventBusSubscriber
public class PlayerDisconnectHandler {
    @SubscribeEvent
    public static void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GameManager.hasGame()) {
            return;
        }
        UUID uuid = player.getUUID();

        GameSession session = GameManager.getSession();
        GamePhase phase = session.getPhase();

        if (uuid.equals(session.getHostUuid())) {
            session.startHostDisconnectTimer();
            // Broadcast warning to all players
            for (net.minecraft.server.level.ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.literal("§c[NOTICE] §fHost disconnected. Game will end in 60 seconds if host doesn't reconnect."));
            }
            return;
        }

        if (phase == GamePhase.STARTING || phase == GamePhase.WAITING_PLAYERS || phase == GamePhase.PREPARATION) {
            // Don't remove from session — just mark as temporarily disconnected
            session.getDisconnectedDuringPrep().add(uuid);
            return;
        }

        if (phase == GamePhase.ACTIVE && session.getPlayers().contains(uuid) && !session.isEliminated(uuid)) {
            if (!session.tryLockDisconnect(uuid)) return; // already being processed
            if (session.isBedBroken(uuid)) {
                session.handlePlayerDisconnect(uuid, true);
                WinManager.checkWinner(session);
            } else {
                session.handlePlayerDisconnect(uuid, false);
            }
            session.unlockDisconnect(uuid);
        }
    }
}