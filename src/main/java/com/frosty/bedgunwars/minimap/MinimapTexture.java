package com.frosty.bedgunwars.minimap;

import com.frosty.bedgunwars.BedGunWars;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class MinimapTexture {

    private static final int BUFFER = 64;
    private static final int MAX_TEX_SIZE = 1024;

    private int originX, originZ, texSize;
    private DynamicTexture texture;
    private ResourceLocation textureLocation;
    private boolean initialized = false;

    // Scanner thread adds here; render thread drains into local list
    private final Set<ChunkPos> pendingComposite = ConcurrentHashMap.newKeySet();

    public void init(int beaconX, int beaconZ, int radius) {
        BedGunWars.LOGGER.info("[Minimap] init called: beaconX={} beaconZ={} radius={}", beaconX, beaconZ, radius);
        // Dispose GL resources but keep pendingComposite intact
        initialized = false;
        if (texture != null) {
            if (textureLocation != null) {
                try { Minecraft.getInstance().getTextureManager().release(textureLocation); }
                catch (Exception ignored) {}
                textureLocation = null;
            }
            texture.close();
            texture = null;
        }

        int diameter = (radius + BUFFER) * 2;
        texSize = Math.min(diameter, MAX_TEX_SIZE);
        originX = beaconX - texSize / 2;
        originZ = beaconZ - texSize / 2;

        NativeImage img = new NativeImage(texSize, texSize, false);
        for (int y = 0; y < texSize; y++)
            for (int x = 0; x < texSize; x++)
                img.setPixelRGBA(x, y, 0xFF111111);

        texture = new DynamicTexture(img);
        textureLocation = Minecraft.getInstance()
                .getTextureManager().register("bgw_minimap", texture);
        initialized = true;

        BedGunWars.LOGGER.info("[Minimap] texture initialized: texSize={}, origin=({},{})",
                texSize, originX, originZ);
    }

    public void dispose() {
        initialized = false;
        pendingComposite.clear();
        if (texture != null) {
            if (textureLocation != null) {
                try { Minecraft.getInstance().getTextureManager().release(textureLocation); }
                catch (Exception ignored) {}
                textureLocation = null;
            }
            texture.close();
            texture = null;
        }
    }

    /** Called from scanner thread — just marks, no texture writes */
    public void markDirty(ChunkPos pos) {
        pendingComposite.add(pos);
    }

    /** Called from render thread each frame */
    public void update(MinimapChunkScanner scanner) {
        BedGunWars.LOGGER.info("[Minimap] update called: pending={}, initialized={}",
                pendingComposite.size(), initialized);
        if (!initialized || texture == null) return;
        NativeImage img = texture.getPixels();
        if (img == null) return;

        // Drain into local list atomically — no concurrent modification
        List<ChunkPos> toProcess = new ArrayList<>(pendingComposite);
        pendingComposite.removeAll(toProcess);
        if (!toProcess.isEmpty()) {
            BedGunWars.LOGGER.info("[Minimap] update: processing {} chunks", toProcess.size());
        }

        boolean changed = false;
        for (ChunkPos pos : toProcess) {
            int[] colors = scanner.colorCache.get(pos);
            if (colors == null) continue;
            blitChunk(img, pos, colors);
            changed = true;
        }
        if (changed) {
            BedGunWars.LOGGER.info("[Minimap] upload: blitted {} chunks", toProcess.size());
            texture.upload();
        }

        if (changed) texture.upload();
    }

    private void blitChunk(NativeImage img, ChunkPos pos, int[] colors) {
        int chunkWorldX = pos.getMinBlockX();
        int chunkWorldZ = pos.getMinBlockZ();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int px = (chunkWorldX + lx) - originX;
                int pz = (chunkWorldZ + lz) - originZ;
                if (px < 0 || px >= texSize || pz < 0 || pz >= texSize) continue;
                img.setPixelRGBA(px, pz, toAbgr(colors[lz * 16 + lx]));
            }
        }
    }

    public int viewportOriginX(double playerWorldX, int halfBlocks) {
        int playerTexX = (int) playerWorldX - originX;
        return Math.max(0, Math.min(playerTexX - halfBlocks, texSize - halfBlocks * 2));
    }

    public int viewportOriginZ(double playerWorldZ, int halfBlocks) {
        int playerTexZ = (int) playerWorldZ - originZ;
        return Math.max(0, Math.min(playerTexZ - halfBlocks, texSize - halfBlocks * 2));
    }

    /** Returns how many pixels the player dot is offset from center due to edge clamping */
    public int dotOffsetX(double playerWorldX, int halfBlocks, int mapSize) {
        int playerTexX = (int) playerWorldX - originX;
        int unclamped = playerTexX - halfBlocks;
        int clamped = Math.max(0, Math.min(unclamped, texSize - halfBlocks * 2));
        float scale = (float) mapSize / (halfBlocks * 2);
        return Math.round((playerTexX - halfBlocks * 2 - (clamped - unclamped + halfBlocks)) * 0);
    }

    public ResourceLocation getTextureLocation() { return textureLocation; }
    public boolean isInitialized() { return initialized; }
    public int getTexSize() { return texSize; }
    public int getOriginX() { return originX; }
    public int getOriginZ() { return originZ; }

    public static int toAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >>  8) & 0xFF;
        int b =  argb        & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
    public int getPendingCount() { return pendingComposite.size(); }
}