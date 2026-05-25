package com.frosty.bedgunwars.command;

import com.frosty.bedgunwars.game.*;
import com.frosty.bedgunwars.network.PacketHandler;
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

    // /game debug eliminate [name] only kills the player, elimination handled by PlayerDeathHandler
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

        // Only kill PlayerDeathHandler will handle elimination logic based on bed state
        target.hurt(target.damageSources().magic(), Float.MAX_VALUE);
        source.sendSuccess(() -> Component.literal("Killed " + target.getName().getString() + " (bed state determines elimination)."), false);
        return 1;
    }

    public static int listTaczItems(CommandSourceStack source) {
        int count = 0;
        for (var entry : com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex()) {
            String displayName = com.frosty.bedgunwars.game.GunHelper.getGunDisplayName(entry.getKey());
            com.frosty.bedgunwars.BedGunWars.LOGGER.info("TACZ gun: {} | {}", entry.getKey(), displayName);
            count++;
        }
        int finalCount = count;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "Logged " + finalCount + " TACZ gun IDs to the server log."), false);
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

    // /game debug eliminatebed [name] breaks bed (no drop), kills; PlayerDeathHandler handles elimination
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

        // Break bed blocks without dropping items flag 18 = update (2) + suppress drops (16)
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

        // Kill PlayerDeathHandler will see bed is broken and permanently eliminate
        target.hurt(target.damageSources().magic(), Float.MAX_VALUE);
        source.sendSuccess(() -> Component.literal(targetName + "'s bed was broken and they were killed."), false);
        return 1;
    }
    // grant player killstreak access for testing
    public static int giveKillstreak(CommandSourceStack source, String playerName, String killstreakName) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }

        com.frosty.bedgunwars.game.KillstreakType type;
        try {
            type = com.frosty.bedgunwars.game.KillstreakType.valueOf(killstreakName.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown killstreak: " + killstreakName));
            return 0;
        }

        net.minecraft.server.level.ServerPlayer target =
                source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found: " + playerName));
            return 0;
        }

        session.getKillstreakManager().award(target.getUUID(), type, source.getServer());
        session.getKillstreakManager().pushState(target.getUUID(), source.getServer(), session);
        source.sendSuccess(() -> Component.literal("§aGranted §e" + type.displayName + " §ato §f" + playerName), false);
        return 1;
    }

    public static int spawnJet(CommandSourceStack source) {
        try {
            net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
            float speed = com.frosty.bedgunwars.entity.JetEntity.SPEED;
            // Spawn above player, flying in the direction they're looking
            float yawRad = (float) Math.toRadians(player.getYRot());
            double dx = -Math.sin(yawRad);
            double dz = Math.cos(yawRad);
            PacketHandler.sendToClient(player,
                    new com.frosty.bedgunwars.network.SpawnJetPacket(
                            player.getX(), player.getY() + 20, player.getZ(),
                            dx / speed, dz / speed, speed, 1));
            source.sendSuccess(() -> Component.literal("§aJet spawned above you."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    public static int despawnJets(CommandSourceStack source) {
        // Send clear packet to all players
        for (net.minecraft.server.level.ServerPlayer p :
                source.getServer().getPlayerList().getPlayers()) {
            PacketHandler.sendToClient(p,
                    new com.frosty.bedgunwars.network.MinimapStopPacket()); // clearAll is called in MinimapStopPacket
        }
        source.sendSuccess(() -> Component.literal("§aCleared all jets."), false);
        return 1;
    }

    public static int deathmatchStatus(CommandSourceStack source) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }
        if (!session.isDeathmatch()) {
            source.sendFailure(Component.literal("Not a deathmatch session."));
            return 0;
        }
        var dm = session.getDeathmatchManager();
        source.sendSuccess(() -> Component.literal("§eAll beacons (" + dm.getAllBeacons().size() + "):"), false);
        for (net.minecraft.core.BlockPos b : dm.getAllBeacons()) {
            source.sendSuccess(() -> Component.literal("  §f[" + b.getX() + ", " + b.getY() + ", " + b.getZ() + "]"), false);
        }
        source.sendSuccess(() -> Component.literal("§eTeam beacons:"), false);
        dm.getTeamBeacons().forEach((team, pos) ->
                source.sendSuccess(() -> Component.literal("  §f" + team + " -> [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]"), false)
        );
        source.sendSuccess(() -> Component.literal("§eBorder center: §f" +
                (int)session.getLevel().getWorldBorder().getCenterX() + ", " +
                (int)session.getLevel().getWorldBorder().getCenterZ()), false);
        source.sendSuccess(() -> Component.literal("§eBeaconPos (host pos): §f" +
                session.getBeaconPos().getX() + ", " +
                session.getBeaconPos().getY() + ", " +
                session.getBeaconPos().getZ()), false);
        source.sendSuccess(() -> Component.literal("§eBorder radius: §f" + session.getBorderRadius()), false);
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
        WinManager.announceWinner(session, displayName);
        session.setPhase(GamePhase.WINNER_ANNOUNCED);
        source.sendSuccess(() -> Component.literal("Force-win triggered for: " + displayName), false);
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
        // Unlock movement if forcing out of prep in deathmatch
        if (session.isDeathmatch() && phase == GamePhase.ACTIVE) {
            for (java.util.UUID uuid : session.getPlayers()) {
                net.minecraft.server.level.ServerPlayer p = source.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) GameCommand.unlockMovement(p);
            }
        }
        // For deathmatch skip ENDING phase not valid
        if (session.isDeathmatch() && phase == GamePhase.ENDING) {
            source.sendFailure(Component.literal("Use WINNER_ANNOUNCED instead."));
            return 0;
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

    public static int setDebugLogging(CommandSourceStack source, int enabled) {
        com.frosty.bedgunwars.BedGunWars.debugLogging = enabled == 1;
        source.sendSuccess(() -> Component.literal(
                "Debug logging " + (com.frosty.bedgunwars.BedGunWars.debugLogging ? "§aenabled" : "§cdisabled")), false);
        return 1;
    }

    // start mvp cutscene
    public static int startMvpCutscene(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (MvpCutsceneManager.isRunning()) {
                source.sendFailure(Component.literal("MVP cutscene already running."));
                return 0;
            }
            MvpCutsceneManager.startDebug(source.getServer(), player);
            source.sendSuccess(() -> Component.literal("§aMVP cutscene started (debug)."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    public static int startMvpCutsceneWithCamera(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (MvpCutsceneManager.isRunning()) {
                source.sendFailure(Component.literal("MVP cutscene already running."));
                return 0;
            }
            MvpCutsceneManager.startDebugWithCamera(source.getServer(), player);
            source.sendSuccess(() -> Component.literal("§aMVP cutscene started with camera (debug)."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    public static int endMvpCutscene(CommandSourceStack source) {
        if (!MvpCutsceneManager.isRunning()) {
            source.sendFailure(Component.literal("No MVP cutscene running."));
            return 0;
        }
        MvpCutsceneManager.endDebug(source.getServer());
        source.sendSuccess(() -> Component.literal("§aMVP cutscene ended."), false);
        return 1;
    }

    public static int giveMoney(CommandSourceStack source, String targetName, int amount) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }
        if (!isHost(source, session)) {
            source.sendFailure(Component.literal("Only the host can use debug commands."));
            return 0;
        }

        if (targetName.equals("@a")) {
            for (UUID uuid : session.getPlayers()) {
                session.addMoney(uuid, amount);
                ServerPlayer p = source.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) p.sendSystemMessage(Component.literal("§a+$" + amount + " §7(Debug)"));
            }
            source.sendSuccess(() -> Component.literal("Gave $" + amount + " to all players."), false);
        } else {
            ServerPlayer target = findInGamePlayer(source.getServer(), session, targetName);
            if (target == null) {
                source.sendFailure(Component.literal("Player '" + targetName + "' is not in the game."));
                return 0;
            }
            session.addMoney(target.getUUID(), amount);
            target.sendSystemMessage(Component.literal("§a+$" + amount + " §7(Debug)"));
            source.sendSuccess(() -> Component.literal("Gave $" + amount + " to " + target.getName().getString() + "."), false);
        }
        return 1;
    }
}