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

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public class GameDebugCommand {

    // /game debug eliminate [name] — eliminates a player, host included
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

        session.eliminatePlayer(target.getUUID());
        target.sendSystemMessage(Component.literal("You have been eliminated by the host (debug)."));
        source.sendSuccess(() -> Component.literal(target.getName().getString() + " has been eliminated."), false);
        WinManager.checkWinner(session);
        return 1;
    }

    // /game debug eliminatebed [name] — breaks bed in world, kills, and eliminates
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

        // Break bed blocks in the world
        BlockPos footPos = session.getPlayerBed(uuid);
        if (footPos != null) {
            BlockState footState = session.getLevel().getBlockState(footPos);
            if (footState.getBlock() instanceof BedBlock) {
                Direction facing = footState.getValue(BedBlock.FACING);
                BlockPos headPos = footPos.relative(facing);
                session.getLevel().setBlock(footPos, Blocks.AIR.defaultBlockState(), 3);
                BlockState headState = session.getLevel().getBlockState(headPos);
                if (headState.getBlock() instanceof BedBlock) {
                    session.getLevel().setBlock(headPos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            session.breakBed(uuid);
            target.sendSystemMessage(Component.literal("Your bed was destroyed by the host (debug)."));
        } else {
            source.sendSuccess(() -> Component.literal(targetName + " had no placed bed — skipping bed break."), false);
        }

        // Kill and eliminate
        target.hurt(target.damageSources().magic(), Float.MAX_VALUE);
        session.eliminatePlayer(uuid);
        target.sendSystemMessage(Component.literal("You have been eliminated by the host (debug)."));
        source.sendSuccess(() -> Component.literal(targetName + "'s bed was broken and they were eliminated."), false);
        WinManager.checkWinner(session);
        return 1;
    }

    // /game debug forcewin [name] — forces a specific in-game player to win
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
        session.setWinner(displayName);
        broadcast(source.getServer(), displayName + " wins! (Debug force-win)");
        source.sendSuccess(() -> Component.literal("Force-win set for: " + displayName), false);
        return 1;
    }

    // /game debug setphase [phase] — with validation and safe ACTIVE default timer
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

        // If forcing to ACTIVE and match timer is spent, give a safe default
        if (phase == GamePhase.ACTIVE && session.getMatchTimeTicks() <= 0) {
            session.setMatchTimeSeconds(600);
        }

        session.setPhase(phase);
        source.sendSuccess(() -> Component.literal("Phase set to: " + phase.name()), false);
        return 1;
    }

    // /game debug status — shows current game state to host
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
            if (p != null && p.getName().getString().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    private static void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(message));
        }
    }
}