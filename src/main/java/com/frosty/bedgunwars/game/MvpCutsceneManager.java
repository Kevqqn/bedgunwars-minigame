package com.frosty.bedgunwars.game;

import com.frosty.bedgunwars.event.GameTickHandler;
import com.frosty.bedgunwars.network.MvpEndPacket;
import com.frosty.bedgunwars.network.MvpPacket;
import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.entity.MvpCharacterEntity;
import com.frosty.bedgunwars.entity.MvpGunEntity;
import com.frosty.bedgunwars.BedGunWars;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class MvpCutsceneManager {

    // Animation is 136 ticks long
    // Session flow: spawn at tick 10, animation ends at 146, cleanup at 166 (1s buffer)
    private static final int SPAWN_TICK = 10;
    private static final int ANIM_END_TICK = SPAWN_TICK + 136; // = 146
    private static final int CLEANUP_TICK = ANIM_END_TICK + 20; // = 166, 1s buffer after anim

    // Debug flow: spawn immediately at tick 0, cleanup at 156
    private static final int DEBUG_CLEANUP_TICK = 130 + 4; //

    private static int cutsceneTick = 0;
    private static boolean running = false;
    private static MvpCharacterEntity characterEntity = null;
    private static MvpGunEntity gunEntity = null;

    public static void start(MinecraftServer server, GameSession session, UUID winnerUUID) {
        cutsceneTick = 0;
        running = true;
        session.setWinnerDelay(CLEANUP_TICK + 5);
    }

    public static void tick(MinecraftServer server, GameSession session) {
        if (!running) return;
        cutsceneTick++;

        if (cutsceneTick == SPAWN_TICK) {
            spawnEntities(server, session);
        }

        if (cutsceneTick == CLEANUP_TICK) {
            sendEnd(server);
            cleanup(server, session);
        }
    }

    private static BlockPos findMvpStage(ServerLevel level, BlockPos beacon, int radius) {
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = beacon.offset(x, y, z);
                    if (level.getBlockState(pos).getBlock() == net.minecraft.world.level.block.Blocks.SPAWNER) {
                        double dist = pos.distSqr(beacon);
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

    private static void spawnEntities(MinecraftServer server, GameSession session) {
        ServerLevel level = session.getLevel();
        BlockPos beacon = session.getBeaconPos();

        // Find MVP stage spawner, fallback to beacon position
        BlockPos stagePos = findMvpStage(level, beacon, 50);
        double x, y, z;
        if (stagePos != null) {
            x = stagePos.getX() + 0.5;
            y = stagePos.getY() + 2;
            z = stagePos.getZ() + 0.5;
        } else {
            x = beacon.getX() + 5.5;
            y = beacon.getY() - 1;
            z = beacon.getZ() + 0.5;
        }

        UUID winnerUUID = session.getWinnerUUID();
        if (winnerUUID == null) return;

        ServerPlayer winner = server.getPlayerList().getPlayer(winnerUUID);
        boolean isSlim = detectSlim(winner);

        characterEntity = new MvpCharacterEntity(BedGunWars.MVP_CHARACTER.get(), level);
        characterEntity.setPos(x, y, z);
        characterEntity.setYRot(0f);
        level.addFreshEntity(characterEntity);

        gunEntity = new MvpGunEntity(BedGunWars.MVP_GUN.get(), level);
        gunEntity.setPos(x, y, z);
        gunEntity.setYRot(0f);
        level.addFreshEntity(gunEntity);

        int winnerKills = session.getKills(winnerUUID);
        String winnerName = winner != null ? winner.getName().getString() : session.getWinnerName();
        long gameTick = level.getGameTime();

        MvpPacket packet = new MvpPacket(winnerUUID, winnerName, winnerKills, gameTick, isSlim, true);
        PacketHandler.sendToAllClients(server, packet);
    }

    private static void sendEnd(MinecraftServer server) {
        PacketHandler.sendToAllClients(server, new MvpEndPacket());
    }

    private static void cleanup(MinecraftServer server, GameSession session) {
        running = false;
        if (characterEntity != null) { characterEntity.discard(); characterEntity = null; }
        if (gunEntity != null) { gunEntity.discard(); gunEntity = null; }
        // Try to start scoreboard view — if it fails, go straight to cleanup
        boolean started = com.frosty.bedgunwars.game.EndScoreboardManager.tryStart(server, session);
        if (!started) {
            session.setWinnerDelay(0);
        }
    }

    private static boolean detectSlim(ServerPlayer player) {
        if (player == null) return false;
        com.mojang.authlib.GameProfile profile = player.getGameProfile();
        com.mojang.authlib.properties.Property texProp = profile.getProperties().get("textures")
                .stream().findFirst().orElse(null);
        if (texProp == null) return false;
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(texProp.getValue()));
            return decoded.contains("\"slim\"");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRunning() { return running; }

    public static void startDebug(MinecraftServer server, ServerPlayer invoker) {
        cutsceneTick = 0;
        running = true;

        ServerLevel level = invoker.serverLevel();

        // Debug has no session, so no beacon — spawn at invoker position
        double x = invoker.getX();
        double y = invoker.getY();
        double z = invoker.getZ();

        MvpCharacterEntity character = new MvpCharacterEntity(BedGunWars.MVP_CHARACTER.get(), level);
        character.setPos(x, y, z);
        character.setYRot(0f);
        level.addFreshEntity(character);
        characterEntity = character;

        MvpGunEntity gun = new MvpGunEntity(BedGunWars.MVP_GUN.get(), level);
        gun.setPos(x, y, z);
        gun.setYRot(0f);
        level.addFreshEntity(gun);
        gunEntity = gun;

        boolean isSlim = detectSlim(invoker);
        long gameTick = level.getGameTime();
        MvpPacket packet = new MvpPacket(invoker.getUUID(), invoker.getName().getString(), -1, gameTick, isSlim, false);
        PacketHandler.sendToAllClients(server, packet);

        GameTickHandler.scheduleTask(DEBUG_CLEANUP_TICK, () -> {
            if (!running) return;
            PacketHandler.sendToAllClients(server, new MvpEndPacket());
            PacketHandler.sendToAllClients(server, new com.frosty.bedgunwars.network.MvpDebugEndPacket());
            if (characterEntity != null && !characterEntity.isRemoved()) characterEntity.discard();
            if (gunEntity != null && !gunEntity.isRemoved()) gunEntity.discard();
            characterEntity = null;
            gunEntity = null;
            running = false;
        });
    }

    public static void startDebugWithCamera(MinecraftServer server, ServerPlayer invoker) {
        cutsceneTick = 0;
        running = true;

        ServerLevel level = invoker.serverLevel();
        double x = invoker.getX();
        double y = invoker.getY();
        double z = invoker.getZ();

        MvpCharacterEntity character = new MvpCharacterEntity(BedGunWars.MVP_CHARACTER.get(), level);
        character.setPos(x, y, z);
        character.setYRot(0f);
        level.addFreshEntity(character);
        characterEntity = character;

        MvpGunEntity gun = new MvpGunEntity(BedGunWars.MVP_GUN.get(), level);
        gun.setPos(x, y, z);
        gun.setYRot(0f);
        level.addFreshEntity(gun);
        gunEntity = gun;

        boolean isSlim = detectSlim(invoker);
        long gameTick = level.getGameTime();
        MvpPacket packet = new MvpPacket(invoker.getUUID(), invoker.getName().getString(), -1, gameTick, isSlim, true);
        PacketHandler.sendToAllClients(server, packet);

        GameTickHandler.scheduleTask(DEBUG_CLEANUP_TICK, () -> {
            if (!running) return;
            PacketHandler.sendToAllClients(server, new MvpEndPacket());
            PacketHandler.sendToAllClients(server, new com.frosty.bedgunwars.network.MvpDebugEndPacket());
            if (characterEntity != null && !characterEntity.isRemoved()) characterEntity.discard();
            if (gunEntity != null && !gunEntity.isRemoved()) gunEntity.discard();
            characterEntity = null;
            gunEntity = null;
            running = false;
        });
    }

    public static void reset() {
        running = false;
        cutsceneTick = 0;
        characterEntity = null;
        gunEntity = null;
        com.frosty.bedgunwars.game.EndScoreboardManager.reset();
    }

    public static void endDebug(MinecraftServer server) {
        if (!running) return;
        PacketHandler.sendToAllClients(server, new MvpEndPacket());
        PacketHandler.sendToAllClients(server, new com.frosty.bedgunwars.network.MvpDebugEndPacket());
        try {
            if (characterEntity != null && !characterEntity.isRemoved()) characterEntity.discard();
            if (gunEntity != null && !gunEntity.isRemoved()) gunEntity.discard();
        } catch (Exception ignored) {}
        reset();
    }
}