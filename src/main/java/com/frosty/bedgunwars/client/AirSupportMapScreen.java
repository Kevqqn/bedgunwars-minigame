package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.minimap.MinimapRenderer;
import com.frosty.bedgunwars.minimap.MinimapTexture;
import com.frosty.bedgunwars.network.AirSupportPointsPacket;
import com.frosty.bedgunwars.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

// Fullscreen air support point selection, Uses Screen so Minecraft handles cursor visibility and ESC correctly.

@OnlyIn(Dist.CLIENT)
public class AirSupportMapScreen extends Screen {

    private static final int MAX = 3;
    private final List<double[]> points = new ArrayList<>();

    public AirSupportMapScreen() {
        super(Component.literal("Air Support"));
    }

    private boolean confirmed = false;

    @Override
    public boolean isPauseScreen() { return false; } // world keeps running

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new AirSupportMapScreen());
    }

    public static void forceClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AirSupportMapScreen) mc.setScreen(null);
    }

    @Override
    public void tick() {
        // Freeze player movement every tick while open
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.input = new FrozenInput();
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float partial) {
        MinimapTexture tex = MinimapRenderer.TEXTURE;
        if (!tex.isInitialized()) return;

        int W = width, H = height;
        int mapSize = Math.min(W, H) - 60;
        int mapX = (W - mapSize) / 2, mapY = (H - mapSize) / 2;
        int texSz = tex.getTexSize();

        // Dim
        gui.fill(0, 0, W, H, 0xAA000000);
        // Map border
        gui.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1, 0xFF000000);
        // Map texture
        gui.blit(tex.getTextureLocation(), mapX, mapY, mapSize, mapSize, 0, 0, texSz, texSz, texSz, texSz);

        // Own player dot
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int dotX = worldToScreenX(mc.player.getX(), mapX, mapSize, tex);
            int dotZ = worldToScreenZ(mc.player.getZ(), mapY, mapSize, tex);
            if (dotX >= mapX && dotX <= mapX + mapSize && dotZ >= mapY && dotZ <= mapY + mapSize) {
                gui.fill(dotX - 3, dotZ - 3, dotX + 3, dotZ + 3, 0xFF00FF00);
                gui.drawCenteredString(font, "§aYou", dotX, dotZ - 12, 0x00FF00);
            }
        }

        // Enemy dots from UAV snapshots (if UAV active)
        for (var entry : com.frosty.bedgunwars.minimap.MinimapRenderer.uavSnapshots.entrySet()) {
            double[] data = entry.getValue();
            int ex = worldToScreenX(data[0], mapX, mapSize, tex);
            int ez = worldToScreenZ(data[1], mapY, mapSize, tex);
            if (ex >= mapX && ex <= mapX + mapSize && ez >= mapY && ez <= mapY + mapSize) {
                gui.fill(ex - 3, ez - 3, ex + 3, ez + 3, 0xFFFF4444);
            }
        }

        // Selected points
        for (int i = 0; i < points.size(); i++) {
            int px = worldToScreenX(points.get(i)[0], mapX, mapSize, tex);
            int pz = worldToScreenZ(points.get(i)[1], mapY, mapSize, tex);
            gui.fill(px - 4, pz - 4, px + 4, pz + 4, 0xFFFF4444);
            gui.drawCenteredString(font, "§c" + (i + 1), px, pz - 12, 0xFF4444);
        }

        // Instructions
        gui.drawCenteredString(font, "§e§lAir Support — Select " + MAX + " Target Points",
                W / 2, mapY - 18, 0xFFFFFF);
        gui.drawCenteredString(font,
                "§7" + points.size() + "/" + MAX + "  •  Left Click: mark  •  Right Click: undo  •  ESC: cancel",
                W / 2, mapY + mapSize + 6, 0xAAAAAA);
        if (points.size() == MAX)
            gui.drawCenteredString(font, "§aLeft Click again to confirm", W / 2, mapY + mapSize + 16, 0x55FF55);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        MinimapTexture tex = MinimapRenderer.TEXTURE;
        if (!tex.isInitialized()) return true;

        int W = width, H = height;
        int mapSize = Math.min(W, H) - 60;
        int mapX = (W - mapSize) / 2, mapY = (H - mapSize) / 2;

        if (button == 1) { // right click = undo
            if (!points.isEmpty()) points.remove(points.size() - 1);
            return true;
        }

        if (button == 0) {
            if (mx >= mapX && mx <= mapX + mapSize && my >= mapY && my <= mapY + mapSize) {
                if (points.size() == MAX) {
                    // Confirm
                    confirmed = true;
                    PacketHandler.CHANNEL.sendToServer(new AirSupportPointsPacket(new ArrayList<>(points)));
                    onClose();
                    return true;
                }
                points.add(new double[]{
                        screenToWorldX(mx, mapX, mapSize, tex),
                        screenToWorldZ(my, mapY, mapSize, tex)
                });
            }
        }
        return true;
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.input = new net.minecraft.client.player.KeyboardInput(mc.options);
        if (!confirmed) {
            PacketHandler.CHANNEL.sendToServer(
                    new com.frosty.bedgunwars.network.AirSupportPointsPacket(new java.util.ArrayList<>()));
        }
        super.onClose();
    }

    // Coordinate conversion
    private static int worldToScreenX(double wx, int mapX, int mapSz, MinimapTexture tex) {
        return mapX + (int)(((wx - tex.getOriginX()) / tex.getTexSize()) * mapSz);
    }
    private static int worldToScreenZ(double wz, int mapY, int mapSz, MinimapTexture tex) {
        return mapY + (int)(((wz - tex.getOriginZ()) / tex.getTexSize()) * mapSz);
    }
    private static double screenToWorldX(double sx, int mapX, int mapSz, MinimapTexture tex) {
        return tex.getOriginX() + ((sx - mapX) / mapSz) * tex.getTexSize();
    }
    private static double screenToWorldZ(double sy, int mapY, int mapSz, MinimapTexture tex) {
        return tex.getOriginZ() + ((sy - mapY) / mapSz) * tex.getTexSize();
    }

    // Frozen movement input
    private static class FrozenInput extends net.minecraft.client.player.Input {
        @Override public void tick(boolean sneaking, float swimFading) {
            forwardImpulse = 0; leftImpulse = 0;
            up = false; down = false; left = false; right = false;
            jumping = false; shiftKeyDown = false;
        }
    }
}