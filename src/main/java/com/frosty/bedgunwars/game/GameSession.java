package com.frosty.bedgunwars.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.HashSet;
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
}