package com.frosty.bedgunwars.entity;

import com.frosty.bedgunwars.BedGunWars;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MvpGeoModel extends GeoModel<MvpCharacterEntity> {

    private static final ResourceLocation MODEL_WIDE =
            ResourceLocation.fromNamespaceAndPath(BedGunWars.MOD_ID, "geo/mvpcharacter_wide.geo.json");
    private static final ResourceLocation MODEL_SLIM =
            ResourceLocation.fromNamespaceAndPath(BedGunWars.MOD_ID, "geo/mvpcharacter_slim.geo.json");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(BedGunWars.MOD_ID, "animations/mvpcharacter.animation.json");
    // Texture is set dynamically in renderer; this is a fallback
    private static final ResourceLocation TEXTURE_FALLBACK =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");

    @Override
    public ResourceLocation getModelResource(MvpCharacterEntity entity) {
        return entity.isSlim ? MODEL_SLIM : MODEL_WIDE;
    }

    @Override
    public ResourceLocation getTextureResource(MvpCharacterEntity entity) {
        return TEXTURE_FALLBACK;
    }

    @Override
    public ResourceLocation getAnimationResource(MvpCharacterEntity entity) {
        return ANIMATION;
    }
}