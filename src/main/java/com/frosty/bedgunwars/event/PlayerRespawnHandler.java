package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class PlayerRespawnHandler {

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
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

        UUID uuid = player.getUUID();

        if (!session.getPlayers().contains(uuid)) {
            return;
        }

        if (session.isEliminated(uuid)) {
            player.setGameMode(GameType.SPECTATOR);
            return;
        }

        if (session.getPendingRespawnPlayers().contains(uuid)) {
            BlockPos bedPos = session.getPlayerBed(uuid);

            if (bedPos != null) {
                player.teleportTo(
                        player.serverLevel(),
                        bedPos.getX() + 0.5,
                        bedPos.getY() + 1.0,
                        bedPos.getZ() + 0.5,
                        player.getYRot(),
                        player.getXRot()
                );
            }

            session.clearPendingRespawn(uuid);
            player.setGameMode(GameType.SURVIVAL);
        }
    }
}