package com.frosty.bedgunwars.game;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class GunHelper {

    public static ItemStack buildGun(ResourceLocation gunId) {
        try {
            ItemStack stack = GunItemBuilder.create().setId(gunId).build();
            if (stack == null || stack.isEmpty()) return new ItemStack(Items.BOW);
            return stack;
        } catch (Exception e) {
            return new ItemStack(Items.BOW);
        }
    }

    public static boolean isGunItem(ResourceLocation id) {
        try {
            var item = ForgeRegistries.ITEMS.getValue(id);
            return item instanceof IGun;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getGunDisplayName(ResourceLocation gunId) {
        return formatPath(gunId.getPath());
    }

    private static String formatPath(String path) {
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
}