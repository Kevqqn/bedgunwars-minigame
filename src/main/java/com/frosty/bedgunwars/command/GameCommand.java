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
import java.util.UUID;

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

        GameSession session = new GameSession(level, beacon, mode, player.getUUID());
        session.setPhase(GamePhase.STARTING);
        GameManager.start(session);

        source.sendSuccess(() -> Component.literal("Game created in " + mode.name() + " mode."), true);
        source.sendSuccess(() -> Component.literal("You are the host. Other players can now use /game join."), false);
        source.sendSuccess(() -> Component.literal("Set border with /game border <size>"), false);
        return 1;
    }

    public static int joinGame(CommandSourceStack source) {
        if (!GameManager.hasGame()) {
            source.sendFailure(Component.literal("No active game to join"));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game to join"));
            return 0;
        }
        if (session.getPhase() != GamePhase.STARTING) {
            source.sendFailure(Component.literal("You can only join before preparation starts"));
            return 0;
        }
        if (!player.serverLevel().dimension().equals(session.getLevel().dimension())) {
            source.sendFailure(Component.literal("You must be in the same dimension as the host to join"));
            return 0;
        }
        if (!session.addJoinedPlayer(player.getUUID())) {
            source.sendFailure(Component.literal("You are already in the lobby"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("You joined the game lobby"), false);
        return 1;
    }

    public static int leaveGame(CommandSourceStack source) {
        if (!GameManager.hasGame()) {
            source.sendFailure(Component.literal("No active game to leave"));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player"));
            return 0;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game to leave"));
            return 0;
        }
        if (session.getPhase() != GamePhase.STARTING) {
            source.sendFailure(Component.literal("You can only leave before preparation starts"));
            return 0;
        }
        if (player.getUUID().equals(session.getHostUuid())) {
            source.sendFailure(Component.literal("Host cannot leave the lobby. Use /game stop instead."));
            return 0;
        }
        if (!session.removeJoinedPlayer(player.getUUID())) {
            source.sendFailure(Component.literal("You are not in the lobby"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("You left the game lobby"), false);
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
        if (!isHost(source)) {
            source.sendFailure(Component.literal("Only the host can set the border"));
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
        source.sendSuccess(() -> Component.literal("Now let players join with /game join, then run /game prep <seconds>"), false);
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
        if (!isHost(source)) {
            source.sendFailure(Component.literal("Only the host can start preparation"));
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

        List<ServerPlayer> players = resolveJoinedPlayers(player.serverLevel().getServer(), session);
        if (players.isEmpty()) {
            source.sendFailure(Component.literal("No valid players found to start"));
            return 0;
        }

        session.resetMatchState();
        session.setPrepTimeSeconds(seconds);
        session.setMatchStartPlayerCount(players.size());

        for (ServerPlayer target : players) {
            session.savePlayerState(target);
        }

        assignPlayers(session, players, session.getMode());
        teleportPlayersToBeacon(players, session.getLevel(), session.getBeaconPos());
        giveStarterItems(players);

        session.setPhase(GamePhase.PREPARATION);
        source.sendSuccess(() -> Component.literal("Preparation phase started for " + seconds + " seconds."), true);
        broadcast(player.serverLevel().getServer(), "Preparation phase started! Place your bed now.");
        return 1;
    }

    public static int setMatchTimer(CommandSourceStack source, int seconds) {
        if (!GameManager.hasGame()) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }
        if (seconds <= 0) {
            source.sendFailure(Component.literal("Match timer must be greater than 0"));
            return 0;
        }
        if (!isHost(source)) {
            source.sendFailure(Component.literal("Only the host can set the match timer"));
            return 0;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }

        session.setMatchTimeSeconds(seconds);
        source.sendSuccess(() -> Component.literal("Match timer set to " + seconds + " seconds."), true);
        return 1;
    }

    public static int stopGame(CommandSourceStack source) {
        if (!GameManager.hasGame()) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }
        if (!isHost(source)) {
            source.sendFailure(Component.literal("Only the host can stop the game"));
            return 0;
        }

        GameSession session = GameManager.getSession();
        if (session == null) {
            source.sendFailure(Component.literal("No active game"));
            return 0;
        }

        GameCleanupManager.restoreAndEnd(source.getServer(), session, "Game stopped. Restoring player state.");
        source.sendSuccess(() -> Component.literal("Game stopped"), true);
        return 1;
    }

    // --- Helpers ---

    static boolean isHost(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        GameSession session = GameManager.getSession();
        return session != null && player.getUUID().equals(session.getHostUuid());
    }

    private static List<ServerPlayer> resolveJoinedPlayers(MinecraftServer server, GameSession session) {
        List<ServerPlayer> players = new ArrayList<>();
        List<UUID> missing = new ArrayList<>();

        for (UUID uuid : session.getJoinedPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) { missing.add(uuid); continue; }
            if (!player.serverLevel().dimension().equals(session.getLevel().dimension())) { missing.add(uuid); continue; }
            players.add(player);
        }

        for (UUID uuid : missing) session.removeJoinedPlayer(uuid);
        players.sort(Comparator.comparing(p -> p.getGameProfile().getName()));
        return players;
    }

    private static void assignPlayers(GameSession session, List<ServerPlayer> players, GameModeType mode) {
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
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.BEACON)) return pos;
                }
        return null;
    }
}