package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.minimap.MinimapConfig;
import com.frosty.bedgunwars.minimap.MinimapRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class DeathFeedRenderer {

    // How long an entry lives in ticks
    private static final int ENTRY_LIFETIME = 80; // 4 seconds
    private static final int FADE_TICKS     = 20;
    private static final int ENTRY_HEIGHT   = 14;
    private static final int ICON_W         = 36;
    private static final int ICON_H         = 12;
    // private static final int FEED_WIDTH     = 160;
    private static final int MAX_ENTRIES    = 5;


    private static final List<DeathFeedEntry> entries = new ArrayList<>();

    public static void addEntry(String killerName, String victimName,
                                String gunNamespace, String gunPath) {
        if (entries.size() >= MAX_ENTRIES) entries.remove(entries.size() - 1);
        ResourceLocation hudTex = ResourceLocation.fromNamespaceAndPath(
                gunNamespace, "textures/gun/hud/" + gunPath + ".png");
        entries.add(0, new DeathFeedEntry(killerName, victimName, hudTex,
                getCurrentTick()));
    }

    // Render

    @SubscribeEvent
    public void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.type()) return;
        if (!MinimapRenderer.isStarted()) return;
        if (entries.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        int screenW  = mc.getWindow().getGuiScaledWidth();
        int screenH  = mc.getWindow().getGuiScaledHeight();

        // Expire old entries
        long now = getCurrentTick();
        entries.removeIf(e -> now - e.createdTick() >= ENTRY_LIFETIME);
        if (entries.isEmpty()) return;

        // Mirror minimap corner logic
        int corner      = MinimapConfig.CORNER.get();
        double sizeMult = MinimapConfig.SIZE_MULTIPLIER.get();
        int margin      = 6;
        int mapSize     = (int) (screenW * 0.18 * sizeMult);
        boolean isTop   = corner == 0 || corner == 1;
        boolean isLeft  = corner == 1 || corner == 3;

        int feedX = isLeft ? margin : screenW - 200 - margin;

        // Y, below minimap if top, above feed stack if bottom
        int feedStartY;
        if (isTop) {
            feedStartY = margin + mapSize + 10;
        } else {
            int totalHeight = entries.size() * ENTRY_HEIGHT;
            int minimapTop  = screenH - mapSize - margin - 20;
            feedStartY      = minimapTop - totalHeight - 6;
        }

        GuiGraphics gfx = event.getGuiGraphics();

        for (int i = 0; i < entries.size(); i++) {
            DeathFeedEntry e   = entries.get(i);
            long age           = now - e.createdTick();
            float alpha        = 1.0f;
            if (age > ENTRY_LIFETIME - FADE_TICKS) {
                alpha = 1.0f - (float)(age - (ENTRY_LIFETIME - FADE_TICKS)) / FADE_TICKS;
            }
            alpha = Math.max(0f, Math.min(1f, alpha));
            int a = (int)(alpha * 255);
            if (a <= 0) continue;

            int entryY = feedStartY + i * ENTRY_HEIGHT;

            String killerStr  = e.killerName();
            String victimStr  = e.victimName();
            int killerW       = mc.font.width(killerStr);
            int victimW       = mc.font.width(victimStr);
            int totalContentW = killerW + 2 + ICON_W + 2 + victimW;

            // bg fill
            // unused int bgAlpha = (int)(a * 0.2f);
            RenderSystem.enableBlend();
            int bgAlpha = (int)(a * 0.35f) << 24;
            gfx.fill(feedX - 4, entryY - 1,
                    feedX + totalContentW + 4, entryY + ENTRY_HEIGHT - 2,
                    bgAlpha);

            int textColor = (a << 24) | 0x00FFFFFF;
            int iconY     = entryY + (ENTRY_HEIGHT - ICON_H) / 2;

            // Layout: KillerName [icon] VictimName
            gfx.drawString(mc.font, killerStr, feedX, entryY + ENTRY_HEIGHT / 2 - 4, textColor, false);
            int iconX = feedX + killerW + 2;
            renderGunIcon(gfx, e.gunTexture(), iconX, iconY, alpha);
            gfx.drawString(mc.font, victimStr, iconX + ICON_W + 2, entryY + ENTRY_HEIGHT / 2 - 4, textColor, false);
        }
    }

    private void renderGunIcon(GuiGraphics gfx, ResourceLocation tex,
                               int x, int y, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        // Mirror horizontally by flipping srcX/srcW: start at 384, width -384
        gfx.blit(tex, x, y, ICON_W, ICON_H, 384, 0, -384, 128, 384, 128);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static long getCurrentTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    // Entry record

    public record DeathFeedEntry(
            String killerName,
            String victimName,
            ResourceLocation gunTexture,
            long createdTick
    ) {}
}