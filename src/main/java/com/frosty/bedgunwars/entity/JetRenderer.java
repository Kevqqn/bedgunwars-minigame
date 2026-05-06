//package com.frosty.bedgunwars.entity;
//
//import com.mojang.blaze3d.vertex.*;
//import com.mojang.math.Axis;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.renderer.MultiBufferSource;
//import net.minecraft.client.renderer.RenderType;
//import net.minecraft.client.renderer.entity.EntityRenderer;
//import net.minecraft.client.renderer.entity.EntityRendererProvider;
//import net.minecraft.client.renderer.texture.OverlayTexture;
//import net.minecraft.resources.ResourceLocation;
//import net.minecraftforge.api.distmarker.Dist;
//import net.minecraftforge.api.distmarker.OnlyIn;
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.util.ArrayList;
//import java.util.List;
//
//@OnlyIn(Dist.CLIENT)
//public class JetRenderer extends EntityRenderer<JetEntity> {
//
//    private static final ResourceLocation TEXTURE =
//            ResourceLocation.fromNamespaceAndPath("bedgunwars", "textures/entity/plane.png");
//    private static final ResourceLocation OBJ_LOC =
//            ResourceLocation.fromNamespaceAndPath("bedgunwars", "models/obj/jet.obj");
//
//    // Parsed geometry: each entry is [x,y,z, u,v, nx,ny,nz]
//    private float[][] tris = null;
//    private boolean loaded = false;
//
//    public JetRenderer(EntityRendererProvider.Context context) {
//        super(context);
//    }
//
//    @Override
//    public ResourceLocation getTextureLocation(JetEntity entity) {
//        return TEXTURE;
//    }
//
//    private void loadOBJ() {
//        if (loaded) return;
//        loaded = true;
//        try {
//            var resource = Minecraft.getInstance().getResourceManager().getResource(OBJ_LOC);
//            if (resource.isEmpty()) return;
//
//            List<float[]> verts = new ArrayList<>();
//            List<float[]> uvs = new ArrayList<>();
//            List<float[]> norms = new ArrayList<>();
//            List<float[]> faces = new ArrayList<>(); // each face = 9 floats: [x,y,z,u,v,nx,ny,nz, ...] * 3
//
//            try (BufferedReader reader = new BufferedReader(
//                    new InputStreamReader(resource.get().open()))) {
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    line = line.trim();
//                    if (line.startsWith("v ")) {
//                        String[] p = line.split("\\s+");
//                        verts.add(new float[]{
//                                Float.parseFloat(p[1]),
//                                Float.parseFloat(p[2]),
//                                Float.parseFloat(p[3])});
//                    } else if (line.startsWith("vt ")) {
//                        String[] p = line.split("\\s+");
//                        uvs.add(new float[]{
//                                Float.parseFloat(p[1]),
//                                Float.parseFloat(p[2])}); // no flip
//                    } else if (line.startsWith("vn ")) {
//                        String[] p = line.split("\\s+");
//                        norms.add(new float[]{
//                                Float.parseFloat(p[1]),
//                                Float.parseFloat(p[2]),
//                                Float.parseFloat(p[3])});
//                    } else if (line.startsWith("f ")) {
//                        String[] parts = line.split("\\s+");
//                        // triangulate fan from first vertex
//                        int[][] indices = new int[parts.length - 1][];
//                        for (int i = 0; i < parts.length - 1; i++) {
//                            String[] idx = parts[i + 1].split("/");
//                            indices[i] = new int[]{
//                                    Integer.parseInt(idx[0]) - 1,
//                                    idx.length > 1 && !idx[1].isEmpty() ? Integer.parseInt(idx[1]) - 1 : -1,
//                                    idx.length > 2 && !idx[2].isEmpty() ? Integer.parseInt(idx[2]) - 1 : -1
//                            };
//                        }
//                        for (int i = 1; i < indices.length - 1; i++) {
//                            for (int j : new int[]{0, i, i + 1}) {
//                                int vi = indices[j][0];
//                                int ti = indices[j][1];
//                                int ni = indices[j][2];
//                                float[] v = verts.get(vi);
//                                float[] uv = ti >= 0 ? uvs.get(ti) : new float[]{0, 0};
//                                float[] n = ni >= 0 ? norms.get(ni) : new float[]{0, 1, 0};
//                                faces.add(new float[]{v[0], v[1], v[2], uv[0], uv[1], n[0], n[1], n[2]});
//                            }
//                        }
//                    }
//                }
//            }
//            tris = faces.toArray(new float[0][]);
//            Minecraft.getInstance().gui.getChat().addMessage(
//                    net.minecraft.network.chat.Component.literal("[Jet] OBJ loaded: " + tris.length + " triangles"));
//        } catch (Exception e) {
//            Minecraft.getInstance().gui.getChat().addMessage(
//                    net.minecraft.network.chat.Component.literal("[Jet] OBJ load error: " + e.getMessage()));
//        }
//    }
//
//    @Override
//    public void render(JetEntity entity, float entityYaw, float partialTick,
//                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
//        loadOBJ();
//        if (tris == null || tris.length == 0) return;
//
//        poseStack.pushPose();
//        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw + 180f));
//        // Scale: Blockbench exports in pixel units (16 per block)
//        float scale = 1.0f;
//        poseStack.scale(scale, scale, scale);
//
//        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
//        PoseStack.Pose pose = poseStack.last();
//
//        for (float[] v : tris) {
//            consumer.vertex(pose.pose(), v[0], v[1], v[2])
//                    .color(1f, 1f, 1f, 1f)
//                    .uv(v[3], v[4])
//                    .overlayCoords(OverlayTexture.NO_OVERLAY)
//                    .uv2(packedLight)
//                    .normal(pose.normal(), v[5], v[6], v[7])
//                    .endVertex();
//        }
//
//        poseStack.popPose();
//        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
//    }
//}