package com.frosty.bedgunwars.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameRules;
import com.frosty.bedgunwars.ui.GameScoreboard;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.server.ServerScoreboard;
import java.util.UUID;

public class GameCleanupManager {

    public static void restoreAndEnd(MinecraftServer server, GameSession session, String endMessage) {
        BossBarManager.remove(server);
        GameScoreboard.remove(server);
        session.getKillstreakManager().reset(server);
        session.getMapRestoreManager().restore(session.getLevel());
        for (java.util.UUID uuid : session.getPlayers()) {
            net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                com.frosty.bedgunwars.command.GameCommand.unlockMovement(p);
                com.frosty.bedgunwars.network.PacketHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                        new com.frosty.bedgunwars.network.MinimapStopPacket());
            }
        }

        com.frosty.bedgunwars.command.GameCommand.clearLockedPositions();

        server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);

        removeGameBeds(session);

        BorderManager.restoreBorder(session);

        for (UUID uuid : session.getSavedSnapshots().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            session.restorePlayerState(player);
        }

        if (session.getMode() == GameModeType.TEAMS) {
            TeamManager.removeScoreboardTeams(server, session);
        }

        // Remove nametag teams
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam solo = scoreboard.getPlayerTeam("bgw_players");
        if (solo != null) scoreboard.removePlayerTeam(solo);
        for (int i = 0; i < 10; i++) {
            PlayerTeam t = scoreboard.getPlayerTeam("bgw_team_" + i);
            if (t != null) scoreboard.removePlayerTeam(t);
        }
        com.frosty.bedgunwars.game.GameSetupUI.close();
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

            // Flag 18 = block update (2) + suppress drops (16)
            session.getLevel().setBlock(footPos, Blocks.AIR.defaultBlockState(), 18);

            Direction facing = footState.getValue(BedBlock.FACING);
            BlockPos headPos = footPos.relative(facing);
            BlockState headState = session.getLevel().getBlockState(headPos);
            if (headState.getBlock() instanceof BedBlock) {
                session.getLevel().setBlock(headPos, Blocks.AIR.defaultBlockState(), 18);
            }
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}