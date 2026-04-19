package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class PlayerDeathHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!GameManager.hasGame()) {
            return;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            return;
        }

        if (session.getPhase() != GamePhase.ACTIVE) {
            return;
        }

        UUID uuid = player.getUUID();

        if (!session.getPlayers().contains(uuid)) {
            return;
        }

        if (session.isEliminated(uuid)) {
            return;
        }

        boolean hasPlacedBed = session.hasPlacedBed(uuid);
        boolean bedBroken = session.isBedBroken(uuid);

        if (hasPlacedBed && !bedBroken) {
            session.markPendingRespawn(uuid);
            player.sendSystemMessage(Component.literal("You will respawn. Your bed is still alive."));
        } else {
            session.eliminatePlayer(uuid);
            player.sendSystemMessage(Component.literal("You are eliminated."));
        }
    }
}