package com.frosty.bedgunwars.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Team;

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

    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private final Set<UUID> joinedPlayers = new HashSet<>();
    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, String> playerTeams = new HashMap<>();
    private final Map<UUID, BlockPos> playerBeds = new HashMap<>();
    private final Map<BlockPos, UUID> bedOwners = new HashMap<>();
    private final Map<UUID, String> playerNameCache = new HashMap<>();

    public void cachePlayerName(UUID uuid, String name) { playerNameCache.put(uuid, name); }
    public String getCachedName(UUID uuid) { return playerNameCache.getOrDefault(uuid, uuid.toString().substring(0, 8)); }
    private final Map<UUID, Integer> bedSenseTimers = new HashMap<>();
    public void setBedSenseTimer(UUID uuid, int ticks) { bedSenseTimers.put(uuid, ticks); }
    public int getBedSenseTimer(UUID uuid) { return bedSenseTimers.getOrDefault(uuid, 0); }
    public void tickBedSenseTimer(UUID uuid) { bedSenseTimers.computeIfPresent(uuid, (k, v) -> v <= 1 ? null : v - 1); }
    public boolean hasBedSenseActive(UUID uuid) { return bedSenseTimers.getOrDefault(uuid, 0) > 0; }
    private final Set<UUID> brokenBeds = new HashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<UUID> pendingRespawnPlayers = new HashSet<>();
    private final GunSelectionManager gunSelectionManager = new GunSelectionManager();
    private final Map<UUID, net.minecraft.core.BlockPos> lastKnownPositions = new HashMap<>();
    private final Map<UUID, Integer> noMoveTicks = new HashMap<>();
    private final Map<UUID, Integer> glowTicks = new HashMap<>();
    private static final int NO_MOVE_THRESHOLD = 15 * 20;
    private static final int NO_MOVE_WARNING   = 12 * 20; // warn at 12s, reveal at 15s
    private static final int GLOW_DURATION = 3 * 20;

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

    public int getKills(UUID uuid) { return playerKills.getOrDefault(uuid, 0); }
    public void addKill(UUID uuid) { playerKills.merge(uuid, 1, Integer::sum); }
    private final Map<UUID, Integer> playerDeaths = new HashMap<>();
    public int getDeaths(UUID uuid) { return playerDeaths.getOrDefault(uuid, 0); }
    public void addDeath(UUID uuid) { playerDeaths.merge(uuid, 1, Integer::sum); }
    public Map<UUID, Integer> getPlayerDeaths() { return playerDeaths; }
    public ServerLevel getLevel() { return level; }
    public BlockPos getBeaconPos() { return beaconPos; }
    public GameModeType getMode() { return mode; }
    public UUID getHostUuid() { return hostUuid; }

    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }
    public GunSelectionManager getGunSelectionManager() { return gunSelectionManager; }

    public boolean isActive() { return active; }
    public void end() { this.active = false; }

    // prpep timer
    public int getPrepTimeTicks() { return prepTimeTicks; }
    public int getInitialPrepTicks() { return initialPrepTicks; }

    public void setPrepTimeSeconds(int seconds) {
        this.prepTimeTicks = seconds * 20;
        this.initialPrepTicks = seconds * 20;
    }

    public void decreasePrepTime() {
        if (prepTimeTicks > 0) prepTimeTicks--;
    }

    // Match timer
    public int getMatchTimeTicks() { return matchTimeTicks; }
    public int getInitialMatchTicks() { return initialMatchTicks; }

    public boolean isMatchTimerSet() { return matchTimerSet; }

    public void setMatchTimeSeconds(int seconds) {
        this.matchTimeTicks = seconds * 20;
        this.initialMatchTicks = seconds * 20;
        this.matchTimerSet = true;
    }

    public int getEndgameBorderShrinkTicks() { return endgameBorderShrinkTicks; }

    public void setEndgameBorderShrinkTicks(int ticks) { this.endgameBorderShrinkTicks = ticks; }

    public void decreaseEndgameShrinkTicks() {
        if (endgameBorderShrinkTicks > 0) endgameBorderShrinkTicks--;
    }

    public int getEndgameBorderShrinkInterval() { return endgameBorderShrinkInterval; }

    public void decreaseMatchTime() {
        if (matchTimeTicks > 0) matchTimeTicks--;

        matchTimerSet = false;
        endgameBorderShrinkTicks = 0;
    }

    private boolean matchTimerSet = false;
    private int endgameBorderShrinkTicks = 0;
    private int endgameBorderShrinkInterval = 60 * 20;

    // WAITING_PLAYERS phase
    private static final int WAITING_PLAYERS_TICKS = 30 * 20;
    private static final int WAITING_MIN_TICKS = 5 * 20;
    private int waitingPlayersTicks = WAITING_PLAYERS_TICKS;
    private int configuredPrepSeconds = 180;

    public int getWaitingPlayersTicks() { return waitingPlayersTicks; }
    public void decreaseWaitingPlayersTicks() { if (waitingPlayersTicks > 0) waitingPlayersTicks--; }
    public void setWaitingPlayersTicks(int ticks) { this.waitingPlayersTicks = ticks; }
    public int getWaitingMinTicks() { return WAITING_MIN_TICKS; }
    public int getWaitingInitialTicks() { return WAITING_PLAYERS_TICKS; }

    public int getConfiguredPrepSeconds() { return configuredPrepSeconds; }
    public void setConfiguredPrepSeconds(int seconds) { this.configuredPrepSeconds = seconds; }

    // Border
    public int getBorderRadius() { return borderRadius; }
    public void setBorderRadius(int borderRadius) { this.borderRadius = borderRadius; }

    // Player counts
    public int getMatchStartPlayerCount() { return matchStartPlayerCount; }
    public void setMatchStartPlayerCount(int count) { this.matchStartPlayerCount = count; }

    // Joined players
    public Set<UUID> getJoinedPlayers() { return joinedPlayers; }
    public boolean addJoinedPlayer(UUID uuid) { return joinedPlayers.add(uuid); }
    public boolean removeJoinedPlayer(UUID uuid) { return joinedPlayers.remove(uuid); }
    public boolean isJoined(UUID uuid) { return joinedPlayers.contains(uuid); }

    // Active players
    public Set<UUID> getPlayers() { return players; }
    public void addPlayer(UUID uuid) { players.add(uuid); }

    // Reconnecting players
    private final Set<UUID> disconnectedDuringPrep = new HashSet<>();

    // Safeguard for reconnecting players
    private final Set<UUID> processingDisconnect = new HashSet<>();

    // tell mod that disconnected players doesn't mean they're out from the player set
    private final Set<UUID> offlinePlayers = new HashSet<>();

    public void markOffline(UUID uuid) { offlinePlayers.add(uuid); }
    public void markOnline(UUID uuid) { offlinePlayers.remove(uuid); }
    public boolean isOffline(UUID uuid) { return offlinePlayers.contains(uuid); }
    public Set<UUID> getOfflinePlayers() { return offlinePlayers; }

    public boolean tryLockDisconnect(UUID uuid) {
        return processingDisconnect.add(uuid); // returns false if already processing
    }

    public void unlockDisconnect(UUID uuid) {
        processingDisconnect.remove(uuid);
    }

    // Host disconnect
    private int hostDisconnectTicks = 0; // 0 = host is online
    private static final int HOST_RECONNECT_GRACE = 60 * 20; // 60 seconds

    public void startHostDisconnectTimer() { hostDisconnectTicks = HOST_RECONNECT_GRACE; }
    public void clearHostDisconnectTimer() { hostDisconnectTicks = 0; }
    public void decreaseHostDisconnectTicks() { if (hostDisconnectTicks > 0) hostDisconnectTicks--; }
    public int getHostDisconnectTicks() { return hostDisconnectTicks; }
    public boolean isHostDisconnected() { return hostDisconnectTicks > 0; }

    public Set<UUID> getDisconnectedDuringPrep() { return disconnectedDuringPrep; }

    // anti sitting duck on end game
    public Map<UUID, net.minecraft.core.BlockPos> getLastKnownPositions() { return lastKnownPositions; }
    public Map<UUID, Integer> getNoMoveTicks() { return noMoveTicks; }
    public Map<UUID, Integer> getGlowTicks() { return glowTicks; }
    public int getNoMoveThreshold() { return NO_MOVE_THRESHOLD; }
    public int getGlowDuration() { return GLOW_DURATION; }
    public Set<UUID> getWarnedPlayers() { return warnedPlayers; }
    public int getNoMoveWarningThreshold() { return NO_MOVE_WARNING; }

    private final Set<UUID> warnedPlayers = new HashSet<>();

    // Spawn immunity
    private final Set<UUID> spawnImmunePlayers = new HashSet<>();

    public void markSpawnImmune(UUID uuid) { spawnImmunePlayers.add(uuid); }
    public void clearSpawnImmune(UUID uuid) { spawnImmunePlayers.remove(uuid); }
    public boolean isSpawnImmune(UUID uuid) { return spawnImmunePlayers.contains(uuid); }

    // Teams
    public Map<UUID, String> getPlayerTeams() { return playerTeams; }
    public void setPlayerTeam(UUID uuid, String teamName) { playerTeams.put(uuid, teamName); }
    public String getPlayerTeam(UUID uuid) { return playerTeams.get(uuid); }

    public void setTeamBedOwner(String team, UUID uuid) { teamBedOwners.put(team, uuid); }
    public UUID getTeamBedOwner(String team) { return teamBedOwners.get(team); }
    public Map<String, UUID> getTeamBedOwners() { return teamBedOwners; }

    public boolean isFriendlyFire() { return friendlyFire; }
    public void setFriendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }

    public int getTeamCount() { return teamCount; }
    public void setTeamCount(int teamCount) { this.teamCount = teamCount; }

    private final Map<String, UUID> teamBedOwners = new HashMap<>();
    private boolean friendlyFire = false;
    private int teamCount = 2;

    // Beds
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

    // Player money system
    private final Map<UUID, Integer> playerMoney = new HashMap<>();

    public int getMoney(UUID uuid) { return playerMoney.getOrDefault(uuid, 0); }
    public void addMoney(UUID uuid, int amount) { playerMoney.merge(uuid, amount, Integer::sum); }
    public boolean spendMoney(UUID uuid, int cost) {
        int current = getMoney(uuid);
        if (current < cost) return false;
        playerMoney.put(uuid, current - cost);
        return true;
    }

    // upgrades with the player money system
    private final BedUpgradeManager bedUpgradeManager = new BedUpgradeManager();
    public BedUpgradeManager getBedUpgradeManager() { return bedUpgradeManager; }

    private final KillstreakManager killstreakManager = new KillstreakManager();
    public KillstreakManager getKillstreakManager() { return killstreakManager; }

    // Deathmatch
    private final DeathmatchManager deathmatchManager = new DeathmatchManager();
    private int killLimit = 30;
    private boolean killLimitSet = false;

    public DeathmatchManager getDeathmatchManager() { return deathmatchManager; }
    public boolean isDeathmatch() { return mode.isDeathmatch(); }
    public int getKillLimit() { return killLimit; }
    public void setKillLimit(int killLimit) { this.killLimit = killLimit; this.killLimitSet = true; }
    public boolean isKillLimitSet() { return killLimitSet; }

    private final MapRestoreManager mapRestoreManager = new MapRestoreManager();
    public MapRestoreManager getMapRestoreManager() { return mapRestoreManager; }

    public boolean isBedBroken(UUID uuid) { return brokenBeds.contains(uuid); }

    // elimination
    public Set<UUID> getEliminatedPlayers() { return eliminatedPlayers; }
    public boolean isEliminated(UUID uuid) { return eliminatedPlayers.contains(uuid); }

    public void eliminatePlayer(UUID uuid) {
        eliminatedPlayers.add(uuid);
        pendingRespawnPlayers.remove(uuid);
    }

    // respawn
    public Set<UUID> getPendingRespawnPlayers() { return pendingRespawnPlayers; }
    public void markPendingRespawn(UUID uuid) { pendingRespawnPlayers.add(uuid); }
    public void clearPendingRespawn(UUID uuid) { pendingRespawnPlayers.remove(uuid); }

    // win
    public String getWinnerName() { return winnerName; }

    public void setWinner(String winnerName) {
        this.winnerName = winnerName;
        this.phase = GamePhase.WINNER_ANNOUNCED;
        this.winnerDelayTicks = 120;
    }

    public void setWinnerDelay(int delay) { this.winnerDelayTicks = delay; }
    public int getWinnerDelayTicks() { return winnerDelayTicks; }

    public void decreaseWinnerDelay() {
        if (winnerDelayTicks > 0) winnerDelayTicks--;
    }

    // mvp cutscene
    private UUID winnerUUID = null;
    public UUID getWinnerUUID() { return winnerUUID; }
    public void setWinnerUUID(UUID uuid) { this.winnerUUID = uuid; }

    // Snapshots
    public boolean hasSnapshot(UUID uuid) { return savedPlayerStates.containsKey(uuid); }
    public Map<UUID, PlayerSnapshot> getSavedSnapshots() { return savedPlayerStates; }

    public void savePlayerState(ServerPlayer player) {
        savedPlayerStates.putIfAbsent(player.getUUID(), PlayerSnapshot.capture(player));
    }

    public void restorePlayerState(ServerPlayer player) {
        PlayerSnapshot snapshot = savedPlayerStates.get(player.getUUID());
        if (snapshot != null) snapshot.restore(player);
    }

    // Border snapshot
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

    // Disconnect
    public void handlePlayerDisconnect(UUID uuid, boolean eliminate) {
        if (eliminate) {
            // Actually eliminate remove from everything
            joinedPlayers.remove(uuid);
            players.remove(uuid);
            pendingRespawnPlayers.remove(uuid);
            eliminatedPlayers.add(uuid);
            playerTeams.remove(uuid);
            if (playerBeds.containsKey(uuid)) removePlayerBed(uuid);
            brokenBeds.remove(uuid);
        } else {
            // Just temporarily offline keep in players set
            markOffline(uuid);
            pendingRespawnPlayers.remove(uuid);
        }
    }

    // Minimap client and serverside
    private boolean minimapStartSent = false;
    public boolean isMinimapStartSent() { return minimapStartSent; }
    public void setMinimapStartSent(boolean v) { minimapStartSent = v; }

    public void resetMatchState() {
        players.clear();
        playerTeams.clear();
        playerBeds.clear();
        bedOwners.clear();
        brokenBeds.clear();
        eliminatedPlayers.clear();
        pendingRespawnPlayers.clear();
        gunSelectionManager.clear();
        teamBedOwners.clear();
        disconnectedDuringPrep.clear();
        winnerName = null;
        winnerUUID = null;
        winnerDelayTicks = 0;
        matchStartPlayerCount = 0;
        offlinePlayers.clear();
        warnedPlayers.clear();
        playerMoney.clear();
        bedUpgradeManager.clear();
        spawnImmunePlayers.clear();
        minimapStartSent = false;
        deathmatchManager.clearKillsOnly();
        killLimitSet = false;
        waitingPlayersTicks = WAITING_PLAYERS_TICKS;
    }

    public void hideAllNametags(MinecraftServer server) {
        net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
        net.minecraft.world.scores.PlayerTeam existing = scoreboard.getPlayerTeam("bgw_players");
        if (existing != null) scoreboard.removePlayerTeam(existing);
        net.minecraft.world.scores.PlayerTeam team = scoreboard.addPlayerTeam("bgw_players");
        team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
        for (UUID uuid : players) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) scoreboard.addPlayerToTeam(p.getGameProfile().getName(), team);
        }
    }

    public void removeNametagTeams(MinecraftServer server) {
        net.minecraft.server.ServerScoreboard scoreboard = server.getScoreboard();
        net.minecraft.world.scores.PlayerTeam solo = scoreboard.getPlayerTeam("bgw_players");
        if (solo != null) scoreboard.removePlayerTeam(solo);
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

    // Inner PlayerSnapshot
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