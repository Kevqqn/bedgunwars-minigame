package com.frosty.bedgunwars.minimap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Background thread scanner.
 * IMPORTANT: chunks are passed in pre-fetched from the render thread.
 * The scanner never touches ClientLevel or any Minecraft state directly —
 * it only processes LevelChunk objects handed to it via ScanRequest.
 */
public class MinimapChunkScanner {

    public record ScanRequest(ChunkPos pos, LevelChunk chunk, int minY, int maxY) {}

    // Output: chunkPos -> 16*16 ARGB array
    public final ConcurrentHashMap<ChunkPos, int[]> colorCache = new ConcurrentHashMap<>();

    private final LinkedBlockingQueue<ScanRequest> queue = new LinkedBlockingQueue<>();
    private final Set<ChunkPos> queued = ConcurrentHashMap.newKeySet();

    private volatile boolean running = false;
    private Thread thread;

    // Set by renderer before enqueuing
    public volatile int centerChunkX = 0;
    public volatile int centerChunkZ = 0;
    public volatile int windowChunkRadius = 4;

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "BGW-MinimapScanner");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        queue.clear();
        queued.clear();
        colorCache.clear();
        if (thread != null) { thread.interrupt(); thread = null; }
    }

    /** Called from render thread — passes pre-fetched chunk data, never blocks */
    public void enqueue(ScanRequest request) {
        if (queued.add(request.pos())) {
            queue.offer(request);
        }
    }

    public boolean isCached(ChunkPos pos) {
        return colorCache.containsKey(pos);
    }

    private void loop() {
        while (running) {
            try {
                ScanRequest req = queue.take();
                queued.remove(req.pos());
                scanChunk(req);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void scanChunk(ScanRequest req) {
        ChunkPos pos = req.pos();
        LevelChunk chunk = req.chunk();
        int minY = req.minY();
        int maxY = req.maxY();

        int[] colors = new int[16 * 16];

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = pos.getMinBlockX() + lx;
                int worldZ = pos.getMinBlockZ() + lz;

                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos(worldX, maxY - 1, worldZ);
                MapColor surfaceColor = null;
                int surfaceY = minY;

                while (mpos.getY() > minY) {
                    BlockState bs = chunk.getBlockState(mpos);
                    MapColor mc2 = bs.getMapColor(chunk, mpos);
                    if (mc2 != MapColor.NONE) {
                        surfaceColor = mc2;
                        surfaceY = mpos.getY();
                        break;
                    }
                    mpos.move(0, -1, 0);
                }

                if (surfaceColor == null) {
                    colors[lz * 16 + lx] = 0xFF111111;
                    continue;
                }

                int shade = 1;
                if (lz < 15) {
                    BlockPos.MutableBlockPos south = new BlockPos.MutableBlockPos(worldX, maxY - 1, worldZ + 1);
                    int southY = minY;
                    while (south.getY() > minY) {
                        BlockState sbs = chunk.getBlockState(south);
                        if (sbs.getMapColor(chunk, south) != MapColor.NONE) {
                            southY = south.getY();
                            break;
                        }
                        south.move(0, -1, 0);
                    }
                    if (surfaceY > southY) shade = 2;
                    else if (surfaceY < southY) shade = 0;
                }

                colors[lz * 16 + lx] = applyShade(mapColorToArgb(surfaceColor), shade);
            }
        }

        colorCache.put(pos, colors);
        MinimapRenderer.TEXTURE.markDirty(pos);
    }

    private static int mapColorToArgb(MapColor color) {
        return 0xFF000000 | color.col;
    }

    private static int applyShade(int argb, int shade) {
        float f = switch (shade) {
            case 0 -> 0.71f;
            case 2 -> 1.16f;
            default -> 1.0f;
        };
        int r = clamp((int) (((argb >> 16) & 0xFF) * f));
        int g = clamp((int) (((argb >>  8) & 0xFF) * f));
        int b = clamp((int) ((argb & 0xFF) * f));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}