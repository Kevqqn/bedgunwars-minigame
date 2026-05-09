package com.frosty.bedgunwars.game;

import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

public class GunHelper {

    public static ItemStack buildGun(ResourceLocation gunId) {
        try {
            ItemStack stack = GunItemBuilder.create().setId(gunId).build();
            if (stack == null || stack.isEmpty()) return new ItemStack(Items.BOW);

            if (stack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
                    java.util.List<com.tacz.guns.api.item.gun.FireMode> modes =
                            index.getGunData().getFireModeSet();
                    if (modes != null && !modes.isEmpty()) {
                        iGun.setFireMode(stack, modes.get(0));
                    }
                });
            }
            return stack;
        } catch (Exception e) {
            return new ItemStack(Items.BOW);
        }
    }

    public static ItemStack buildAttachment(ResourceLocation attachmentId) {
        try {
            // Create the base tacz:attachment item
            var attachmentItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("tacz", "attachment"));
            if (attachmentItem == null || attachmentItem == Items.AIR) return ItemStack.EMPTY;

            ItemStack stack = new ItemStack(attachmentItem);
            CompoundTag tag = stack.getOrCreateTag();
            tag.putString("AttachmentId", attachmentId.toString());

            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static String getAttachmentDisplayName(ResourceLocation attachmentId) {
        return formatPath(attachmentId.getPath());
    }

    public static String getGunCategory(ResourceLocation gunId) {
        try {
            var index = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId);
            if (index.isPresent()) {
                var gunIndex = index.get();
                // Try to get GunTabType via getType() or getTabType()
                try {
                    var method = gunIndex.getClass().getMethod("getType");
                    Object result = method.invoke(gunIndex);
                    if (result != null) return formatTabType(result.toString());
                } catch (NoSuchMethodException ignored) {}
                try {
                    var method = gunIndex.getClass().getMethod("getTabType");
                    Object result = method.invoke(gunIndex);
                    if (result != null) return formatTabType(result.toString());
                } catch (NoSuchMethodException ignored) {}
                // Check all methods returning GunTabType
                for (var method : gunIndex.getClass().getMethods()) {
                    if (method.getReturnType().getSimpleName().equals("GunTabType")) {
                        Object result = method.invoke(gunIndex);
                        if (result != null) return formatTabType(result.toString());
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Rifle";
    }

    private static String formatTabType(String raw) {
        return switch (raw.toUpperCase()) {
            case "PISTOL"  -> "Pistol";
            case "SNIPER"  -> "Sniper";
            case "RIFLE"   -> "Rifle";
            case "SHOTGUN" -> "Shotgun";
            case "SMG"     -> "SMG";
            case "RPG"     -> "RPG";
            case "MG"      -> "LMG";
            default        -> "Rifle";
        };
    }

// Unused
//    public static ItemStack buildCreativeAmmoBox() {
//        ItemStack item = new ItemStack(ForgeRegistries.ITEMS.getValue(
//                ResourceLocation.fromNamespaceAndPath("tacz", "ammo_box")));
//        item.getOrCreateTag().putBoolean("AllTypeCreative", true);
//        return item;
//    }

    
    // Gun stats for tooltip
    

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static java.util.List<net.minecraft.network.chat.Component> getGunStats(ResourceLocation gunId) {
        java.util.List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
        try {
            com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
                Object data = index.getGunData();
                if (data == null) return;
                Class<?> cls = data.getClass();
                // Damage — via BulletData.getDamageAmount()
                try {
                    Object bulletData = cls.getMethod("getBulletData").invoke(data);
                    if (bulletData != null) {
                        float dmg = ((Number) bulletData.getClass().getMethod("getDamageAmount").invoke(bulletData)).floatValue();
                        if (dmg > 0) lines.add(stat("Damage", String.format("%.0f", dmg)));
                    }
                } catch (Exception ignored) {}

                // Fire rate (RPM)
                int rpm = reflectInt(cls, data, "getRoundsPerMinute", "getFireRate", "getRpm");
                if (rpm > 0) lines.add(stat("Fire Rate", rpm + " RPM"));

                // Magazine size
                int mag = reflectInt(cls, data, "getAmmoAmount", "getMagazineSize", "getAmmoSize");
                if (mag > 0) lines.add(stat("Magazine", String.valueOf(mag)));

                // Reload time (ticks → seconds)
                int reloadTicks = reflectInt(cls, data, "getReloadTime", "getNormalReloadTime");
                if (reloadTicks > 0) {
                    lines.add(stat("Reload", String.format("%.1fs", reloadTicks / 20.0f)));
                }

                // Fire mode
                try {
                    java.util.List<com.tacz.guns.api.item.gun.FireMode> modes =
                            index.getGunData().getFireModeSet();
                    if (modes != null && !modes.isEmpty()) {
                        String modeStr = modes.stream()
                                .map(m -> formatFireMode(m.toString()))
                                .reduce((a, b) -> a + " / " + b)
                                .orElse("Auto");
                        lines.add(stat("Fire Mode", modeStr));
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        return lines;
    }

    private static net.minecraft.network.chat.Component stat(String label, String value) {
        return net.minecraft.network.chat.Component.literal("§7" + label + ": §f" + value);
    }

    private static float reflectFloat(Class<?> cls, Object obj, String... methodNames) {
        for (String name : methodNames) {
            try {
                var m = cls.getMethod(name);
                Object result = m.invoke(obj);
                if (result instanceof Number n) return n.floatValue();
            } catch (Exception ignored) {}
        }
        return 0f;
    }

    private static int reflectInt(Class<?> cls, Object obj, String... methodNames) {
        for (String name : methodNames) {
            try {
                var m = cls.getMethod(name);
                Object result = m.invoke(obj);
                if (result instanceof Number n) return n.intValue();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private static String formatFireMode(String raw) {
        return switch (raw.toUpperCase()) {
            case "AUTO" -> "Auto";
            case "SEMI" -> "Semi";
            case "BURST" -> "Burst";
            default -> raw;
        };
    }

    
    // Ammo detection and giving
    

    /** Returns the ammo item ResourceLocation for a gun, via reflection on getGunData() */
    public static ResourceLocation getAmmoId(ResourceLocation gunId) {
        try {
            var opt = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId);
            if (opt.isEmpty()) return null;
            Object data = opt.get().getGunData();
            var m = data.getClass().getMethod("getAmmoId");
            Object result = m.invoke(data);
            if (result instanceof ResourceLocation rl) return rl;
            if (result instanceof String s) return ResourceLocation.parse(s);
        } catch (Exception ignored) {}
        return null;
    }

    /** Builds a TACZ ammo ItemStack using the tacz:ammo item with AmmoId NBT tag */
    public static ItemStack buildAmmoStack(ResourceLocation ammoId, int count) {
        try {
            net.minecraft.world.item.Item ammoItem =
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                            ResourceLocation.fromNamespaceAndPath("tacz", "ammo"));
            if (ammoItem == null || ammoItem == Items.AIR) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(ammoItem, Math.min(count, 64));
            stack.getOrCreateTag().putString("AmmoId", ammoId.toString());
            return stack;
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    private static int countAmmo(net.minecraft.server.level.ServerPlayer player, ResourceLocation ammoId) {
        int count = 0;
        String ammoStr = ammoId.toString();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            net.minecraft.nbt.CompoundTag tag = s.getTag();
            if (tag != null && ammoStr.equals(tag.getString("AmmoId"))) count += s.getCount();
        }
        return count;
    }

    public static void removeAmmo(net.minecraft.server.level.ServerPlayer player, ResourceLocation ammoId) {
        String ammoStr = ammoId.toString();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            net.minecraft.nbt.CompoundTag tag = s.getTag();
            if (tag != null && ammoStr.equals(tag.getString("AmmoId")))
                player.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }

    private static void giveAmmo(net.minecraft.server.level.ServerPlayer player,
                                 ResourceLocation ammoId, int amount) {
        int remaining = amount;
        // Target main inventory slots (9-35), skipping hotbar (0-8)
        for (int i = 9; i < 36 && remaining > 0; i++) {
            ItemStack existing = player.getInventory().getItem(i);
            if (existing.isEmpty()) {
                int stackSize = Math.min(remaining, 64);
                ItemStack stack = buildAmmoStack(ammoId, stackSize);
                if (!stack.isEmpty()) {
                    player.getInventory().setItem(i, stack);
                    remaining -= stackSize;
                }
            } else {
                // Check if same ammo type and not full
                net.minecraft.nbt.CompoundTag tag = existing.getTag();
                if (tag != null && ammoId.toString().equals(tag.getString("AmmoId"))
                        && existing.getCount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    int add = Math.min(space, remaining);
                    existing.grow(add);
                    remaining -= add;
                }
            }
        }
    }

    /** Returns the ammo reserve count based on gun category */
    public static int getAmmoReserve(String category) {
        return switch (category) {
            case "Sniper"       -> 40;
            case "Pistol"       -> 60;
            case "Shotgun"      -> 60;
            case "SMG", "LMG"   -> 300;
            case "Rifle"        -> 210;
            default             -> 120;
        };
    }

    /** Gives the correct ammo reserve to a player for all their selected guns */
    public static void giveAmmoReserves(net.minecraft.server.level.ServerPlayer player,
                                        java.util.List<ResourceLocation> gunIds,
                                        boolean topUpOnly) {
        java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (ResourceLocation gunId : gunIds) {
            ResourceLocation ammoId = getAmmoId(gunId);
            if (ammoId == null || !seen.add(ammoId)) continue;
            int reserve = getAmmoReserve(getGunCategory(gunId));
            if (topUpOnly) {
                int existing = countAmmo(player, ammoId);
                int toGive = reserve - existing;
                if (toGive <= 0) continue;
                giveAmmo(player, ammoId, toGive);
            } else {
                giveAmmo(player, ammoId, reserve);
            }
        }
    }

    /** Replenish ammo back to cap */
    public static void replenishAmmo(net.minecraft.server.level.ServerPlayer player,
                                     java.util.List<ResourceLocation> gunIds) {
        java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (ResourceLocation gunId : gunIds) {
            ResourceLocation ammoId = getAmmoId(gunId);
            if (ammoId == null || !seen.add(ammoId)) continue;
            int cap = getAmmoReserve(getGunCategory(gunId));
            removeAmmo(player, ammoId);
            giveAmmo(player, ammoId, cap);
        }
    }

    public static void reloadAllGuns(net.minecraft.server.level.ServerPlayer player,
                                     GunSelectionManager gsm) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof com.tacz.guns.api.item.IGun iGun)) continue;
            ResourceLocation gunId = iGun.getGunId(stack);
            if (gunId == null) continue;
            com.tacz.guns.api.TimelessAPI.getCommonGunIndex(gunId).ifPresent(index -> {
                int maxAmmo = index.getGunData().getAmmoAmount();
                if (maxAmmo > 0) iGun.setCurrentAmmoCount(stack, maxAmmo);
            });
        }
    }

    /** Removes ammo for all guns in the given list */
    public static void removeAllGunAmmo(net.minecraft.server.level.ServerPlayer player,
                                        java.util.List<ResourceLocation> gunIds) {
        java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (ResourceLocation gunId : gunIds) {
            ResourceLocation ammoId = getAmmoId(gunId);
            if (ammoId != null && seen.add(ammoId)) {
                removeAmmo(player, ammoId);
            }
        }
    }

    /** Returns true if all selected gun ammo types are already at full reserve */
    public static boolean isAmmoFull(net.minecraft.server.level.ServerPlayer player,
                                     java.util.List<ResourceLocation> gunIds) {
        java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
        for (ResourceLocation gunId : gunIds) {
            ResourceLocation ammoId = getAmmoId(gunId);
            if (ammoId == null || !seen.add(ammoId)) continue;
            int cap = getAmmoReserve(getGunCategory(gunId));
            int current = countAmmo(player, ammoId);
            if (current < cap) return false;
        }
        return true;
    }

    public static boolean isGunId(ResourceLocation id) {
        try {
            return com.tacz.guns.api.TimelessAPI.getCommonGunIndex(id).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public static String getGunDisplayName(ResourceLocation id) {
        return formatPath(id.getPath());
    }

    public static List<ResourceLocation> getCompatibleAttachments(List<ResourceLocation> selectedGunIds) {
        List<ResourceLocation> compatible = new ArrayList<>();
        try {
            // Build gun stacks for each selected gun
            List<ItemStack> gunStacks = new ArrayList<>();
            for (ResourceLocation gunId : selectedGunIds) {
                ItemStack stack = buildGun(gunId);
                if (!stack.isEmpty()) gunStacks.add(stack);
            }
            if (gunStacks.isEmpty()) return GunSelectionManager.getAllAvailableAttachments();

            // Get all attachments and filter by compatibility with ANY selected gun
            com.tacz.guns.api.TimelessAPI.getAllCommonAttachmentIndex().forEach(entry -> {
                ResourceLocation attachId = entry.getKey();
                try {
                    ItemStack attachStack = buildAttachment(attachId);
                    if (attachStack.isEmpty()) return;
                    for (ItemStack gunStack : gunStacks) {
                        if (gunStack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                            if (iGun.allowAttachment(gunStack, attachStack)) {
                                compatible.add(attachId);
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            return GunSelectionManager.getAllAvailableAttachments();
        }
        compatible.sort(Comparator.comparing(ResourceLocation::getPath));
        return compatible;
    }

    public static String formatPath(String path) {
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static ItemStack buildThrowable(ResourceLocation throwableId) {
        try {
            var indexes = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes();
            for (var index : indexes) {
                if (index.getId().equals(throwableId)) {
                    ItemStack stack = index.createItemStack();
                    if (stack != null && !stack.isEmpty()) return stack;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return ItemStack.EMPTY;
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static String getThrowableDisplayName(ResourceLocation id) {
        try {
            var indexes = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableIndexes();
            for (var index : indexes) {
                if (index.getId().equals(id)) {
                    String key = index.getDescriptionId();
                    String translated = net.minecraft.network.chat.Component
                            .translatable(key).getString();
                    if (!translated.equals(key)) return translated;
                    return formatPath(id.getPath());
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return formatPath(id.getPath());
    }
}