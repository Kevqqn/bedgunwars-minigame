package com.frosty.bedgunwars.game;

import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Comparator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GunSelectionManager {

    private final Map<UUID, List<ResourceLocation>> playerGunSelections = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> playerAttachmentSelections = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> playerThrowableSelections = new HashMap<>();

    private static final int MAX_GUN_SLOTS = 3;
    private static final int MAX_ATTACHMENT_PICKS = 5;
    private static final int MAX_THROWABLE_PICKS = 5;

    public static List<ResourceLocation> getAllAvailableGuns() {
        List<ResourceLocation> guns = new ArrayList<>();
        try {
            com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex().forEach(entry ->
                    guns.add(entry.getKey()));
        } catch (Exception e) {
            guns.add(ResourceLocation.fromNamespaceAndPath("tacz", "ak47"));
            guns.add(ResourceLocation.fromNamespaceAndPath("tacz", "m4a1"));
            guns.add(ResourceLocation.fromNamespaceAndPath("tacz", "m700"));
        }
        guns.sort(Comparator.comparing(ResourceLocation::getPath));
        return guns;
    }

    public static List<ResourceLocation> getAllAvailableAttachments() {
        List<ResourceLocation> guns = new ArrayList<>();
        try {
            com.tacz.guns.api.TimelessAPI.getAllCommonAttachmentIndex().forEach(entry ->
                    guns.add(entry.getKey()));
        } catch (Exception e) {
            guns.add(ResourceLocation.fromNamespaceAndPath("tacz", "ak47"));
            guns.add(ResourceLocation.fromNamespaceAndPath("tacz", "m4a1"));
            guns.add(ResourceLocation.fromNamespaceAndPath("tacz", "m700"));
        }
        guns.sort(Comparator.comparing(ResourceLocation::getPath));
        return guns;
    }

    public static List<ResourceLocation> getAllAvailableThrowables() {
        List<ResourceLocation> list = new ArrayList<>();
        try {
            me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes().forEach(index ->
                    list.add(index.getId()));
        } catch (Exception e) {
            // LesRaisins not installed
        }
        list.sort(Comparator.comparing(ResourceLocation::getPath));
        return list;
    }

    public void setGunSelections(UUID player, List<ResourceLocation> selections) {
        playerGunSelections.put(player, new ArrayList<>(selections));
    }

    /** Back-compat alias used by existing SelectGunPacket code. */
    public void setSelections(UUID player, List<ResourceLocation> selections) {
        setGunSelections(player, selections);
    }

    public List<ResourceLocation> getGunSelections(UUID player) {
        return playerGunSelections.getOrDefault(player, new ArrayList<>());
    }

    /** Back-compat alias. */
    public List<ResourceLocation> getSelections(UUID player) {
        return getGunSelections(player);
    }

    public boolean hasSelected(UUID player) {
        return playerGunSelections.containsKey(player) && !playerGunSelections.get(player).isEmpty();
    }

    public void setAttachmentSelections(UUID player, List<ResourceLocation> selections) {
        playerAttachmentSelections.put(player, new ArrayList<>(selections));
    }

    public List<ResourceLocation> getAttachmentSelections(UUID player) {
        return playerAttachmentSelections.getOrDefault(player, new ArrayList<>());
    }

    public void setThrowableSelections(UUID player, List<ResourceLocation> selections) {
        playerThrowableSelections.put(player, new ArrayList<>(selections));
    }

    public List<ResourceLocation> getThrowableSelections(UUID player) {
        return playerThrowableSelections.getOrDefault(player, new ArrayList<>());
    }

    public int getMaxGunSlots()         { return MAX_GUN_SLOTS; }
    public int getMaxAttachmentPicks()  { return MAX_ATTACHMENT_PICKS; }
    public int getMaxThrowablePicks()   { return MAX_THROWABLE_PICKS; }

    public void clear() {
        playerGunSelections.clear();
        playerAttachmentSelections.clear();
        playerThrowableSelections.clear();
    }
}
