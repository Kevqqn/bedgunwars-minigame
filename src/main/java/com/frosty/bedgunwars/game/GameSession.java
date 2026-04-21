package com.frosty.bedgunwars.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

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
    private final UUID hostUuid;

    private GamePhase phase = GamePhase.STARTING;
    private boolean active = true;

    private int prepTimeTicks = 180 * 20;
    private int initialPrepTicks = 180 * 20;
    private int matchTimeTicks = 600 * 20;
    private int initialMatchTicks = 600 * 20;
    private int borderRadius = 75;
    private int winnerDelayTicks = 0;
    private String winnerName = null;
    private int matchStartPlayerCount = 0;

    private final Set<UUID> joinedPlayers = new HashSet<>();
    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, String> playerTeams = new HashMap<>();
    private final Map<UUID, BlockPos> playerBeds = new HashMap<>();
    private final Map<BlockPos, UUID> bedOwners = new HashMap<>();
    private final Set<UUID> brokenBeds = new HashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<UUID> pendingRespawnPlayers = new HashSet<>();

    private final Map<UUID, PlayerSnapshot> savedPlayerStates = new HashMap<>();

    private boolean borderSnapshotCaptured = false;
    private double originalBorderCenterX;
    private double originalBorderCenterZ;
    private double originalBorderSize;

    public GameSession(ServerLevel level, BlockPos beaconPos, GameModeType mode, UUID hostUuid) {
        this.level = level;
        this.beaconPos = beaconPos;
        this.mode = mode;
        this.hostUuid = hostUuid;
        this.joinedPlayers.add(hostUuid);
    }

    public ServerLevel getLevel() { return level; }
    public BlockPos getBeaconPos() { return beaconPos; }
    public GameModeType getMode() { return mode; }
    public UUID getHostUuid() { return hostUuid; }

    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }

    public boolean isActive() { return active; }
    public void end() { this.active = false; }

    // --- Prep timer ---
    public int getPrepTimeTicks() { return prepTimeTicks; }
    public int getInitialPrepTicks() { return initialPrepTicks; }

    public void setPrepTimeSeconds(int seconds) {
        this.prepTimeTicks = seconds * 20;
        this.initialPrepTicks = seconds * 20;
    }

    public void decreasePrepTime() {
        if (prepTimeTicks > 0) prepTimeTicks--;
    }

    // --- Match timer ---
    public int getMatchTimeTicks() { return matchTimeTicks; }
    public int getInitialMatchTicks() { return initialMatchTicks; }

    public void setMatchTimeSeconds(int seconds) {
        this.matchTimeTicks = seconds * 20;
        this.initialMatchTicks = seconds * 20;
    }

    public void decreaseMatchTime() {
        if (matchTimeTicks > 0) matchTimeTicks--;
    }

    // --- Border ---
    public int getBorderRadius() { return borderRadius; }
    public void setBorderRadius(int borderRadius) { this.borderRadius = borderRadius; }

    // --- Player counts ---
    public int getMatchStartPlayerCount() { return matchStartPlayerCount; }
    public void setMatchStartPlayerCount(int count) { this.matchStartPlayerCount = count; }

    // --- Joined players ---
    public Set<UUID> getJoinedPlayers() { return joinedPlayers; }
    public boolean addJoinedPlayer(UUID uuid) { return joinedPlayers.add(uuid); }
    public boolean removeJoinedPlayer(UUID uuid) { return joinedPlayers.remove(uuid); }
    public boolean isJoined(UUID uuid) { return joinedPlayers.contains(uuid); }

    // --- Active players ---
    public Set<UUID> getPlayers() { return players; }
    public void addPlayer(UUID uuid) { players.add(uuid); }

    // --- Teams ---
    public Map<UUID, String> getPlayerTeams() { return playerTeams; }
    public void setPlayerTeam(UUID uuid, String teamName) { playerTeams.put(uuid, teamName); }
    public String getPlayerTeam(UUID uuid) { return playerTeams.get(uuid); }

    // --- Beds ---
    public boolean hasPlacedBed(UUID uuid) { return playerBeds.containsKey(uuid); }
    public BlockPos getPlayerBed(UUID uuid) { return playerBeds.get(uuid); }
    public Map<UUID, BlockPos> getAllPlayerBeds() { return playerBeds; }

    public void setPlayerBed(UUID uuid, BlockPos footPos, BlockPos headPos) {
        playerBeds.put(uuid, footPos);
        bedOwners.put(footPos, uuid);
        bedOwners.put(headPos, uuid);
        brokenBeds.remove(uuid);
    }

    public UUID getBedOwner(BlockPos pos) { return bedOwners.get(pos); }

    public void removePlayerBed(UUID owner) {
        playerBeds.remove(owner);
        bedOwners.entrySet().removeIf(e -> e.getValue().equals(owner));
        brokenBeds.remove(owner);
    }

    public void breakBed(UUID owner) {
        brokenBeds.add(owner);
        bedOwners.entrySet().removeIf(e -> e.getValue().equals(owner));
    }

    public boolean isBedBroken(UUID uuid) { return brokenBeds.contains(uuid); }

    // --- Elimination ---
    public Set<UUID> getEliminatedPlayers() { return eliminatedPlayers; }
    public boolean isEliminated(UUID uuid) { return eliminatedPlayers.contains(uuid); }

    public void eliminatePlayer(UUID uuid) {
        eliminatedPlayers.add(uuid);
        pendingRespawnPlayers.remove(uuid);
    }

    // --- Respawn ---
    public Set<UUID> getPendingRespawnPlayers() { return pendingRespawnPlayers; }
    public void markPendingRespawn(UUID uuid) { pendingRespawnPlayers.add(uuid); }
    public void clearPendingRespawn(UUID uuid) { pendingRespawnPlayers.remove(uuid); }

    // --- Winner ---
    public String getWinnerName() { return winnerName; }

    public void setWinner(String winnerName) {
        this.winnerName = winnerName;
        this.phase = GamePhase.WINNER_ANNOUNCED;
        this.winnerDelayTicks = 60;
    }

    public void setWinnerDelay(int delay) { this.winnerDelayTicks = delay; }
    public int getWinnerDelayTicks() { return winnerDelayTicks; }

    public void decreaseWinnerDelay() {
        if (winnerDelayTicks > 0) winnerDelayTicks--;
    }

    // --- Snapshots ---
    public boolean hasSnapshot(UUID uuid) { return savedPlayerStates.containsKey(uuid); }
    public Map<UUID, PlayerSnapshot> getSavedSnapshots() { return savedPlayerStates; }

    public void savePlayerState(ServerPlayer player) {
        savedPlayerStates.putIfAbsent(player.getUUID(), PlayerSnapshot.capture(player));
    }

    public void restorePlayerState(ServerPlayer player) {
        PlayerSnapshot snapshot = savedPlayerStates.get(player.getUUID());
        if (snapshot != null) snapshot.restore(player);
    }

    // --- Border snapshot ---
    public void captureBorderState() {
        if (borderSnapshotCaptured) return;
        var border = level.getWorldBorder();
        originalBorderCenterX = border.getCenterX();
        originalBorderCenterZ = border.getCenterZ();
        originalBorderSize = border.getSize();
        borderSnapshotCaptured = true;
    }

    public boolean hasBorderSnapshot() { return borderSnapshotCaptured; }
    public double getOriginalBorderCenterX() { return originalBorderCenterX; }
    public double getOriginalBorderCenterZ() { return originalBorderCenterZ; }
    public double getOriginalBorderSize() { return originalBorderSize; }

    // --- Disconnect ---
    public void handlePlayerDisconnect(UUID uuid, boolean eliminate) {
        joinedPlayers.remove(uuid);
        players.remove(uuid);
        pendingRespawnPlayers.remove(uuid);
        if (eliminate) eliminatedPlayers.add(uuid);
        playerTeams.remove(uuid);
        if (playerBeds.containsKey(uuid)) removePlayerBed(uuid);
        brokenBeds.remove(uuid);
    }

    public void resetMatchState() {
        players.clear();
        playerTeams.clear();
        playerBeds.clear();
        bedOwners.clear();
        brokenBeds.clear();
        eliminatedPlayers.clear();
        pendingRespawnPlayers.clear();
        winnerName = null;
        winnerDelayTicks = 0;
        matchStartPlayerCount = 0;
    }

    public void assignSpawns(List<ServerPlayer> players) {
        int spacing = 3;
        int index = 0;
        for (ServerPlayer player : players) {
            int xOffset = (index % 2) * spacing;
            int zOffset = (index / 2) * spacing;
            player.teleportTo(level, beaconPos.getX() + xOffset, beaconPos.getY() + 2, beaconPos.getZ() + zOffset, player.getYRot(), player.getXRot());
            index++;
        }
    }

    // --- Inner PlayerSnapshot ---
    public static class PlayerSnapshot {
        private final ServerLevel level;
        private final double x, y, z;
        private final float yRot, xRot;
        private final GameType gameType;
        private final List<ItemStack> inventoryContents;
        private final int selectedSlot;
        private final float health;
        private final int foodLevel;
        private final float saturationLevel;

        private PlayerSnapshot(ServerLevel level, double x, double y, double z,
                               float yRot, float xRot, GameType gameType,
                               List<ItemStack> inventoryContents, int selectedSlot,
                               float health, int foodLevel, float saturationLevel) {
            this.level = level;
            this.x = x; this.y = y; this.z = z;
            this.yRot = yRot; this.xRot = xRot;
            this.gameType = gameType;
            this.inventoryContents = inventoryContents;
            this.selectedSlot = selectedSlot;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturationLevel = saturationLevel;
        }

        public static PlayerSnapshot capture(ServerPlayer player) {
            List<ItemStack> contents = new ArrayList<>();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                contents.add(player.getInventory().getItem(slot).copy());
            }
            return new PlayerSnapshot(
                    player.serverLevel(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    player.gameMode.getGameModeForPlayer(),
                    contents,
                    player.getInventory().selected,
                    player.getHealth(),
                    player.getFoodData().getFoodLevel(),
                    player.getFoodData().getSaturationLevel()
            );
        }

        public void restore(ServerPlayer player) {
            player.teleportTo(level, x, y, z, yRot, xRot);
            player.getInventory().clearContent();
            int maxSlots = Math.min(player.getInventory().getContainerSize(), inventoryContents.size());
            for (int slot = 0; slot < maxSlots; slot++) {
                player.getInventory().setItem(slot, inventoryContents.get(slot).copy());
            }
            player.getInventory().selected = selectedSlot;
            player.setGameMode(gameType);
            player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0F, health)));
            player.getFoodData().setFoodLevel(foodLevel);
            player.getFoodData().setSaturation(saturationLevel);
            player.containerMenu.broadcastChanges();
        }
    }
}