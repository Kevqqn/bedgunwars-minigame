package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.entity.MvpCharacterEntity;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class MvpCutsceneClient {

    private static boolean active = false;

    // Camera transform stored each frame by MvpCharacterRenderer
    private static double camX, camY, camZ;
    private static float camPitch, camYaw, camRoll;
    private static boolean cameraReady = false;

    // Winner data for HUD
    private static String winnerName = "";
    private static int kills = 0;
    private static long startTick = 0;

    // Skin texture
    private static ResourceLocation winnerSkin = null;
    private static DynamicTexture skinTexture = null;
    private static final ResourceLocation SKIN_RL =
            ResourceLocation.fromNamespaceAndPath("bedgunwars", "mvp_winner_skin");

    // HUD timing all relative to startTick (which is the game tick when entities spawn)
    // 2.75s = 55 ticks after animation starts
    public static final int HUD_SHOW_TICK = 55;
    // Fade to black: last 5 frames of 136-tick animation
    public static final int FADE_START_TICK = 126;
    public static final int FADE_END_TICK = 136;

    // FOV
    private static final float CUTSCENE_FOV = 40.0f;
    private static float originalFov = 70.0f;

    private static MvpCharacterEntity trackedEntity = null;

    public static void clearHoldBlack() {
        holdBlack = false;
        holdBlackStartTick = 0;
    }

    private static long holdBlackStartTick = 0;

    private static boolean cameraEnabled = false;

    public static void setCameraEnabled(boolean enabled) {
        cameraEnabled = enabled;
    }

    public static void setTrackedEntity(com.frosty.bedgunwars.entity.MvpCharacterEntity entity) {
        trackedEntity = entity;
    }

    public static void startSkinPrefetch(UUID uuid, String name) {
        winnerName = name; // needed by fetchSkinAsync
        fetchSkinAsync(uuid, false); // slim will be updated from API anyway
    }

    public static void start(UUID winnerUUID, String name, int killCount, long tick, boolean slim) {
        originalFov = (float) Minecraft.getInstance().options.fov().get();
        active = true;
        winnerName = name;
        kills = killCount;
        startTick = tick;

        // Only fetch if prefetch didn't already load the skin
        if (winnerSkin == null) {
            fetchSkinAsync(winnerUUID, slim);
        }

        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                com.frosty.bedgunwars.BedGunWars.MVP_SFX.get().getLocation(),
                net.minecraft.sounds.SoundSource.MASTER,
                2.0f,  // volume
                1.0f,  // pitch
                net.minecraft.util.RandomSource.create(),
                false, 0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                0, 0, 0, true));
    }

    private static boolean holdBlack = false;

    public static void end() {
        active = false;
        cameraReady = false;
        preFadeActive = false; // ADD THIS
        Minecraft.getInstance().options.fov().set((int) originalFov);
        releaseTexture();
        winnerSkin = null;
        winnerName = "";
        kills = 0;
        trackedEntity = null;
        holdBlack = true;
    }

    @SubscribeEvent
    public static void onHoldBlack(RenderGuiOverlayEvent.Post event) {
        if (!holdBlack) return;
        if (event.getOverlay() != VanillaGuiOverlay.VIGNETTE.type()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gui = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        if (EndScoreboardClient.isActive()) {
            // Fade out holdBlack over 10 ticks once scoreboard is active
            long currentTick = mc.level != null ? mc.level.getGameTime() : 0;
            if (holdBlackStartTick == 0) holdBlackStartTick = currentTick;
            long elapsed = currentTick - holdBlackStartTick;

            if (elapsed >= 10) {
                holdBlack = false;
                holdBlackStartTick = 0;
                return;
            }

            float fadeIn = 1f - (float) elapsed / 10f;
            int alpha = (int)(fadeIn * 255);
            gui.fill(0, 0, screenW, screenH, (alpha << 24));
        } else {
            gui.fill(0, 0, screenW, screenH, 0xFF000000);
        }
    }

    public static boolean isActive() { return active; }
    public static String getWinnerName() { return winnerName; }
    public static int getKills() { return kills; }
    public static long getStartTick() { return startTick; }
    public static ResourceLocation getWinnerSkin() { return winnerSkin; }
    public static float getOriginalFov() { return originalFov; }

    private static boolean preFadeActive = false;
    private static long preFadeStartTick = 0;

    public static void startPreFade() {
        preFadeActive = true;
        preFadeStartTick = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0;
    }

    @SubscribeEvent
    public static void onPreFadeOverlay(RenderGuiOverlayEvent.Post event) {
        if (!preFadeActive) return;
        if (event.getOverlay() != VanillaGuiOverlay.VIGNETTE.type()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gui = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        long currentTick = mc.level != null ? mc.level.getGameTime() : 0;
        long elapsed = currentTick - preFadeStartTick;

        float fadeOut = Math.min(1f, (float) elapsed / 10f);
        int alpha = (int)(fadeOut * 255);
        gui.fill(0, 0, screenW, screenH, (alpha << 24));

        if (elapsed >= 10) {
            if ((active && cameraReady && MvpCutsceneClient.getWinnerSkin() != null) || elapsed >= 20) {
                preFadeActive = false;
            } else {
                gui.fill(0, 0, screenW, screenH, 0xFF000000);
            }
        }
    }

    public static void storeCameraTransform(double x, double y, double z, float pitch, float yaw, float roll) {
        camX = x; camY = y; camZ = z;
        camPitch = pitch; camYaw = yaw; camRoll = roll;
        cameraReady = true;
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!active || !cameraReady || !cameraEnabled) return;
        event.setFOV(CUTSCENE_FOV);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active || !cameraReady || !cameraEnabled) return;
        event.setPitch(camPitch);
        event.setYaw(camYaw);
        event.setRoll(camRoll);
        ((com.frosty.bedgunwars.mixin.CameraAccessor)
                Minecraft.getInstance().gameRenderer.getMainCamera())
                .invokeSetPosition(camX, camY, camZ);
    }

    private static void fetchSkinAsync(UUID uuid, boolean slim) {
        Thread thread = new Thread(() -> {
            try {
                // Step 1: get real Mojang UUID from username
                String mojangProfileUrl = "https://api.mojang.com/users/profiles/minecraft/" + winnerName;
                java.net.HttpURLConnection profileConn = (java.net.HttpURLConnection) new java.net.URL(mojangProfileUrl).openConnection();
                profileConn.setRequestProperty("User-Agent", "BedGunWars/1.0");
                profileConn.setConnectTimeout(5000);
                profileConn.setReadTimeout(5000);

                if (profileConn.getResponseCode() == 404) {
                    com.frosty.bedgunwars.BedGunWars.LOGGER.warn("[MVP Skin] Username not found on Mojang, using default skin");
                    useDefaultSkin(uuid);
                    return;
                }

                String profileJson = new String(profileConn.getInputStream().readAllBytes());
                profileConn.disconnect();

                // UUID extraction
                String idKey = "\"id\"";
                int idPos = profileJson.indexOf(idKey) + idKey.length();
                while (idPos < profileJson.length() && (profileJson.charAt(idPos) == ' ' || profileJson.charAt(idPos) == ':' || profileJson.charAt(idPos) == '"')) {
                    idPos++;
                }
                int idEnd = profileJson.indexOf("\"", idPos);
                String realUuidStr = profileJson.substring(idPos, idEnd);
                com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MVP Skin] Profile JSON raw: {}", profileJson);
                com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MVP Skin] Extracted UUID string: '{}'", realUuidStr);

                String dashedUuid = realUuidStr.replaceFirst(
                        "([0-9a-f]{8})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{12})",
                        "$1-$2-$3-$4-$5");
                com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MVP Skin] Real UUID: {}", dashedUuid);

                // Step 2: fetch skin data using real UUID
                String sessionUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + realUuidStr;
                java.net.HttpURLConnection sessionConn = (java.net.HttpURLConnection) new java.net.URL(sessionUrl).openConnection();
                sessionConn.setRequestProperty("User-Agent", "BedGunWars/1.0");
                sessionConn.setConnectTimeout(5000);
                sessionConn.setReadTimeout(5000);

                if (sessionConn.getResponseCode() != 200) {
                    com.frosty.bedgunwars.BedGunWars.LOGGER.warn("[MVP Skin] Session server returned {}, using default skin", sessionConn.getResponseCode());
                    useDefaultSkin(uuid);
                    return;
                }

                String sessionJson = new String(sessionConn.getInputStream().readAllBytes());
                sessionConn.disconnect();

                // Step 3: decode base64 textures property
                String valueKey = "\"value\"";
                int valPos = sessionJson.indexOf(valueKey) + valueKey.length();
                while (valPos < sessionJson.length() && (sessionJson.charAt(valPos) == ' ' || sessionJson.charAt(valPos) == ':' || sessionJson.charAt(valPos) == '"')) {
                    valPos++;
                }
                int valEnd = sessionJson.indexOf("\"", valPos);
                String encoded = sessionJson.substring(valPos, valEnd);
                com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MVP Skin] Encoded value length: {}", encoded.length());
                String decoded = new String(java.util.Base64.getDecoder().decode(encoded));

                com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MVP Skin] Decoded textures: {}", decoded);


                // skin regardless of spacing
                String skinKey = "\"SKIN\"";
                int skinPos = decoded.indexOf(skinKey);
                if (skinPos == -1) {
                    com.frosty.bedgunwars.BedGunWars.LOGGER.warn("[MVP Skin] No SKIN entry, using default skin");
                    useDefaultSkin(uuid);
                    return;
                }
                // Navigate to url value
                String urlKey = "\"url\"";
                int urlPos = decoded.indexOf(urlKey, skinPos) + urlKey.length();
                while (urlPos < decoded.length() && (decoded.charAt(urlPos) == ' ' || decoded.charAt(urlPos) == ':' || decoded.charAt(urlPos) == '"')) {
                    urlPos++;
                }
                int urlEnd = decoded.indexOf("\"", urlPos);
                String skinUrl = decoded.substring(urlPos, urlEnd);

                // Step 6: detect slim from metadata
                boolean isSlimFromApi = decoded.contains("\"slim\":true");
                com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MVP Skin] Skin URL: {}, slim: {}", skinUrl, isSlimFromApi);

                // Step 7: download texture
                java.net.HttpURLConnection skinConn = (java.net.HttpURLConnection) new java.net.URL(skinUrl).openConnection();
                skinConn.setRequestProperty("User-Agent", "BedGunWars/1.0");
                skinConn.setConnectTimeout(5000);
                skinConn.setReadTimeout(5000);
                NativeImage image = NativeImage.read(skinConn.getInputStream());
                skinConn.disconnect();

                com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MVP Skin] Fetch success, uploading texture");
                boolean finalIsSlim = isSlimFromApi;
                Minecraft.getInstance().execute(() -> {
                    uploadSkin(image);
                    if (trackedEntity != null) {
                        trackedEntity.setSlim(finalIsSlim);
                    }
                });

            } catch (Exception e) {
                com.frosty.bedgunwars.BedGunWars.LOGGER.error("[MVP Skin] Fetch failed: {}", e.getMessage());
                useDefaultSkin(uuid);
            }
        }, "mvp-skin-fetch");
        thread.setDaemon(true);
        thread.start();
    }

    private static void useDefaultSkin(UUID uuid) {
        Minecraft.getInstance().execute(() -> {
            winnerSkin = net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(uuid);
            com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MVP Skin] Using default skin: {}", winnerSkin);
        });
    }

    private static void uploadSkin(NativeImage image) {
        releaseTexture();
        skinTexture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(SKIN_RL, skinTexture);
        winnerSkin = SKIN_RL;
    }

    private static void releaseTexture() {
        if (skinTexture != null) {
            Minecraft.getInstance().getTextureManager().release(SKIN_RL);
            skinTexture.close();
            skinTexture = null;
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (active) end();
        cameraEnabled = false;
        holdBlack = false;
    }
    @SubscribeEvent
    public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        if (active) event.setCanceled(true);
    }
}