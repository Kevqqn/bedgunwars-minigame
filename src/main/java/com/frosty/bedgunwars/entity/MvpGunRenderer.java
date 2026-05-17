package com.frosty.bedgunwars.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class MvpGunRenderer extends GeoEntityRenderer<MvpGunEntity> {

    public static double muzzleWorldX, muzzleWorldY, muzzleWorldZ;

    public MvpGunRenderer(EntityRendererProvider.Context context) {
        super(context, new MvpGunGeoModel());
        addRenderLayer(new MuzzleFlashLayer(this));
    }

    @Override
    public void render(MvpGunEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        CoreGeoBone muzzleBone = getGeoModel().getBone("muzzle_flash").orElse(null);
        if (muzzleBone != null) {
            muzzleWorldX = entity.getX() + (muzzleBone.getPivotX() + muzzleBone.getPosX()) / 16.0;
            muzzleWorldY = entity.getY() + (muzzleBone.getPivotY() + muzzleBone.getPosY()) / 16.0;
            muzzleWorldZ = entity.getZ() - ((muzzleBone.getPivotZ() + muzzleBone.getPosZ()) / 16.0);
        }
    }
}