package com.frosty.bedgunwars.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameSession {
    private final ServerLevel level;
    private final BlockPos beaconPos;
    private final GameModeType mode;

    private GamePhase phase = GamePhase.STARTING;
    private boolean active = true;

    private int prepTimeTicks = 180 * 20;
    private int borderRadius = 75;
    private int winnerDelayTicks = 0;
    private String winnerName = null;

    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, String> playerTeams = new HashMap<>();
    private final Map<UUID, BlockPos> playerBeds = new HashMap<>();
    private final Map<BlockPos, UUID> bedOwners = new HashMap<>();
    private final Set<UUID> brokenBeds = new HashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<UUID> pendingRespawnPlayers = new HashSet<>();

    private final Map<UUID, PlayerSnapshot> playerSnapshots = new HashMap<>();
    private boolean borderSnapshotCaptured = false;
    private double originalBorderCenterX;
    private double originalBorderCenterZ;
    private double originalBorderSize;

    public GameSession(ServerLevel level, BlockPos beaconPos, GameModeType mode) {
        this.level = level;
        this.beaconPos = beaconPos;
        this.mode = mode;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getBeaconPos() {
        return beaconPos;
    }

    public GameModeType getMode() {
        return mode;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    public boolean isActive() {
        return active;
    }

    public void end() {
        this.active = false;
    }

    public int getPrepTimeTicks() {
        return prepTimeTicks;
    }

    public void setPrepTimeSeconds(int seconds) {
        this.prepTimeTicks = seconds * 20;
    }

    public void decreasePrepTime() {
        if (prepTimeTicks > 0) {
            prepTimeTicks--;
        }
    }

    public int getBorderRadius() {
        return borderRadius;
    }

    public void setBorderRadius(int borderRadius) {
        this.borderRadius = borderRadius;
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }

    public Map<UUID, String> getPlayerTeams() {
        return playerTeams;
    }

    public void setPlayerTeam(UUID uuid, String teamName) {
        playerTeams.put(uuid, teamName);
    }

    public String getPlayerTeam(UUID uuid) {
        return playerTeams.get(uuid);
    }

    public boolean hasPlacedBed(UUID uuid) {
        return playerBeds.containsKey(uuid);
    }

    public BlockPos getPlayerBed(UUID uuid) {
        return playerBeds.get(uuid);
    }

    public void setPlayerBed(UUID uuid, BlockPos footPos, BlockPos headPos) {
        playerBeds.put(uuid, footPos);
        bedOwners.put(footPos, uuid);
        bedOwners.put(headPos, uuid);
        brokenBeds.remove(uuid);
    }

    public UUID getBedOwner(BlockPos pos) {
        return bedOwners.get(pos);
    }

    public void removePlayerBed(UUID owner) {
        playerBeds.remove(owner);
        bedOwners.entrySet().removeIf(entry -> entry.getValue().equals(owner));
        brokenBeds.remove(owner);
    }

    public void breakBed(UUID owner) {
        brokenBeds.add(owner);
        bedOwners.entrySet().removeIf(entry -> entry.getValue().equals(owner));
    }

    public boolean isBedBroken(UUID uuid) {
        return brokenBeds.contains(uuid);
    }

    public Set<UUID> getEliminatedPlayers() {
        return eliminatedPlayers;
    }

    public boolean isEliminated(UUID uuid) {
        return eliminatedPlayers.contains(uuid);
    }

    public void eliminatePlayer(UUID uuid) {
        eliminatedPlayers.add(uuid);
        pendingRespawnPlayers.remove(uuid);
    }

    public Set<UUID> getPendingRespawnPlayers() {
        return pendingRespawnPlayers;
    }

    public void markPendingRespawn(UUID uuid) {
        pendingRespawnPlayers.add(uuid);
    }

    public void clearPendingRespawn(UUID uuid) {
        pendingRespawnPlayers.remove(uuid);
    }

    public String getWinnerName() {
        return winnerName;
    }

    public void setWinner(String winnerName) {
        this.winnerName = winnerName;
        this.phase = GamePhase.WINNER_ANNOUNCED;
        this.winnerDelayTicks = 60;
    }

    public int getWinnerDelayTicks() {
        return winnerDelayTicks;
    }

    public void decreaseWinnerDelay() {
        if (winnerDelayTicks > 0) {
            winnerDelayTicks--;
        }
    }

    public void captureBorderState() {
        if (borderSnapshotCaptured) {
            return;
        }

        WorldBorder border = level.getWorldBorder();
        originalBorderCenterX = border.getCenterX();
        originalBorderCenterZ = border.getCenterZ();
        originalBorderSize = border.getSize();
        borderSnapshotCaptured = true;
    }

    public boolean hasBorderSnapshot() {
        return borderSnapshotCaptured;
    }

    public double getOriginalBorderCenterX() {
        return originalBorderCenterX;
    }

    public double getOriginalBorderCenterZ() {
        return originalBorderCenterZ;
    }

    public double getOriginalBorderSize() {
        return originalBorderSize;
    }

    public void savePlayerState(ServerPlayer player) {
        playerSnapshots.putIfAbsent(player.getUUID(), PlayerSnapshot.capture(player));
    }

    public PlayerSnapshot getPlayerSnapshot(UUID uuid) {
        return playerSnapshots.get(uuid);
    }

    public static class PlayerSnapshot {
        private final ServerLevel level;
        private final double x;
        private final double y;
        private final double z;
        private final float yRot;
        private final float xRot;
        private final GameType gameType;
        private final List<ItemStack> inventoryContents;
        private final float health;
        private final int foodLevel;
        private final float saturationLevel;

        private PlayerSnapshot(
                ServerLevel level,
                double x,
                double y,
                double z,
                float yRot,
                float xRot,
                GameType gameType,
                List<ItemStack> inventoryContents,
                float health,
                int foodLevel,
                float saturationLevel
        ) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.gameType = gameType;
            this.inventoryContents = inventoryContents;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturationLevel = saturationLevel;
        }

        public static PlayerSnapshot capture(ServerPlayer player) {
            List<ItemStack> inventoryContents = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                inventoryContents.add(player.getInventory().getItem(slot).copy());
            }

            return new PlayerSnapshot(
                    player.serverLevel(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    player.gameMode.getGameModeForPlayer(),
                    inventoryContents,
                    player.getHealth(),
                    player.getFoodData().getFoodLevel(),
                    player.getFoodData().getSaturationLevel()
            );
        }

        public void restore(ServerPlayer player) {
            player.teleportTo(level, x, y, z, yRot, xRot);

            player.getInventory().clearContent();
            int slotCount = Math.min(player.getInventory().getContainerSize(), inventoryContents.size());
            for (int slot = 0; slot < slotCount; slot++) {
                player.getInventory().setItem(slot, inventoryContents.get(slot).copy());
            }

            player.setGameMode(gameType);
            player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0F, health)));
            player.getFoodData().setFoodLevel(foodLevel);
            player.getFoodData().setSaturation(saturationLevel);
            player.containerMenu.broadcastChanges();
        }
    }
}