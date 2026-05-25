package com.frosty.bedgunwars.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class MuzzleFlashLayer extends GeoRenderLayer<MvpGunEntity> {

    private static final ResourceLocation FLASH_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("bedgunwars", "textures/entity/common_muzzle_flash.png");

    // Flash visible from tick 20 to tick 24 (1.0s to 1.2s at 20tps)
    private static final int FLASH_START_TICK = 20;
    private static final int FLASH_END_TICK   = 21;
    private static final float FLASH_SIZE     = 0.2f; // size in blocks

    public MuzzleFlashLayer(GeoRenderer<MvpGunEntity> renderer) {
        super(renderer);
    }

    private static float[] getWorldPivot(CoreGeoBone bone) {
        float x = 0, y = 0, z = 0;
        CoreGeoBone current = bone;
        while (current != null) {
            x += current.getPivotX() + current.getPosX();
            y += current.getPivotY() + current.getPosY();
            z += current.getPivotZ() + current.getPosZ();
            current = current.getParent();
        }
        return new float[]{x / 16.0f, y / 16.0f, -(z / 16.0f)};
    }

    @Override
    public void render(PoseStack poseStack, MvpGunEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {

        int animTick = animatable.getAnimTick();
        
        if (animTick < FLASH_START_TICK || animTick > FLASH_END_TICK) return;

        CoreGeoBone flashBone = getGeoModel().getBone("muzzle_flash").orElse(null);
                if (flashBone == null) return;

        
        // Compute flash alpha full at start, fade out
        float progress = (float)(animTick - FLASH_START_TICK) / (FLASH_END_TICK - FLASH_START_TICK);
        float alpha = 1.0f - progress;

        poseStack.pushPose();

// double ex = animatable.getX();
// double ey = animatable.getY();
// double ez = animatable.getZ();

        float[] worldPos = getWorldPivot(flashBone);
        float bx = worldPos[0] + 0.1f;
        float by = worldPos[1] - 2.6f;
        float bz = worldPos[2] + 1.15f;
        poseStack.translate(bx, by, bz);

        // Billboard
        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.mulPose(camera.rotation());

        poseStack.scale(FLASH_SIZE, FLASH_SIZE, FLASH_SIZE);

        // Draw quad
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer flashBuffer = bufferSource.getBuffer(
                RenderType.entityTranslucentEmissive(FLASH_TEXTURE));

        int a = (int)(alpha * 255);
        flashBuffer.vertex(matrix, -1, -1, 0).color(255, 255, 255, a).uv(0, 1).overlayCoords(packedOverlay).uv2(0xF000F0).normal(0, 0, 1).endVertex();
        flashBuffer.vertex(matrix,  1, -1, 0).color(255, 255, 255, a).uv(1, 1).overlayCoords(packedOverlay).uv2(0xF000F0).normal(0, 0, 1).endVertex();
        flashBuffer.vertex(matrix,  1,  1, 0).color(255, 255, 255, a).uv(1, 0).overlayCoords(packedOverlay).uv2(0xF000F0).normal(0, 0, 1).endVertex();
        flashBuffer.vertex(matrix, -1,  1, 0).color(255, 255, 255, a).uv(0, 0).overlayCoords(packedOverlay).uv2(0xF000F0).normal(0, 0, 1).endVertex();
        poseStack.popPose();
    }
}