package com.frosty.bedgunwars.game;

import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class BedUpgradeManager {

    public enum UpgradeType {
        MINING_FATIGUE, ALARM, SLOWNESS, TP_TO_BED,
        PICKAXE, SPEED, AXE, BED_SENSE,
        HEALING_STATION
    }

    // Prices
    public static final int[][] PRICES = {
            // T1,   T2,   T3,   T4,   T5,   T6
            {100,  250,  250,  400,  800, 1000}, // MINING_FATIGUE
            {500,    0,    0,    0,    0,    0}, // ALARM
            {350,    0,    0,    0,    0,    0}, // SLOWNESS
            {1000,   0,    0,    0,    0,    0}, // TP_TO_BED
            {50,   80,  100,  200,  400,    0}, // PICKAXE
            {300,    0,    0,    0,    0,    0}, // SPEED
            {100,  300,    0,    0,    0,    0}, // AXE
            {400,    0,    0,    0,    0,    0}, // BED_SENSE
            {200,  300,  450,  500,  600, 1000}, // HEALING_STATION
    };

    // Max tiers
    public static final int[] MAX_TIERS = {6, 1, 1, 1, 5, 1, 2, 1, 6};

    // Per-team upgrade tiers
    private final Map<String, int[]> teamUpgrades = new HashMap<>();

    public void initTeam(String team) {
        teamUpgrades.put(team, new int[UpgradeType.values().length]);
    }

    public int getTier(String team, UpgradeType type) {
        int[] tiers = teamUpgrades.getOrDefault(team, new int[UpgradeType.values().length]);
        return tiers[type.ordinal()];
    }

    public void setTier(String team, UpgradeType type, int tier) {
        teamUpgrades.computeIfAbsent(team, k -> new int[UpgradeType.values().length])[type.ordinal()] = tier;
    }

    public int getPrice(UpgradeType type, int nextTier) {
        int[] prices = PRICES[type.ordinal()];
        if (nextTier < 1 || nextTier > prices.length) return -1;
        return prices[nextTier - 1];
    }

    public int getMaxTier(UpgradeType type) {
        return MAX_TIERS[type.ordinal()];
    }

    public void clear() { teamUpgrades.clear(); }

    // Display items for chest UI
    public static ItemStack getDisplayItem(UpgradeType type, int currentTier) {
        ItemStack stack = switch (type) {
            case MINING_FATIGUE -> new ItemStack(Items.END_CRYSTAL);
            case ALARM          -> new ItemStack(Items.BELL);
            case SLOWNESS       -> new ItemStack(Items.ICE);
            case TP_TO_BED      -> makeBedTeleportCompass();
            case PICKAXE        -> getPickaxeForTier(currentTier);
            case SPEED          -> new ItemStack(Items.SUGAR);
            case AXE            -> getAxeForTier(currentTier);
            case BED_SENSE      -> new ItemStack(Items.ENDER_EYE);
            case HEALING_STATION -> new ItemStack(Items.GLISTERING_MELON_SLICE);
        };
        return stack;
    }

    public static ItemStack makeBedTeleportCompass() {
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.setHoverName(Component.literal("§6Bed Teleport"));
        // Add enchantment glint via NBT
        CompoundTag tag = compass.getOrCreateTag();
        tag.putBoolean("HideFlags", false);
        net.minecraft.nbt.ListTag enchants = new net.minecraft.nbt.ListTag();
        CompoundTag ench = new CompoundTag();
        ench.putString("id", "minecraft:aqua_affinity");
        ench.putInt("lvl", 1);
        enchants.add(ench);
        tag.put("Enchantments", enchants);
        tag.putInt("HideFlags", 1); // hide enchant tooltip but keep glint
        return compass;
    }

    public static ItemStack getPickaxeForTier(int tier) {
        return switch (tier) {
            case 0 -> new ItemStack(Items.WOODEN_PICKAXE);
            case 1 -> new ItemStack(Items.STONE_PICKAXE);
            case 2 -> new ItemStack(Items.IRON_PICKAXE);
            case 3 -> new ItemStack(Items.DIAMOND_PICKAXE);
            case 4 -> new ItemStack(Items.NETHERITE_PICKAXE);
            default -> {
                ItemStack s = new ItemStack(Items.NETHERITE_PICKAXE);
                s.enchant(Enchantments.BLOCK_EFFICIENCY, 4);
                yield s;
            }
        };
    }

    public static ItemStack getAxeForTier(int tier) {
        return switch (tier) {
            case 1  -> new ItemStack(Items.STONE_AXE);
            default -> new ItemStack(Items.DIAMOND_AXE);
        };
    }
}