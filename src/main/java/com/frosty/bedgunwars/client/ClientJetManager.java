package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.BedGunWars;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientJetManager {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("bedgunwars", "textures/entity/plane.png");

    // Cached model instance — created once
    private static com.frosty.bedgunwars.entity.JetModel<?> model = null;

    // ── Independent moving sound  

    private static class JetSound extends AbstractTickableSoundInstance {
        private double sx, sy, sz;
        private double prevSx, prevSz;
        final double dx, dz;
        final float speed;

        JetSound(double x, double y, double z, double dx, double dz, float speed) {
            super(com.frosty.bedgunwars.BedGunWars.JET_SOUND.get(),
                    SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.sx = this.prevSx = x;
            this.sy = y;
            this.sz = this.prevSz = z;
            this.dx = dx; this.dz = dz; this.speed = speed;
            this.volume = 1.2f;
            this.pitch = 1.0f;
            this.looping = false;
            this.delay = 0;
            this.attenuation = Attenuation.LINEAR;
            this.x = (float) sx;
            this.y = (float) sy;
            this.z = (float) sz;
        }

        @Override
        public void tick() {
            prevSx = sx; prevSz = sz;
            sx += dx * speed;
            sz += dz * speed;
            // Use 0.5 partial interpolation between prev and current
            this.x = (float)(prevSx + (sx - prevSx) * 0.5);
            this.y = (float) sy;
            this.z = (float)(prevSz + (sz - prevSz) * 0.5);
        }

        @Override public boolean canPlaySound() { return true; }
        @Override public boolean canStartSilent() { return true; }
    }

    // ── Client jet data  ─────────

    static class ClientJet {
        double prevX, prevY, prevZ;
        double x, y, z;
        final double dx, dz;
        final float speed;
        int ticksAlive = 0;
        static final int MAX_TICKS = 200;
        double distanceTravelled = 0;
        static final double MAX_DISTANCE = 600;
        boolean done = false;

        ClientJet(double x, double y, double z, double dx, double dz, float speed) {
            this.x = this.prevX = x;
            this.y = this.prevY = y;
            this.z = this.prevZ = z;
            this.dx = dx; this.dz = dz; this.speed = speed;
        }

        boolean tick() {
            prevX = x; prevY = y; prevZ = z;
            double mx = dx * speed, mz = dz * speed;
            x += mx; z += mz;
            distanceTravelled += Math.sqrt(mx * mx + mz * mz);
            ticksAlive++;
            if (ticksAlive >= MAX_TICKS || distanceTravelled >= MAX_DISTANCE) {
                done = true; return true;
            }
            return false;
        }

        double getRenderX(float p) { return prevX + (x - prevX) * p; }
        double getRenderY(float p) { return prevY + (y - prevY) * p; }
        double getRenderZ(float p) { return prevZ + (z - prevZ) * p; }

        float getYaw() { return (float) -(Math.toDegrees(Math.atan2(-dx, dz))); }
    }

    // ── Pending jets  

    private static class PendingJet {
        final double x, y, z, dx, dz;
        final float speed;
        int ticksRemaining;

        PendingJet(double x, double y, double z, double dx, double dz, float speed, int delay) {
            this.x = x; this.y = y; this.z = z;
            this.dx = dx; this.dz = dz; this.speed = speed;
            this.ticksRemaining = delay;
        }
    }

    private static final List<PendingJet> pendingJets = new ArrayList<>();
    private static final List<ClientJet> jets = new ArrayList<>();

    // ── Public API  ───

    public static void queueJet(double x, double y, double z,
                                double dx, double dz, float speed, int delay) {
        pendingJets.add(new PendingJet(x, y, z, dx, dz, speed, delay));
    }

    public static void spawnJet(double x, double y, double z,
                                double dx, double dz, float speed) {
        ClientJet jet = new ClientJet(x, y, z, dx, dz, speed);
        jets.add(jet);
        Minecraft.getInstance().getSoundManager().play(
                new JetSound(x, y, z, dx, dz, speed));
    }

    public static void clearAll() {
        jets.forEach(j -> j.done = true);
        jets.clear();
        pendingJets.clear();
    }

    // ── Tick  ─────────

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (Minecraft.getInstance().isPaused()) return;

        Iterator<PendingJet> pit = pendingJets.iterator();
        while (pit.hasNext()) {
            PendingJet p = pit.next();
            p.ticksRemaining--;
            if (p.ticksRemaining <= 0) {
                spawnJet(p.x, p.y, p.z, p.dx, p.dz, p.speed);
                pit.remove();
            }
        }

        jets.removeIf(ClientJet::tick);
    }

    @SubscribeEvent
    public void onDimensionChange(
            net.minecraftforge.event.entity.EntityTravelToDimensionEvent event) {
        clearAll();
    }

    // ── Render  ───────

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (jets.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Lazy-init model
        if (model == null) {
            ModelPart root = mc.getEntityModels()
                    .bakeLayer(com.frosty.bedgunwars.entity.JetModel.LAYER_LOCATION);
            model = new com.frosty.bedgunwars.entity.JetModel<>(root);
        }

        float partial = event.getPartialTick();
        var camera = event.getCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        var consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int packedLight = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

        for (ClientJet jet : jets) {
            poseStack.pushPose();
            BedGunWars.LOGGER.info("[Jet] dx={} dz={} yaw={}", jet.dx, jet.dz, jet.getYaw());
            poseStack.translate(
                    jet.getRenderX(partial) - camX,
                    jet.getRenderY(partial) - camY,
                    jet.getRenderZ(partial) - camZ);
            poseStack.mulPose(Axis.YP.rotationDegrees(jet.getYaw() + 180f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            // Scale from model units (1/16 block per unit)
            poseStack.translate(0, -1.5, 0); // adjust Y to center the model vertically
            float scale = 2.5f;
            poseStack.scale(scale, scale, scale);

            model.renderToBuffer(poseStack, consumer, packedLight,
                    OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            poseStack.popPose();
        }

        bufferSource.endBatch(RenderType.entityCutoutNoCull(TEXTURE));
    }
}