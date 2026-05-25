package com.frosty.bedgunwars.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class DeathmatchManager {

    // Solo, kills per player UUID. Teams: kills per team name.
    private final Map<String, Integer> killCounts = new HashMap<>();

    // Team name > assigned beacon BlockPos
    private final Map<String, BlockPos> teamBeacons = new HashMap<>();

    // All detected beacons (used for solo random respawn)
    private final List<BlockPos> allBeacons = new ArrayList<>();

    private final Random random = new Random();

    public void clearKillsOnly() {
        killCounts.clear();
        // Intentionally keep allBeacons and teamBeacons set during /game border
    }

    // Kill tracking

    public void addKill(String key) {
        killCounts.merge(key, 1, Integer::sum);
    }

    public int getKills(String key) {
        return killCounts.getOrDefault(key, 0);
    }

    public String getLeader() {
        return killCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public Map<String, Integer> getAllKills() {
        return Collections.unmodifiableMap(killCounts);
    }

    // Beacon management

    public void setAllBeacons(List<BlockPos> beacons) {
        allBeacons.clear();
        allBeacons.addAll(beacons);
    }

    public List<BlockPos> getAllBeacons() {
        return Collections.unmodifiableList(allBeacons);
    }

    public void assignTeamBeacon(String team, BlockPos pos) {
        teamBeacons.put(team, pos);
    }

    public BlockPos getTeamBeacon(String team) {
        return teamBeacons.get(team);
    }

    public Map<String, BlockPos> getTeamBeacons() {
        return Collections.unmodifiableMap(teamBeacons);
    }

    // Respawn positions

    public BlockPos getRandomBeacon() {
        if (allBeacons.isEmpty()) return null;
        return allBeacons.get(random.nextInt(allBeacons.size()));
    }

    public BlockPos getTeamRespawnBeacon(String team) {
        return teamBeacons.get(team);
    }

    // Win condition
    
    public String checkKillLimitWinner(int killLimit) {
        for (Map.Entry<String, Integer> entry : killCounts.entrySet()) {
            if (entry.getValue() >= killLimit) return entry.getKey();
        }
        return null;
    }
    
    public String getMostKillsWinner() {
        return killCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public void clear() {
        killCounts.clear();
        teamBeacons.clear();
        allBeacons.clear();
    }
}