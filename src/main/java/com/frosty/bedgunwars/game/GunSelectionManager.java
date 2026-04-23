package com.frosty.bedgunwars.game;

import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GunSelectionManager {

    public static final List<ResourceLocation> DEFAULT_GUNS = List.of(
            ResourceLocation.fromNamespaceAndPath("tacz", "ak47"),
            ResourceLocation.fromNamespaceAndPath("tacz", "m4a1"),
            ResourceLocation.fromNamespaceAndPath("tacz", "m700")
    );

    private final List<ResourceLocation> availableGuns;
    private final Map<UUID, ResourceLocation> playerSelections = new HashMap<>();

    public GunSelectionManager() {
        this.availableGuns = new ArrayList<>(DEFAULT_GUNS);
    }

    public List<ResourceLocation> getAvailableGuns() {
        return availableGuns;
    }

    public void setSelection(UUID player, ResourceLocation gunId) {
        playerSelections.put(player, gunId);
    }

    public ResourceLocation getSelection(UUID player) {
        return playerSelections.getOrDefault(player, availableGuns.get(0));
    }

    public boolean hasSelected(UUID player) {
        return playerSelections.containsKey(player);
    }

    public void clear() {
        playerSelections.clear();
    }
}