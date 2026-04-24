package com.frosty.bedgunwars.game;

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

// Unused
//    public static ItemStack buildCreativeAmmoBox() {
//        ItemStack item = new ItemStack(ForgeRegistries.ITEMS.getValue(
//                ResourceLocation.fromNamespaceAndPath("tacz", "ammo_box")));
//        item.getOrCreateTag().putBoolean("AllTypeCreative", true);
//        return item;
//    }

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

    public static String getAttachmentDisplayName(ResourceLocation id) {
        return formatPath(id.getPath());
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
}