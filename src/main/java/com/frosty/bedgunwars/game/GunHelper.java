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



