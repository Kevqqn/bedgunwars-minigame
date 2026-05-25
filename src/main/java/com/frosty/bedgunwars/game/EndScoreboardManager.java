package com.frosty.bedgunwars.game;

import com.frosty.bedgunwars.network.EndScoreboardPacket;
import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.network.TabStatsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EndScoreboardManager {

    public static final int SCOREBOARD_TICKS = 160;

    private static boolean running = false;
    private static int tick = 0;

    public static boolean isRunning() { return running; }

    // Called when MVP cutscene cleanup fires tries to start scoreboard view
    // Returns true if scoreboard view started, false if straight to cleanup
    public static boolean tryStart(MinecraftServer server, GameSession session) {
        BlockPos beacon = session.getBeaconPos();
        if (beacon == null) return false;

        ServerLevel level = session.getLevel();

        // Find closest trapped chest within 50 blocks of beacon
        BlockPos chestPos = findClosestTrappedChest(level, beacon, 50);

        // Fallback 5 blocks from beacon if no chest found
        if (chestPos == null) {
            chestPos = beacon.offset(5, 0, 0);
        }

        double camX = chestPos.getX() + 0.5;
        double camY = chestPos.getY() + 10.0;
        double camZ = chestPos.getZ() + 0.5;

        // Beacon center
        double beaconX = beacon.getX() + 0.5;
        double beaconY = beacon.getY() + 1.0;
        double beaconZ = beacon.getZ() + 0.5;

        // Build final stats packet
        TabStatsPacket stats = buildStats(server, session);

        EndScoreboardPacket pkt = new EndScoreboardPacket(
                camX, camY, camZ,
                beaconX, beaconY, beaconZ,
                stats);

        PacketHandler.sendToAllClients(server, pkt);

        running = true;
        tick = 0;
        session.setPhase(GamePhase.SCOREBOARD_VIEW);
        session.setWinnerDelay(SCOREBOARD_TICKS + 5);
        return true;
    }

    public static int getTick() { return tick; }

    public static void tick(MinecraftServer server, GameSession session) {
        if (!running) return;
        tick++;

        if (tick >= SCOREBOARD_TICKS) {
            running = false;
            PacketHandler.sendToAllClients(server, new com.frosty.bedgunwars.network.EndScoreboardStopPacket());
            session.setWinnerDelay(0);
        }
    }

    public static void reset() {
        running = false;
        tick = 0;
    }

    private static BlockPos findClosestTrappedChest(ServerLevel level, BlockPos origin, int radius) {
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (level.getBlockState(pos).getBlock() == Blocks.TRAPPED_CHEST) {
                        double dist = pos.distSqr(origin);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private static TabStatsPacket buildStats(MinecraftServer server, GameSession session) {
        String mode = session.getMode().name();
        List<TabStatsPacket.PlayerEntry> entries = new ArrayList<>();

        for (UUID uuid : session.getPlayers()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            String name = sp != null ? sp.getName().getString()
                    : session.getCachedName(uuid);

            String team = session.getMode() == GameModeType.TEAMS
                    ? session.getPlayerTeam(uuid) : null;
            int teamColor = team != null ? teamNameToColor(team) : 0xFFFFFFFF;

            int kills   = session.getKills(uuid);
            int deaths  = session.getDeaths(uuid);
            int money   = session.getMoney(uuid);
            boolean alive = !session.isEliminated(uuid);

            byte bedStatus;
            if (!session.hasPlacedBed(uuid))    bedStatus = TabStatsPacket.BED_NONE;
            else if (session.isBedBroken(uuid)) bedStatus = TabStatsPacket.BED_BROKEN;
            else                                bedStatus = TabStatsPacket.BED_INTACT;

            entries.add(new TabStatsPacket.PlayerEntry(
                    uuid, name, team, teamColor,
                    kills, deaths, money, bedStatus, alive));
        }
        return new TabStatsPacket(mode, entries);
    }

    private static int teamNameToColor(String team) {
        return switch (team) {
            case "Team 1" -> 0xFFFF5555;
            case "Team 2" -> 0xFF5555FF;
            case "Team 3" -> 0xFF55FF55;
            case "Team 4" -> 0xFFFFFF55;
            case "Team 5" -> 0xFFFF55FF;
            case "Team 6" -> 0xFFFFAA00;
            default       -> 0xFFFFFFFF;
        };
    }
}