package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.Component;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.WinManager;
import com.frosty.bedgunwars.ui.GameScoreboard;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import java.util.List;

import java.util.UUID;

@Mod.EventBusSubscriber
public class PlayerReconnectHandler {
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        UUID uuid = player.getUUID();
        GamePhase phase = session.getPhase();

        if (!session.getPlayers().contains(uuid)) return;

        if (phase == GamePhase.PREPARATION) {
            // Hide this reconnecting player from all other prep players
            for (UUID otherUuid : session.getPlayers()) {
                if (otherUuid.equals(uuid)) continue;
                ServerPlayer other = player.getServer().getPlayerList().getPlayer(otherUuid);
                if (other == null) continue;
                // Hide reconnecting player from others
                other.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(uuid)));
                // Hide others from reconnecting player
                player.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(otherUuid)));
            }
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.sendSystemMessage(Component.literal("You reconnected. Game starts soon."));
            return;
        }

        if (session.isEliminated(uuid)) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("You were eliminated."));
            GameScoreboard.update(session);
            return;
        }

        if (phase == GamePhase.ACTIVE) {
            if (session.isBedBroken(uuid)) {
                session.eliminatePlayer(uuid);
                player.setGameMode(GameType.SPECTATOR);
                player.sendSystemMessage(Component.literal("Your bed was destroyed while you were gone. You are eliminated."));
                WinManager.checkWinner(session);
                return;
            }
            player.setGameMode(GameType.SURVIVAL);
            player.sendSystemMessage(Component.literal("You reconnected and are still in the match."));
            GameScoreboard.update(session);
        }
    }
}