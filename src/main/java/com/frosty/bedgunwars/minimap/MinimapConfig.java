package com.frosty.bedgunwars.minimap;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class MinimapConfig {

    public static final ForgeConfigSpec SPEC;

    /** Sliding window radius in blocks (half-width of the visible area) */
    public static final ForgeConfigSpec.IntValue WINDOW_RADIUS;

    /** Size multiplier applied to screenWidth * 0.18 base size */
    public static final ForgeConfigSpec.DoubleValue SIZE_MULTIPLIER;

    /** Corner: 0 = top-right, 1 = top-left, 2 = bottom-right, 3 = bottom-left */
    public static final ForgeConfigSpec.IntValue CORNER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("BedGunWars Minimap Client Settings").push("minimap");

        WINDOW_RADIUS = builder
                .comment("Sliding window radius in blocks. The minimap shows this many blocks in each direction from the player. Range: 30-150.")
                .defineInRange("windowRadius", 60, 30, 150);

        SIZE_MULTIPLIER = builder
                .comment("Minimap size multiplier. Base size is screenWidth * 0.18. Range: 0.5-2.0.")
                .defineInRange("sizeMultiplier", 0.6, 0.5, 2.0);

        CORNER = builder
                .comment("Minimap corner position. 0 = top-right, 1 = top-left, 2 = bottom-right, 3 = bottom-left.")
                .defineInRange("corner", 0, 0, 3);

        builder.pop();
        SPEC = builder.build();
    }

    public static void register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, SPEC, "bedgunwars-minimap.toml");
    }
}