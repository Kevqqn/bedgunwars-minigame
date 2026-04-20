package com.frosty.bedgunwars.event;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.WinManager;

@Mod.EventBusSubscriber
public class PlayerDisconnectHandler {

    @SubscribeEvent
    public static void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        UUID uuid = player.getUUID();

        if (uuid.equals(session.getHostUuid())) {
            GameManager.end();
            return;
        }

        GamePhase phase = session.getPhase();

        if (phase == GamePhase.STARTING) {
            if (session.isJoined(uuid)) {
                session.handlePlayerDisconnect(uuid, false);
                notify(session, "Player left lobby");
            }
            return;
        }

        if (phase == GamePhase.PREPARATION) {
            session.handlePlayerDisconnect(uuid, false);
            notify(session, "Player left during preparation");
            return;
        }

        if (phase == GamePhase.ACTIVE) {
            if (!session.isEliminated(uuid)) {
                session.handlePlayerDisconnect(uuid, true);
                notify(session, "Player disconnected and was eliminated");
                WinManager.checkWinner(session);
            }
        }
    }

    private static void notify(GameSession session, String msg) {
        for (UUID id : session.getPlayers()) {
            ServerPlayer p = session.getLevel().getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(Component.literal(msg));
            }
        }
    }
}