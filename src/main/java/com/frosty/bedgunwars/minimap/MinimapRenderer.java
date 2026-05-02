package com.frosty.bedgunwars.minimap;

import com.frosty.bedgunwars.BedGunWars;
import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameModeType;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class MinimapRenderer {

    public static final MinimapChunkScanner SCANNER = new MinimapChunkScanner();
    public static final MinimapTexture TEXTURE = new MinimapTexture();

    // Enemy dot visibility
    public static final Set<UUID> visibleEnemyDots = ConcurrentHashMap.newKeySet();
    public static boolean showAllEnemyDots = false;

    // Set by onGameStart (server/logic thread), consumed lazily on render thread
    private static volatile boolean pendingInit = false;


    private static volatile int pendingBeaconX, pendingBeaconZ, pendingRadius;

    // Chunk feeding throttle
    private int chunkFeedCooldown = 0;
    private int debugLogCooldown = 0;
    private static final int CHUNK_FEED_INTERVAL = 20;

    private static volatile boolean started = false;

    public static boolean isStarted() { return started; }
    
    // Called from GameTickHandler (server tick thread) — only sets flags,
    // never touches GL or DynamicTexture


    public static void startWithParams(int beaconX, int beaconZ, int radius) {
        if (started) return;
        started = true;
        SCANNER.start();
        pendingBeaconX = beaconX;
        pendingBeaconZ = beaconZ;
        pendingRadius  = radius;
        pendingInit    = true;
    }

    public static void onGameEnd() {
        started = false;
        pendingInit = false;
        SCANNER.stop();
        pendingDispose = true;
        clearEnemyDots();
    }

    private static volatile boolean pendingDispose = false;

    public static void clearEnemyDots() {
        visibleEnemyDots.clear();
        showAllEnemyDots = false;
    }
    // Render event — everything GL-touching happens here (render thread)

    @SubscribeEvent
    public void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        // Handle pending dispose first
        if (pendingDispose) {
            pendingDispose = false;
            TEXTURE.dispose();
            return;
        }

        // Handle pending init (lazy, on render thread)
        if (pendingInit) {
            pendingInit = false;
            TEXTURE.init(pendingBeaconX, pendingBeaconZ, pendingRadius);
            chunkFeedCooldown = 0;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!TEXTURE.isInitialized()) return;

        // Feed loaded chunks to scanner once per second
        chunkFeedCooldown--;
        if (chunkFeedCooldown <= 0) {
            chunkFeedCooldown = CHUNK_FEED_INTERVAL;
            feedLoadedChunks(mc);
        }

        // Composite pending chunks → GPU
        TEXTURE.update(SCANNER);

        // Screen layout
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        double sizeMult = MinimapConfig.SIZE_MULTIPLIER.get();
        int mapSize = (int) (screenW * 0.18 * sizeMult);
        int margin = 6;

        int corner = MinimapConfig.CORNER.get();
        int mapX = switch (corner) {
            case 1, 3 -> margin;
            default   -> screenW - mapSize - margin;
        };
        int mapY = switch (corner) {
            case 2, 3 -> screenH - mapSize - margin - 20;
            default   -> margin;
        };

        int windowRadius = MinimapConfig.WINDOW_RADIUS.get();
        int halfBlocks = windowRadius;
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        int vpX = TEXTURE.viewportOriginX(playerX, halfBlocks);
        int vpZ = TEXTURE.viewportOriginZ(playerZ, halfBlocks);
        int vpSize = halfBlocks * 2;
        int texSize = TEXTURE.getTexSize();
        float scale = (float) mapSize / vpSize; // add this line

        GuiGraphics gui = event.getGuiGraphics();

        // Border
        gui.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1, 0xFF000000);

        // Map texture — blit viewport
        gui.blit(TEXTURE.getTextureLocation(),
                mapX, mapY, mapSize, mapSize,
                vpX, vpZ, vpSize, vpSize,
                texSize, texSize);

        // Own player dot — always centered
        // Own player dot — centered, but offset if viewport is clamped at texture edge
        int playerTexX = (int) playerX - TEXTURE.getOriginX();
        int playerTexZ = (int) playerZ - TEXTURE.getOriginZ();
