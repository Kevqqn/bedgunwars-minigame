package com.frosty.bedgunwars.command;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.WinManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public class GameDebugCommand {

    // /game debug eliminate [name] — only kills the player, elimination handled by PlayerDeathHandler
    public static int eliminate(CommandSourceStack source, String targetName) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }
        if (!isHost(source, session)) {
            source.sendFailure(Component.literal("Only the host can use debug commands."));
            return 0;
        }

        ServerPlayer target = findInGamePlayer(source.getServer(), session, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player '" + targetName + "' is not in the game."));
            return 0;
        }
        if (session.isEliminated(target.getUUID())) {
            source.sendFailure(Component.literal(target.getName().getString() + " is already eliminated."));
            return 0;
        }

        // Only kill — PlayerDeathHandler will handle elimination logic based on bed state
        target.hurt(target.damageSources().magic(), Float.MAX_VALUE);
        source.sendSuccess(() -> Component.literal("Killed " + target.getName().getString() + " (bed state determines elimination)."), false);
        return 1;
    }

    public static int listTaczItems(CommandSourceStack source) {
        int count = 0;
        for (var entry : net.minecraftforge.registries.ForgeRegistries.ITEMS.getEntries()) {
            if (entry.getKey().location().getNamespace().equals("tacz")) {
                com.frosty.bedgunwars.BedGunWars.LOGGER.info("TACZ item: {}", entry.getKey().location());
                count++;
            }
        }
        int finalCount = count;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "Logged " + finalCount + " TACZ items to the server log."), false);
        return 1;
    }

    public static int forceBorderShrink(CommandSourceStack source, int seconds) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }

        WorldBorder border = session.getLevel().getWorldBorder();
        double currentSize = border.getSize();
        double newSize = Math.max(10, currentSize - 60.0);
        border.lerpSizeBetween(currentSize, newSize, seconds * 1000L);

        session.setEndgameBorderShrinkTicks(session.getEndgameBorderShrinkInterval());

        source.sendSuccess(() -> Component.literal("Border shrunk over " + seconds + "s: " + (int)currentSize + " -> " + (int)newSize + ". Timer reset."), false);
        return 1;
    }

    // /game debug eliminatebed [name] — breaks bed (no drop), kills; PlayerDeathHandler handles elimination
    public static int eliminateBed(CommandSourceStack source, String targetName) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }
        if (!isHost(source, session)) {
            source.sendFailure(Component.literal("Only the host can use debug commands."));
            return 0;
        }

        ServerPlayer target = findInGamePlayer(source.getServer(), session, targetName);
        if (target == null) {
            source.sendFailure(Component.literal("Player '" + targetName + "' is not in the game."));
            return 0;
        }

        UUID uuid = target.getUUID();

        // Break bed blocks without dropping items — flag 18 = update (2) + suppress drops (16)
        BlockPos footPos = session.getPlayerBed(uuid);
        if (footPos != null) {
            BlockState footState = session.getLevel().getBlockState(footPos);
            if (footState.getBlock() instanceof BedBlock) {
                Direction facing = footState.getValue(BedBlock.FACING);
                BlockPos headPos = footPos.relative(facing);
                session.getLevel().setBlock(footPos, Blocks.AIR.defaultBlockState(), 18);
                BlockState headState = session.getLevel().getBlockState(headPos);
                if (headState.getBlock() instanceof BedBlock) {
                    session.getLevel().setBlock(headPos, Blocks.AIR.defaultBlockState(), 18);
                }
            }
            session.breakBed(uuid);
            target.sendSystemMessage(Component.literal("Your bed was destroyed by the host (debug)."));
        } else {
            source.sendSuccess(() -> Component.literal(targetName + " had no placed bed — skipping bed break."), false);
        }

        // Kill — PlayerDeathHandler will see bed is broken and permanently eliminate
        target.hurt(target.damageSources().magic(), Float.MAX_VALUE);
        source.sendSuccess(() -> Component.literal(targetName + "'s bed was broken and they were killed."), false);
        return 1;
    }

    // /game debug forcewin [name]
    public static int forceWin(CommandSourceStack source, String winnerName) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }
        if (!isHost(source, session)) {
            source.sendFailure(Component.literal("Only the host can use debug commands."));
            return 0;
        }

        ServerPlayer target = findInGamePlayer(source.getServer(), session, winnerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player '" + winnerName + "' is not in the game."));
            return 0;
        }

        String displayName = target.getName().getString();

        // Send title screen to all players
        for (UUID uuid : session.getPlayers()) {
            ServerPlayer p = source.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) WinManager.sendTitle(p, displayName + " won the game!", "(Debug force-win)");
        }

        session.setWinner(displayName);
        broadcast(source.getServer(), displayName + " wins! (Debug force-win)");
        source.sendSuccess(() -> Component.literal("Force-win set for: " + displayName), false);
        return 1;
    }

    // /game debug setphase [phase]
    public static int setPhase(CommandSourceStack source, String phaseName) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }
        if (!isHost(source, session)) {
            source.sendFailure(Component.literal("Only the host can use debug commands."));
            return 0;
        }

        GamePhase phase;
        try {
            phase = GamePhase.valueOf(phaseName.toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(GamePhase.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            source.sendFailure(Component.literal("Unknown phase: '" + phaseName + "'. Valid: " + valid));
            return 0;
        }

        if (phase == GamePhase.ACTIVE && session.getMatchTimeTicks() <= 0) {
            session.setMatchTimeSeconds(600);
        }

        session.setPhase(phase);
        source.sendSuccess(() -> Component.literal("Phase set to: " + phase.name()), false);
        return 1;
    }

    // /game debug status
    public static int status(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be a player."));
            return 0;
        }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }
        if (!session.getHostUuid().equals(player.getUUID())) {
            source.sendFailure(Component.literal("Only the host can use debug commands."));
            return 0;
        }

        int alive = (int) session.getPlayers().stream().filter(u -> !session.isEliminated(u)).count();
        int total = session.getMatchStartPlayerCount();
        int prepSec = session.getPrepTimeTicks() / 20;
        int matchSec = session.getMatchTimeTicks() / 20;

        source.sendSuccess(() -> Component.literal("=== BedGunWars Debug Status ==="), false);
        source.sendSuccess(() -> Component.literal("Phase: " + session.getPhase().name()), false);
        source.sendSuccess(() -> Component.literal("Mode: " + session.getMode().name()), false);
        source.sendSuccess(() -> Component.literal("Players alive: " + alive + " / " + total), false);
        source.sendSuccess(() -> Component.literal("Prep time left: " + prepSec + "s"), false);
        source.sendSuccess(() -> Component.literal("Match time left: " + matchSec + "s"), false);
        source.sendSuccess(() -> Component.literal("Winner: " + (session.getWinnerName() != null ? session.getWinnerName() : "none")), false);
        source.sendSuccess(() -> Component.literal("Border radius: " + session.getBorderRadius()), false);
        return 1;
    }

    // --- Helpers ---

    private static boolean isHost(CommandSourceStack source, GameSession session) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        return player.getUUID().equals(session.getHostUuid());
    }

    private static ServerPlayer findInGamePlayer(MinecraftServer server, GameSession session, String name) {
        for (UUID uuid : session.getPlayers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null && p.getName().getString().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private static void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(message));
        }
    }
}