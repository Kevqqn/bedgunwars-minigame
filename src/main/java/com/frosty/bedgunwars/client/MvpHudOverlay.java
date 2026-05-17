package com.frosty.bedgunwars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class MvpHudOverlay {

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!MvpCutsceneClient.isActive()) return;

        var overlay = event.getOverlay();
        if (overlay == VanillaGuiOverlay.HOTBAR.type()
                || overlay == VanillaGuiOverlay.PLAYER_HEALTH.type()
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

    private static String buildRevealText(String full, long hudElapsed, int ticksPerChar) {
        if (hudElapsed <= 0) return "\u00a7k" + full; // all scrambled
        int revealed = (int)(hudElapsed / ticksPerChar);
        if (revealed >= full.length()) return full; // fully revealed

        String realPart = full.substring(0, revealed);
        String scrambledPart = "\u00a7k" + full.substring(revealed); // §k scrambles the rest
        return realPart + scrambledPart;
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!MvpCutsceneClient.isActive()) return;
        if (event.getOverlay() != VanillaGuiOverlay.VIGNETTE.type()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gui = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        long currentTick = mc.level != null ? mc.level.getGameTime() : 0;
        long elapsed = currentTick - MvpCutsceneClient.getStartTick();

        // DEBUG remove once confirmed working
        gui.drawCenteredString(mc.font, "elapsed: " + elapsed + " startTick: " + MvpCutsceneClient.getStartTick(),
                screenW / 2, 10, 0xFFFFFFFF);

        // Fade in from black at cutscene start 10 ticks
        if (elapsed < 10) {
            float fadeIn = 1f - (float) elapsed / 10f;
            int alpha = (int)(fadeIn * 255);
            gui.fill(0, 0, screenW, screenH, (alpha << 24));
        }

        if (elapsed >= MvpCutsceneClient.HUD_SHOW_TICK) {
            float textAlpha = Math.min(1f, (elapsed - MvpCutsceneClient.HUD_SHOW_TICK) / 10f);
            int alpha = (int)(textAlpha * 255);
            int white = (alpha << 24) | 0xFFFFFF;
            int gold  = (alpha << 24) | 0xFFD700;

            int centerX = screenW * 2 / 3;
            int mvpY    = (int)(screenH * 0.30f);
            int nameY   = (int)(screenH * 0.42f);
            int killsY  = (int)(screenH * 0.52f);

            // Ticks since HUD appeared
            long hudElapsed = elapsed - MvpCutsceneClient.HUD_SHOW_TICK;

            // Every 3 ticks one more character reveals — scrambled prefix replaced by real text
            String mvpFull   = "Match MVP!";
            String nameFull  = MvpCutsceneClient.getWinnerName();
            String killsFull = MvpCutsceneClient.getKills() < 0 ? "Kills: DEBUG" : "Kills: " + MvpCutsceneClient.getKills();

            String mvpLine   = buildRevealText(mvpFull, hudElapsed, 3);
            String nameLine  = buildRevealText(nameFull, hudElapsed - 6, 3);  // name reveals slightly after MVP
            String killsLine = buildRevealText(killsFull, hudElapsed - 12, 3); // kills reveals after name

            // "Match MVP!" — large, gold
            gui.pose().pushPose();
            gui.pose().translate(centerX, mvpY, 0);
            gui.pose().scale(3.0f, 3.0f, 1.0f);
            gui.pose().translate(-centerX, -mvpY, 0);
            gui.drawCenteredString(mc.font, mvpLine, centerX, mvpY, gold);
            gui.pose().popPose();

            // "<name>" — medium, white
            gui.pose().pushPose();
            gui.pose().translate(centerX, nameY, 0);
            gui.pose().scale(2.0f, 2.0f, 1.0f);
            gui.pose().translate(-centerX, -nameY, 0);
            gui.drawCenteredString(mc.font, nameLine, centerX, nameY, white);
            gui.pose().popPose();

            // "Kills: X" — large, white
            gui.pose().pushPose();
            gui.pose().translate(centerX, killsY, 0);
            gui.pose().scale(3.0f, 3.0f, 1.0f);
            gui.pose().translate(-centerX, -killsY, 0);
            gui.drawCenteredString(mc.font, killsLine, centerX, killsY, white);
            gui.pose().popPose();
        }

        if (elapsed >= MvpCutsceneClient.FADE_START_TICK) {
            float fadeProgress = Math.min(1f,
                    (float)(elapsed - MvpCutsceneClient.FADE_START_TICK)
                            / (MvpCutsceneClient.FADE_END_TICK - MvpCutsceneClient.FADE_START_TICK));
            int blackAlpha = (int)(fadeProgress * 255);
            gui.fill(0, 0, screenW, screenH, (blackAlpha << 24));
        }
    }
}