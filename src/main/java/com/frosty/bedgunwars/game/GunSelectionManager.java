package com.frosty.bedgunwars.game;

import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GunSelectionManager {

    private final Map<UUID, List<ResourceLocation>> playerSelections = new HashMap<>();
    private static final int MAX_GUN_SLOTS = 3;

    public static List<ResourceLocation> getAllAvailableGuns() {
        List<ResourceLocation> guns = new ArrayList<>();
        ForgeRegistries.ITEMS.getEntries().forEach(entry -> {
            if (entry.getValue() instanceof IGun) {
                guns.add(entry.getKey().location());
            }
        });
        guns.sort((a, b) -> a.getPath().compareTo(b.getPath()));
        return guns;
    }

    public void setSelections(UUID player, List<ResourceLocation> selections) {
        playerSelections.put(player, new ArrayList<>(selections));
    }

    public List<ResourceLocation> getSelections(UUID player) {
        return playerSelections.getOrDefault(player, new ArrayList<>());
    }

    public boolean hasSelected(UUID player) {
        return playerSelections.containsKey(player) && !playerSelections.get(player).isEmpty();
    }

    public void clear() {
        playerSelections.clear();
    }

    public int getMaxGunSlots() {
        return MAX_GUN_SLOTS;
    }
}