//        float scale = (float) mapSize / (halfBlocks * 2);
        int dotX = mapX + Math.round((playerTexX - vpX) * scale);
        int dotZ = mapY + Math.round((playerTexZ - vpZ) * scale);
        gui.fill(dotX - 2, dotZ - 2, dotX + 2, dotZ + 2, 0xFF00FF00);

        // Other dots
        GameSession session = GameManager.getSession();
        if (session != null) {
            drawOtherDots(gui, mc, session, mapX, mapY, mapSize, halfBlocks, playerX, playerZ, vpX, vpZ, scale);
        }

        // UI chrome
        gui.drawString(mc.font, "N", mapX + mapSize / 2 - 3, mapY - 3, 0xFFFFFFFF);
        drawScaleBar(gui, mc, mapX, mapY, mapSize, halfBlocks);
    }

    
    // Chunk feeding — render thread, once per second
    

    private void feedLoadedChunks(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        int windowRadius = MinimapConfig.WINDOW_RADIUS.get();
        int chunkRadius = (windowRadius / 16) + 1;
        int playerChunkX = mc.player.chunkPosition().x;
        int playerChunkZ = mc.player.chunkPosition().z;

        SCANNER.centerChunkX = playerChunkX;
        SCANNER.centerChunkZ = playerChunkZ;
        SCANNER.windowChunkRadius = chunkRadius;

        int minY = mc.level.getMinBuildHeight();
        int maxY = mc.level.getMaxBuildHeight();

        int enqueued = 0, alreadyCached = 0, notLoaded = 0;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos pos = new ChunkPos(playerChunkX + dx, playerChunkZ + dz);
                if (SCANNER.isCached(pos)) {
                    alreadyCached++;
                } else if (!mc.level.hasChunk(pos.x, pos.z)) {
                    notLoaded++;
                } else {
                    net.minecraft.world.level.chunk.LevelChunk chunk = mc.level.getChunk(pos.x, pos.z);
                    SCANNER.enqueue(new MinimapChunkScanner.ScanRequest(pos, chunk, minY, maxY));
                    enqueued++;
                }
            }
        }

        // Log every 5 seconds
        debugLogCooldown--;
        if (debugLogCooldown <= 0) {
            debugLogCooldown = 100;
            BedGunWars.LOGGER.info("[Minimap] feed: enqueued={}, cached={}, notLoaded={} | cacheSize={} | pendingComposite={}",
                    enqueued, alreadyCached, notLoaded,
                    SCANNER.colorCache.size(),
                    TEXTURE.getPendingCount());
        }
    }

    
    // Other player dots


    private void drawOtherDots(GuiGraphics gui, Minecraft mc, GameSession session,
                               int mapX, int mapY, int mapSize, int halfBlocks,
                               double playerX, double playerZ, int vpX, int vpZ, float scale) {
        UUID localUuid = mc.player.getUUID();

        for (AbstractClientPlayer other : mc.level.players()) {
            UUID uuid = other.getUUID();
            if (uuid.equals(localUuid)) continue;

            boolean isTeammate = isTeammate(session, localUuid, uuid);
            if (!isTeammate && !showAllEnemyDots && !visibleEnemyDots.contains(uuid)) continue;

            int otherTexX = (int) other.getX() - TEXTURE.getOriginX();
            int otherTexZ = (int) other.getZ() - TEXTURE.getOriginZ();
            int dotX = mapX + Math.round((otherTexX - vpX) * scale);
            int dotZ = mapY + Math.round((otherTexZ - vpZ) * scale);

            if (dotX < mapX || dotX > mapX + mapSize || dotZ < mapY || dotZ > mapY + mapSize) continue;

            int color = isTeammate ? teammateColor(session, uuid) : 0xFFFF0000;
            gui.fill(dotX - 2, dotZ - 2, dotX + 2, dotZ + 2, color);
        }
    }

    private boolean isTeammate(GameSession session, UUID local, UUID other) {
        if (session.getMode() != GameModeType.TEAMS) return false;
        String myTeam = session.getPlayerTeam(local);
        String theirTeam = session.getPlayerTeam(other);
        return myTeam != null && myTeam.equals(theirTeam);
    }

    private int teammateColor(GameSession session, UUID uuid) {
        String team = session.getPlayerTeam(uuid);
        if (team == null) return 0xFFFFFFFF;
        return switch (team) {
            case "Team 1" -> 0xFFFF5555;
            case "Team 2" -> 0xFF5555FF;
            case "Team 3" -> 0xFF55FF55;
            case "Team 4" -> 0xFFFFFF55;
            case "Team 5" -> 0xFFFF55FF;
            case "Team 6" -> 0xFFFFAA00;
            default       -> 0xFFFFFFFF;
        };
    }

    
    // Scale bar
    

    private void drawScaleBar(GuiGraphics gui, Minecraft mc,
                              int mapX, int mapY, int mapSize, int halfBlocks) {
        float pixelsPerBlock = (float) mapSize / (halfBlocks * 2);
        int barWidthPx = Math.max(4, (int) (10 * pixelsPerBlock));
        int barX = mapX + 4;
        int barY = mapY + mapSize - 6;
        gui.fill(barX, barY, barX + barWidthPx, barY + 2, 0xFFFFFFFF);
        gui.drawString(mc.font, "10b", barX, barY - 9, 0xFFCCCCCC, false);
    }
}