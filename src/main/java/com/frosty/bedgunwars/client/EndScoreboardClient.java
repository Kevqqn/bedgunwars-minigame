package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.network.TabStatsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class EndScoreboardClient {

    private static boolean active = false;

    // Camera position above trapped chest, facing beacon
    private static double camX, camY, camZ;
    private static float camPitch, camYaw;

    // Fade timing (in ticks)
    // 0-10: fade in, 10-150: visible, 150-160: fade out
    private static final int FADE_IN_END    = 10;
    private static final int FADE_OUT_START = 150;
    private static final int TOTAL_TICKS    = 160;

    private static long startTick = 0;
    private static TabStatsPacket cachedStats = null;

    // FOV
// private static float originalFov = 70.0f;
    private static final float SCOREBOARD_FOV = 70.0f;

    public static void start(double cx, double cy, double cz,
                             double beaconX, double beaconY, double beaconZ,
                             TabStatsPacket stats) {
        active = true;
        camX = cx; camY = cy; camZ = cz;
        cachedStats = stats;

        // Calculate pitch and yaw to face beacon from camera position
        double dx = beaconX - cx;
        double dy = beaconY - cy;
        double dz = beaconZ - cz;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        camPitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));
        camYaw   = (float)  Math.toDegrees(Math.atan2(-dx, dz));

        startTick = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0;

        // Push stats to TabStatsScreen so it can render
        TabStatsScreen.updateCache(stats);
        TabStatsScreen.visible = true;
    }

    public static void end() {
        com.frosty.bedgunwars.BedGunWars.LOGGER.info("[Scoreboard] end() called, active={}", active);
        com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MVP] end() called, active={}", active);
        active = false;
        TabStatsScreen.visible = false;
        Minecraft.getInstance().options.fov().set((int) MvpCutsceneClient.getOriginalFov());
        cachedStats = null;
        MvpCutsceneClient.clearHoldBlack();
    }

    public static boolean isActive() { return active; }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active) return;
        event.setPitch(camPitch);
        event.setYaw(camYaw);
        ((com.frosty.bedgunwars.mixin.CameraAccessor)
                Minecraft.getInstance().gameRenderer.getMainCamera())
                .invokeSetPosition(camX, camY, camZ);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!active) return;
        event.setFOV(SCOREBOARD_FOV);
    }

    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        if (active) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!active) return;
        var overlay = event.getOverlay();
        // Cancel all vanilla HUD except HOTBAR (TabStatsScreen renders on HOTBAR post)
        if (overlay == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || overlay == VanillaGuiOverlay.FOOD_LEVEL.type()
                || overlay == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || overlay == VanillaGuiOverlay.AIR_LEVEL.type()
                || overlay == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                || overlay == VanillaGuiOverlay.CROSSHAIR.type()
                || overlay == VanillaGuiOverlay.JUMP_BAR.type()
                || overlay == VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type()
                || overlay == VanillaGuiOverlay.PLAYER_LIST.type()
                || overlay == VanillaGuiOverlay.POTION_ICONS.type()
                || overlay == VanillaGuiOverlay.SCOREBOARD.type()
                || overlay == VanillaGuiOverlay.ITEM_NAME.type()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!active) return;
        if (event.getOverlay() != VanillaGuiOverlay.VIGNETTE.type()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gui = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        long currentTick = mc.level != null ? mc.level.getGameTime() : 0;
        long elapsed = currentTick - startTick;

        // Fade out: 150-160 ticks (clear to black)
        if (elapsed >= FADE_OUT_START) {
            float fadeOut = Math.min(1f, (float)(elapsed - FADE_OUT_START) / (TOTAL_TICKS - FADE_OUT_START));
            int alpha = (int)(fadeOut * 255);
            gui.fill(0, 0, screenW, screenH, (alpha << 24));
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (active) end();
    }
}