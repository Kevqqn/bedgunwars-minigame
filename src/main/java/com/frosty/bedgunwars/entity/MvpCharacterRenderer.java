package com.frosty.bedgunwars.entity;

import com.frosty.bedgunwars.client.MvpCutsceneClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class MvpCharacterRenderer extends GeoEntityRenderer<MvpCharacterEntity> {

    public MvpCharacterRenderer(EntityRendererProvider.Context context) {
        super(context, new MvpGeoModel());
    }

    @Override
    public ResourceLocation getTextureLocation(MvpCharacterEntity entity) {
        ResourceLocation skin = MvpCutsceneClient.getWinnerSkin();
        if (skin != null) return skin;
        return super.getTextureLocation(entity);
    }

    @Override
    public void render(MvpCharacterEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (MvpCutsceneClient.isActive()) {
            MvpCutsceneClient.setTrackedEntity(entity);
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        if (!MvpCutsceneClient.isActive()) return;

        CoreGeoBone cameraBone = getGeoModel().getBone("Camera").orElse(null);
        if (cameraBone == null) return;

        // Pivot + animated delta, converted from Blockbench units to blocks
        // Z negated: Blockbench Z+ faces viewer, Minecraft Z+ is south
        double camX = entity.getX() + (cameraBone.getPivotX() + cameraBone.getPosX()) / 16.0;
        double camY = entity.getY() + (cameraBone.getPivotY() + cameraBone.getPosY()) / 16.0;
        double camZ = entity.getZ() - ((cameraBone.getPivotZ() + cameraBone.getPosZ()) / 16.0);

        // getRotX/Y/Z() returns radians, negate for BlockbenchtoMinecraft handedness
        // +180 on yaw so camera faces toward the character
        float pitch = (float) Math.toDegrees(cameraBone.getRotX());
        float yaw   = -(float) Math.toDegrees(cameraBone.getRotY()) + 180.0f;
        float roll  = (float) Math.toDegrees(cameraBone.getRotZ());

        MvpCutsceneClient.storeCameraTransform(camX, camY, camZ, pitch, yaw, roll);
    }
}