package com.frosty.bedgunwars.game;

import com.frosty.bedgunwars.BedGunWars;
import com.google.gson.*;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class LoadoutManager {

    public static final int MAX_LOADOUTS = 8;

    public static class Loadout {
        public String name;
        public List<String> guns;                          // 3 gun IDs
        public Map<Integer, Map<String, String>> attachments; // slot -> (attachType -> attachId)
        public List<String> throwables;

        public Loadout(String name,
                       List<String> guns,
                       Map<Integer, Map<String, String>> attachments,
                       List<String> throwables) {
            this.name = name;
            this.guns = guns;
            this.attachments = attachments;
            this.throwables = throwables;
        }
    }

    // UUID -> list of loadouts
    private final Map<UUID, List<Loadout>> loadouts = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path saveDir;

    public static void init(net.minecraft.server.MinecraftServer server) {
        saveDir = server.getServerDirectory().toPath()
                .resolve("config").resolve("bedgunwars").resolve("loadouts");
        try { Files.createDirectories(saveDir); }
        catch (Exception e) { BedGunWars.LOGGER.error("[Loadout] Failed to create dir: {}", e.getMessage()); }
    }

    // ── Load from disk ────────────────────────────────────────────────────────

    public List<Loadout> getLoadouts(UUID uuid) {
        return loadouts.computeIfAbsent(uuid, k -> loadFromDisk(k));
    }

    private List<Loadout> loadFromDisk(UUID uuid) {
        if (saveDir == null) return new ArrayList<>();
        Path file = saveDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            String json = Files.readString(file);
            JsonArray arr = GSON.fromJson(json, JsonArray.class);
            List<Loadout> result = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String name = obj.get("name").getAsString();
                List<String> guns = new ArrayList<>();
                for (JsonElement g : obj.getAsJsonArray("guns")) guns.add(g.getAsString());
                Map<Integer, Map<String, String>> atts = new HashMap<>();
                if (obj.has("attachments")) {
                    for (Map.Entry<String, JsonElement> slotEntry :
                            obj.getAsJsonObject("attachments").entrySet()) {
                        int slot = Integer.parseInt(slotEntry.getKey());
                        Map<String, String> typeMap = new HashMap<>();
                        for (Map.Entry<String, JsonElement> typeEntry :
                                slotEntry.getValue().getAsJsonObject().entrySet()) {
                            typeMap.put(typeEntry.getKey(), typeEntry.getValue().getAsString());
                        }
                        atts.put(slot, typeMap);
                    }
                }
                List<String> throwables = new ArrayList<>();
                if (obj.has("throwables"))
                    for (JsonElement t : obj.getAsJsonArray("throwables"))
                        throwables.add(t.getAsString());
                result.add(new Loadout(name, guns, atts, throwables));
            }
            return result;
        } catch (Exception e) {
            BedGunWars.LOGGER.error("[Loadout] Failed to load {}: {}", uuid, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── Save to disk ──────────────────────────────────────────────────────────

    private void saveToDisk(UUID uuid) {
        if (saveDir == null) return;
        try {
            List<Loadout> list = getLoadouts(uuid);
            JsonArray arr = new JsonArray();
            for (Loadout l : list) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", l.name);
                JsonArray guns = new JsonArray();
                for (String g : l.guns) guns.add(g);
                obj.add("guns", guns);
                JsonObject atts = new JsonObject();
                for (Map.Entry<Integer, Map<String, String>> slotEntry : l.attachments.entrySet()) {
                    JsonObject typeMap = new JsonObject();
                    for (Map.Entry<String, String> e : slotEntry.getValue().entrySet())
                        typeMap.addProperty(e.getKey(), e.getValue());
                    atts.add(String.valueOf(slotEntry.getKey()), typeMap);
                }
                obj.add("attachments", atts);
                JsonArray throwables = new JsonArray();
                for (String t : l.throwables) throwables.add(t);
                obj.add("throwables", throwables);
                arr.add(obj);
            }
            Files.writeString(saveDir.resolve(uuid + ".json"), GSON.toJson(arr));
        } catch (Exception e) {
            BedGunWars.LOGGER.error("[Loadout] Failed to save {}: {}", uuid, e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean saveLoadout(UUID uuid, String name,
                               GunSelectionManager gsm) {
        List<Loadout> list = getLoadouts(uuid);
        if (list.size() >= MAX_LOADOUTS) return false;

        List<String> guns = new ArrayList<>();
        for (ResourceLocation g : gsm.getGunSelections(uuid))
            guns.add(g.toString());

        Map<Integer, Map<String, String>> atts = new HashMap<>();
        for (Map.Entry<Integer, Map<AttachmentType, ResourceLocation>> slotEntry :
                gsm.getAllGunAttachments(uuid).entrySet()) {
            Map<String, String> typeMap = new HashMap<>();
            for (Map.Entry<AttachmentType, ResourceLocation> e : slotEntry.getValue().entrySet())
                typeMap.put(e.getKey().name(), e.getValue().toString());
            atts.put(slotEntry.getKey(), typeMap);
        }

        List<String> throwables = new ArrayList<>();
        for (ResourceLocation t : gsm.getThrowableSelections(uuid))
            throwables.add(t.toString());

        list.add(new Loadout(name, guns, atts, throwables));
        saveToDisk(uuid);
        return true;
    }

    public void applyLoadout(UUID uuid, int index, GunSelectionManager gsm) {
        List<Loadout> list = getLoadouts(uuid);
        if (index < 0 || index >= list.size()) return;
        Loadout l = list.get(index);

        List<ResourceLocation> guns = new ArrayList<>();
        for (String g : l.guns) guns.add(ResourceLocation.parse(g));
        gsm.setGunSelections(uuid, guns);

        gsm.getAllGunAttachments(uuid).clear();
        for (Map.Entry<Integer, Map<String, String>> slotEntry : l.attachments.entrySet()) {
            for (Map.Entry<String, String> e : slotEntry.getValue().entrySet()) {
                AttachmentType type = AttachmentType.valueOf(e.getKey());
                gsm.setAttachment(uuid, slotEntry.getKey(), type,
                        ResourceLocation.parse(e.getValue()));
            }
        }

        List<ResourceLocation> throwables = new ArrayList<>();
        for (String t : l.throwables) throwables.add(ResourceLocation.parse(t));
        gsm.setThrowableSelections(uuid, throwables);
    }

    public boolean deleteLoadout(UUID uuid, int index) {
        List<Loadout> list = getLoadouts(uuid);
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        saveToDisk(uuid);
        return true;
    }

    public boolean renameLoadout(UUID uuid, int index, String newName) {
        List<Loadout> list = getLoadouts(uuid);
        if (index < 0 || index >= list.size()) return false;
        list.get(index).name = newName;
        saveToDisk(uuid);
        return true;
    }

    // Singleton per server
    private static LoadoutManager instance;
    public static LoadoutManager get() {
        if (instance == null) instance = new LoadoutManager();
        return instance;
    }
    public static void reset() { instance = null; }
}