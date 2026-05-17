package com.frosty.bedgunwars.entity;

import com.frosty.bedgunwars.BedGunWars;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MvpGunGeoModel extends GeoModel<MvpGunEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(BedGunWars.MOD_ID, "geo/mvpgun.geo.json");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(BedGunWars.MOD_ID, "animations/mvpgun.animation.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BedGunWars.MOD_ID, "textures/entity/mvpgun.png");

    @Override
    public ResourceLocation getModelResource(MvpGunEntity entity) { return MODEL; }

    @Override
    public ResourceLocation getTextureResource(MvpGunEntity entity) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(MvpGunEntity entity) { return ANIMATION; }
}