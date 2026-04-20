package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.WinManager;
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
        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        UUID uuid = player.getUUID();

        if (uuid.equals(session.getHostUuid())) {
            GameManager.end(); // host disconnect -> end
            return;
        }

        GamePhase phase = session.getPhase();

        if (phase == GamePhase.STARTING || phase == GamePhase.PREPARATION) {
            session.handlePlayerDisconnect(uuid, false);
            return;
        }

        if (phase == GamePhase.ACTIVE && !session.isEliminated(uuid)) {
            session.handlePlayerDisconnect(uuid, true);
            WinManager.checkWinner(session);
        }
    }
}