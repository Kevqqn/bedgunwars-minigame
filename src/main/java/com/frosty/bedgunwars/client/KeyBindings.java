package com.frosty.bedgunwars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyConflictContext;

import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class KeyBindings {

    public static final KeyMapping GUN_MENU_KEY = new KeyMapping(
            "key.bedgunwars.gun_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.bedgunwars"
    );
    public static final KeyMapping MINIMAP_SETTINGS_KEY = new KeyMapping(
            "key.bedgunwars.minimap_settings",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.bedgunwars"
    );
//    public static final KeyMapping TAB_STATS_KEY = new KeyMapping(
//            "key.bedgunwars.tab_stats",
//            net.minecraftforge.client.settings.KeyConflictContext.IN_GAME,
//            net.minecraftforge.client.settings.KeyModifier.NONE,
//            InputConstants.Type.KEYSYM,
//            GLFW.GLFW_KEY_TAB,
//            "key.categories.bedgunwars"
//    );
}