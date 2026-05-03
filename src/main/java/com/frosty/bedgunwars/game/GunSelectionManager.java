package com.frosty.bedgunwars.game;

import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class GunSelectionManager {

    private final Map<UUID, List<ResourceLocation>> playerGunSelections = new HashMap<>();
    // player → gun slot (0/1/2) → attachment type → attachment id
    private final Map<UUID, Map<Integer, Map<AttachmentType, ResourceLocation>>> playerGunAttachments = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> playerThrowableSelections = new HashMap<>();

    private static final int MAX_GUN_SLOTS       = 3;
    private static final int MAX_THROWABLE_PICKS = 5;

    public static List<ResourceLocation> getAllAvailableGuns() {
        List<ResourceLocation> guns = new ArrayList<>();
        com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex().forEach(entry -> {
            if (!ExcludedGunsConfig.isExcluded(entry.getKey())) {
                guns.add(entry.getKey());
            }
        });
        return guns;
    }

    public static List<ResourceLocation> getAllAvailableAttachments() {
        List<ResourceLocation> list = new ArrayList<>();
        try {
            com.tacz.guns.api.TimelessAPI.getAllCommonAttachmentIndex().forEach(entry -> list.add(entry.getKey()));
        } catch (Exception e) { /* ignore */ }
        list.sort(Comparator.comparing(ResourceLocation::getPath));
        return list;
    }

    public static List<ResourceLocation> getAllAvailableThrowables() {
        List<ResourceLocation> list = new ArrayList<>();
        try {
            me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes().forEach(index -> list.add(index.getId()));
        } catch (Exception e) { /* ignore */ }
        list.sort(Comparator.comparing(ResourceLocation::getPath));
        return list;
    }

    // ── Gun selections ────────────────────────────────────────────────────────

    public void setGunSelections(UUID player, List<ResourceLocation> selections) {
        playerGunSelections.put(player, new ArrayList<>(selections));
    }

    public void setSelections(UUID player, List<ResourceLocation> selections) {
        setGunSelections(player, selections);
    }

    public List<ResourceLocation> getGunSelections(UUID player) {
        return playerGunSelections.getOrDefault(player, new ArrayList<>());
    }

    public List<ResourceLocation> getSelections(UUID player) {
        return getGunSelections(player);
    }

    public boolean hasSelected(UUID player) {
        return playerGunSelections.containsKey(player) && !playerGunSelections.get(player).isEmpty();
    }

    // ── Per-gun attachments ───────────────────────────────────────────────────

    public void setAttachment(UUID player, int gunSlot, AttachmentType type, ResourceLocation attachmentId) {
        playerGunAttachments
                .computeIfAbsent(player, k -> new HashMap<>())
                .computeIfAbsent(gunSlot, k -> new HashMap<>())
                .put(type, attachmentId);
    }

    public void removeAttachment(UUID player, int gunSlot, AttachmentType type) {
        Map<Integer, Map<AttachmentType, ResourceLocation>> slots = playerGunAttachments.get(player);
        if (slots == null) return;
        Map<AttachmentType, ResourceLocation> typeMap = slots.get(gunSlot);
        if (typeMap != null) typeMap.remove(type);
    }

    public void clearGunAttachments(UUID player, int gunSlot) {
        Map<Integer, Map<AttachmentType, ResourceLocation>> slots = playerGunAttachments.get(player);
        if (slots != null) slots.remove(gunSlot);
    }

    public Map<AttachmentType, ResourceLocation> getGunAttachments(UUID player, int gunSlot) {
        return playerGunAttachments
                .getOrDefault(player, new HashMap<>())
                .getOrDefault(gunSlot, new HashMap<>());
    }

    public Map<Integer, Map<AttachmentType, ResourceLocation>> getAllGunAttachments(UUID player) {
        return playerGunAttachments.getOrDefault(player, new HashMap<>());
    }

    // ── Throwables ────────────────────────────────────────────────────────────

    public void setThrowableSelections(UUID player, List<ResourceLocation> selections) {
        playerThrowableSelections.put(player, new ArrayList<>(selections));
    }

    public List<ResourceLocation> getThrowableSelections(UUID player) {
        return playerThrowableSelections.getOrDefault(player, new ArrayList<>());
    }

    public int getMaxGunSlots()       { return MAX_GUN_SLOTS; }
    public int getMaxThrowablePicks() { return MAX_THROWABLE_PICKS; }

    public void clear() {
        playerGunSelections.clear();
        playerGunAttachments.clear();
        playerThrowableSelections.clear();
    }
}