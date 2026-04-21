package com.frosty.bedgunwars.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class GameCleanupManager {

    public static void restoreAndEnd(MinecraftServer server, GameSession session, String endMessage) {
        // Remove boss bar
        BossBarManager.remove(server);

        // Remove all bed blocks placed during the game
        removeGameBeds(session);

        // Restore world border
        BorderManager.restoreBorder(session);

        // Restore all player states using saved snapshots
        for (UUID uuid : session.getSavedSnapshots().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            session.restorePlayerState(player);
        }

        session.end();
        GameManager.end();
        broadcast(server, endMessage);
    }

    private static void removeGameBeds(GameSession session) {
        for (UUID uuid : session.getPlayers()) {
            BlockPos footPos = session.getPlayerBed(uuid);
            if (footPos == null) continue;

            BlockState footState = session.getLevel().getBlockState(footPos);
            if (!(footState.getBlock() instanceof BedBlock)) continue;

            // Remove foot block
            session.getLevel().setBlock(footPos, Blocks.AIR.defaultBlockState(), 3);

            // Remove head block (one block in the facing direction from foot)
            Direction facing = footState.getValue(BedBlock.FACING);
            BlockPos headPos = footPos.relative(facing);
            BlockState headState = session.getLevel().getBlockState(headPos);
            if (headState.getBlock() instanceof BedBlock) {
                session.getLevel().setBlock(headPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}