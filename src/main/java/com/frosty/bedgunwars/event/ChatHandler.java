package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber
public class ChatHandler {

    private static final String HANDLER_KEY = "bedgunwars_chat";

    // inject pipeline handler on login
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        player.connection.connection.channel().pipeline().addBefore(
                "packet_handler",
                HANDLER_KEY,
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof ServerboundChatPacket packet) {
                            // Schedule this on server thread, netty not here
                            server.execute(() -> handleChat(player, packet.message(), server));
                        }
                        ctx.fireChannelRead(msg);
                    }

                    @Override
                    public boolean isSharable() { return false; }
                }
        );
    }

    // remove pipeline handler on logout to prevent leaks
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            var pipeline = player.connection.connection.channel().pipeline();
            if (pipeline.get(HANDLER_KEY) != null) {
                pipeline.remove(HANDLER_KEY);
            }
        } catch (Exception ignored) {}
    }

    private static void handleChat(ServerPlayer player, String message, MinecraftServer server) {
        if (com.frosty.bedgunwars.game.GameSetupUI.onChatMessage(player, message)) return;

        if (!GameManager.hasGame()) {
            broadcastVanilla(player, message, server);
            return;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            broadcastVanilla(player, message, server);
            return;
        }

        // during STARTING/WAITING_PLAYERS treat as normal chat
        if (session.getPhase() == GamePhase.STARTING
                || session.getPhase() == GamePhase.WAITING_PLAYERS) {
            broadcastVanilla(player, message, server);
            return;
        }

        UUID uuid = player.getUUID();
        boolean isInGame = session.getPlayers().contains(uuid);
        boolean isSpectator = isInGame && session.isEliminated(uuid);
        boolean isActivePlayer = isInGame && !isSpectator;

        if (isSpectator) {
            // spectator chat only spectators and ops
            String formatted = "§8[SPECTATOR] §7" + player.getName().getString() + ": " + message;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                UUID pUuid = p.getUUID();
                boolean pIsSpectator = session.getPlayers().contains(pUuid)
                        && session.isEliminated(pUuid);
                if (pIsSpectator || p.hasPermissions(2)) {
                    p.sendSystemMessage(Component.literal(formatted));
                }
            }
        } else if (isActivePlayer) {
            // in-game chat all in-game players and spectators, not non-joined
            String formatted = "§f[GAME] §e" + player.getName().getString() + ": §f" + message;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                UUID pUuid = p.getUUID();
                boolean pIsInGame = session.getPlayers().contains(pUuid);
                if (pIsInGame) {
                    p.sendSystemMessage(Component.literal(formatted));
                }
            }
        } else {
            // non-joined chat non-joined players and ops only
            String formatted = "§7[LOBBY] " + player.getName().getString() + ": " + message;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                UUID pUuid = p.getUUID();
                boolean pIsInGame = session.getPlayers().contains(pUuid);
                if (!pIsInGame || p.hasPermissions(2)) {
                    p.sendSystemMessage(Component.literal(formatted));
                }
            }
        }
    }

    // fallback broadcast matching vanilla chat appearance
    private static void broadcastVanilla(ServerPlayer player, String message, MinecraftServer server) {
        String formatted = "<" + player.getName().getString() + "> " + message;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(formatted));
        }
    }
}