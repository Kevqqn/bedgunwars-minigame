package com.frosty.bedgunwars.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class JetModel<T extends Entity> extends EntityModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("bedgunwars", "jet"), "main");

    private final ModelPart group5;

    public JetModel(ModelPart root) {
        this.group5 = root.getChild("group5");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition group5 = partdefinition.addOrReplaceChild("group5",
                CubeListBuilder.create(), PartPose.offset(-5.0F, 24.0F, 0.0F));

        PartDefinition root = group5.addOrReplaceChild("root", CubeListBuilder.create()
                        .texOffs(72, 86).addBox(-11.5F, -7.0F, -11.5F, 22.0F, 10.0F, 46.0F, new CubeDeformation(0.0F))
                        .texOffs(142, 155).addBox(47.5F, -5.5F, 12.5F, 4.0F, 3.0F, 17.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(10.5065F, -5.5F, 8.3247F, 23.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
                        .texOffs(114, 115).addBox(-4.5F, -13.0F, -28.5F, 9.0F, 10.0F, 17.0F, new CubeDeformation(0.0F))
                        .texOffs(120, 123).addBox(-5.5F, -7.0F, -46.5F, 11.0F, 8.0F, 9.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(-5.0F, -1.75F, -40.0F, 10.0F, 5.0F, 81.0F, new CubeDeformation(0.0F))
                        .texOffs(101, 0).addBox(-4.5F, -12.75F, -9.5F, 8.0F, 7.0F, 56.0F, new CubeDeformation(0.0F))
                        .texOffs(25, 75).addBox(-4.5F, -12.75F, 46.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(25, 75).addBox(-4.5F, 2.25F, 46.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(25, 75).addBox(-4.5F, 2.25F, 41.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(35, 29).addBox(-4.5F, -13.75F, -11.5F, 8.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        root.addOrReplaceChild("cube_r1", CubeListBuilder.create()
                        .texOffs(69, 142).mirror().addBox(-4.5F, -8.5F, 27.5F, 10.0F, 8.0F, 56.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-7.25F, -3.5F, -37.0F, 0.0F, 0.0F, 0.7854F));
        root.addOrReplaceChild("cube_r2", CubeListBuilder.create()
                        .texOffs(35, 18).addBox(-3.5F, -9.5F, 27.5F, 8.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-8.8F, -4.45F, -39.0F, 0.0F, 0.0F, 0.7854F));
        root.addOrReplaceChild("cube_r3", CubeListBuilder.create()
                        .texOffs(25, 75).addBox(-4.0F, -0.5F, -2.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(25, 75).addBox(-4.0F, -0.5F, 2.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(25, 75).addBox(-4.0F, -0.5F, 7.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
                        .texOffs(25, 75).addBox(-4.0F, -0.5F, 12.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(6.0F, 0.0F, 34.0F, 0.0F, 0.0F, -0.7854F));
        root.addOrReplaceChild("cube_r4", CubeListBuilder.create()
                        .texOffs(25, 75).addBox(-4.0F, -0.5F, -2.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-7.0F, -9.5F, 49.0F, 0.0F, 0.0F, -0.7854F));
        root.addOrReplaceChild("cube_r5", CubeListBuilder.create()
                        .texOffs(69, 142).addBox(-5.5F, -8.5F, 27.5F, 10.0F, 8.0F, 56.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(6.25F, -3.5F, -37.0F, 0.0F, 0.0F, -0.7854F));
        root.addOrReplaceChild("cube_r6", CubeListBuilder.create()
                        .texOffs(120, 123).addBox(0.0F, -3.0F, -23.0F, 11.0F, 8.0F, 9.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-5.5F, -7.0F, -16.5F, 0.0873F, 0.0F, 0.0F));
        root.addOrReplaceChild("cube_r7", CubeListBuilder.create()
                        .texOffs(124, 125).addBox(-4.5F, -5.0F, -2.0F, 9.0F, 10.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -5.9549F, -29.7022F, 0.5236F, 0.0F, 0.0F));
        root.addOrReplaceChild("cube_r8", CubeListBuilder.create()
                        .texOffs(106, 111).addBox(-2.0F, -5.0F, -23.0F, 13.0F, 10.0F, 21.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-4.5F, -5.0F, -7.5F, 0.0436F, 0.0F, 0.0F));
        root.addOrReplaceChild("cube_r9", CubeListBuilder.create()
                        .texOffs(142, 142).addBox(-17.5F, -1.0F, -6.0F, 40.0F, 2.0F, 11.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(27.0F, -4.25F, 26.5F, 0.0F, 0.1745F, 0.0F));
        root.addOrReplaceChild("cube_r10", CubeListBuilder.create()
                        .texOffs(101, 63).addBox(-22.5F, -1.0F, -6.0F, 45.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(28.0F, -4.0F, 10.5F, 0.0F, -0.4363F, 0.0F));

        PartDefinition group2 = root.addOrReplaceChild("group2", CubeListBuilder.create()
                        .texOffs(142, 155).addBox(19.5F, -0.5F, 0.0F, 4.0F, 3.0F, 17.0F, new CubeDeformation(0.0F)),
                PartPose.offset(9.0F, -5.0F, 42.5F));
        group2.addOrReplaceChild("cube_r11", CubeListBuilder.create()
                        .texOffs(68, 150).addBox(3.5F, 0.0F, -6.0F, 19.0F, 1.0F, 11.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, 0.0F, 13.0F, 0.0F, 0.1745F, 0.0F));
        group2.addOrReplaceChild("cube_r12", CubeListBuilder.create()
                        .texOffs(1, 150).addBox(-0.5F, 0.0F, -6.0F, 23.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.4363F, 0.0F));

        PartDefinition group3 = root.addOrReplaceChild("group3", CubeListBuilder.create()
                        .texOffs(142, 155).addBox(19.5F, -0.5F, 0.0F, 4.0F, 3.0F, 17.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(8.0F, -4.0F, 25.5F, 0.0175F, -0.1309F, -1.3963F));
        group3.addOrReplaceChild("cube_r13", CubeListBuilder.create()
                        .texOffs(68, 150).addBox(3.5F, 0.0F, -6.0F, 19.0F, 1.0F, 11.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, 0.0F, 13.0F, 0.0F, 0.1745F, 0.0F));
        group3.addOrReplaceChild("cube_r14", CubeListBuilder.create()
                        .texOffs(1, 150).addBox(-0.5F, 0.0F, -6.0F, 23.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.4363F, 0.0F));

        PartDefinition group4 = root.addOrReplaceChild("group4", CubeListBuilder.create()
                        .texOffs(142, 155).addBox(19.5F, -0.5F, 0.0F, 4.0F, 3.0F, 17.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-10.0F, -4.0F, 25.5F, 0.0175F, -0.1309F, -1.8326F));
        group4.addOrReplaceChild("cube_r15", CubeListBuilder.create()
                        .texOffs(68, 150).addBox(3.5F, 0.0F, -6.0F, 19.0F, 1.0F, 11.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.0F, 0.0F, 13.0F, 0.0F, 0.1745F, 0.0F));
        group4.addOrReplaceChild("cube_r16", CubeListBuilder.create()
                        .texOffs(1, 150).addBox(-0.5F, 0.0F, -6.0F, 23.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.4363F, 0.0F));

        PartDefinition root2 = group5.addOrReplaceChild("root2", CubeListBuilder.create()
                        .texOffs(0, 0).mirror().addBox(-33.5065F, -5.5F, 8.3247F, 23.0F, 2.0F, 16.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(142, 155).mirror().addBox(-51.5F, -5.5F, 12.5F, 4.0F, 3.0F, 17.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(0, 162).mirror().addBox(-2.0F, -7.0F, 34.5F, 3.0F, 5.0F, 17.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(0, 162).mirror().addBox(-22.0F, -7.0F, 34.5F, 3.0F, 5.0F, 17.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(142, 155).mirror().addBox(-43.5F, -5.5F, 42.5F, 4.0F, 3.0F, 17.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offset(10.0F, 0.0F, 0.0F));

        root2.addOrReplaceChild("cube_r17", CubeListBuilder.create()
                        .texOffs(68, 150).mirror().addBox(-22.5F, 0.0F, -6.0F, 19.0F, 1.0F, 11.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-19.0F, -5.0F, 55.5F, 0.0F, -0.1745F, 0.0F));
        root2.addOrReplaceChild("cube_r18", CubeListBuilder.create()
                        .texOffs(1, 150).mirror().addBox(-22.5F, 0.0F, -6.0F, 23.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-20.0F, -5.0F, 42.5F, 0.0F, 0.4363F, 0.0F));
        root2.addOrReplaceChild("cube_r19", CubeListBuilder.create()
                        .texOffs(25, 75).mirror().addBox(-4.0F, -0.5F, -2.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(25, 75).mirror().addBox(-4.0F, -0.5F, 2.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(25, 75).mirror().addBox(-4.0F, -0.5F, 7.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(25, 75).mirror().addBox(-4.0F, -0.5F, 12.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-17.0F, 0.0F, 34.0F, 0.0F, 0.0F, 0.7854F));
        root2.addOrReplaceChild("cube_r20", CubeListBuilder.create()
                        .texOffs(25, 75).mirror().addBox(-4.0F, -0.5F, -2.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-4.0F, -9.5F, 49.0F, 0.0F, 0.0F, 0.7854F));
        root2.addOrReplaceChild("cube_r21", CubeListBuilder.create()
                        .texOffs(142, 142).mirror().addBox(-22.5F, -1.0F, -6.0F, 40.0F, 2.0F, 11.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-27.0F, -4.25F, 26.5F, 0.0F, -0.1745F, 0.0F));
        root2.addOrReplaceChild("cube_r22", CubeListBuilder.create()
                        .texOffs(101, 63).mirror().addBox(-22.5F, -1.0F, -6.0F, 45.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(135, 142).mirror().addBox(-22.5F, 0.0F, -6.0F, 45.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-28.0F, -4.0F, 10.5F, 0.0F, 0.4363F, 0.0F));
        root2.addOrReplaceChild("cube_r23", CubeListBuilder.create()
                        .texOffs(35, 18).mirror().addBox(-4.5F, -9.5F, 27.5F, 8.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(-2.2F, -4.45F, -39.0F, 0.0F, 0.0F, -0.7854F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {}

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        group5.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}