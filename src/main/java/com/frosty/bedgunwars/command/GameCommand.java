package com.frosty.bedgunwars.command;

import com.frosty.bedgunwars.game.BorderManager;
import com.frosty.bedgunwars.game.GameCleanupManager;
import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameModeType;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GameCommand {
    public static int startGame(CommandSourceStack source, GameModeType mode) {
        if (GameManager.hasGame()) {
            source.sendFailure(Component.literal("Game already running"));
            return 0;
        }

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos beacon = findNearestBeacon(level, player.blockPosition(), 10);
        if (beacon == null) {
            source.sendFailure(Component.literal("No beacon nearby"));
            return 0;
        }

        GameSession session = new GameSession(level, beacon, mode);
        session.setPhase(GamePhase.STARTING);
        GameManager.start(session);

        source.sendSuccess(() -> Component.literal("Game created in " + mode.name() + " mode."), true);
        source.sendSuccess(() -> Component.literal("Set border with /game border <size>"), false);
        return 1;
    }

    public static int setBorder(CommandSourceStack source, int size) {
        if (!GameManager.hasGame()) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }

        if (size <= 10) {
            source.sendFailure(Component.literal("Border size must be greater than 10"));
            return 0;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }

        if (session.getPhase() != GamePhase.STARTING) {
            source.sendFailure(Component.literal("Border can only be set during STARTING phase"));
            return 0;
        }

        session.setBorderRadius(size);
        BorderManager.applyBorder(session);

        source.sendSuccess(() -> Component.literal("Border radius set to " + size), true);
        source.sendSuccess(() -> Component.literal("Now set prep time with /game prep <seconds>"), false);
        return 1;
    }

    public static int setPrep(CommandSourceStack source, int seconds) {
        if (!GameManager.hasGame()) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }

        if (seconds <= 0) {
            source.sendFailure(Component.literal("Prep time must be greater than 0"));
            return 0;
        }

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }

        if (session.getPhase() != GamePhase.STARTING) {
            source.sendFailure(Component.literal("Prep can only be set during STARTING phase"));
            return 0;
        }

        List<ServerPlayer> players = new ArrayList<>(player.serverLevel().getServer().getPlayerList().getPlayers());
        if (players.isEmpty()) {
            source.sendFailure(Component.literal("No players found"));
            return 0;
        }

        session.setPrepTimeSeconds(seconds);
        savePlayerStates(session, players);
        assignPlayers(session, players, session.getMode());
        teleportPlayersToBeacon(players, session.getLevel(), session.getBeaconPos());
        giveStarterItems(players);

        session.setPhase(GamePhase.PREPARATION);
        source.sendSuccess(() -> Component.literal("Preparation phase started for " + seconds + " seconds."), true);
        broadcast(player.serverLevel().getServer(), "Preparation phase started! Place your bed now.");
        return 1;
    }

    public static int stopGame(CommandSourceStack source) {
        if (!GameManager.hasGame()) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }

        GameSession session = GameManager.getSession();
        if (session == null) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }

        GameCleanupManager.restoreAndEnd(
                source.getServer(),
                session,
                "Game stopped. Restoring previous player state."
        );

        source.sendSuccess(() -> Component.literal("Game stopped"), true);
        return 1;
    }

    private static void savePlayerStates(GameSession session, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            session.savePlayerState(player);
        }
    }

    private static void assignPlayers(GameSession session, List<ServerPlayer> players, GameModeType mode) {
        players.sort(Comparator.comparing(p -> p.getGameProfile().getName()));

        if (mode == GameModeType.SOLO) {
            for (ServerPlayer player : players) {
                session.addPlayer(player.getUUID());
                session.setPlayerTeam(player.getUUID(), player.getGameProfile().getName());
            }
            return;
        }

        boolean toggle = false;
        for (ServerPlayer player : players) {
            session.addPlayer(player.getUUID());
            session.setPlayerTeam(player.getUUID(), toggle ? "RED" : "BLUE");
            toggle = !toggle;
        }
    }

    private static void teleportPlayersToBeacon(List<ServerPlayer> players, ServerLevel level, BlockPos beacon) {
        int index = 0;
        for (ServerPlayer player : players) {
            double x = beacon.getX() + 0.5 + (index % 4);
            double y = beacon.getY() + 2;
            double z = beacon.getZ() + 0.5 + (index / 4);

            player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
            index++;
        }
    }

    private static void giveStarterItems(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(Items.RED_BED, 1));
            player.getInventory().add(new ItemStack(Items.COOKED_BEEF, 16));
            player.getInventory().add(new ItemStack(Items.STONE_SWORD, 1));
            player.containerMenu.broadcastChanges();
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private static BlockPos findNearestBeacon(ServerLevel level, BlockPos origin, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.BEACON)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}