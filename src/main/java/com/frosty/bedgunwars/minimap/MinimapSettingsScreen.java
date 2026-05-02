package com.frosty.bedgunwars.minimap;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinimapSettingsScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 250;
    private static final int SLIDER_W = 220;
    private static final int SLIDER_H = 10;

    private int windowRadius;
    private double sizeMultiplier;
    private int corner;

    private boolean draggingRadius = false;
    private boolean draggingSize = false;

    private int sliderX, radiusSliderY, sizeSliderY;

    private static final int RADIUS_MIN = 30;
    private static final int RADIUS_MAX = 150;
    private static final double SIZE_MIN = 0.5;
    private static final double SIZE_MAX = 2.0;

    public MinimapSettingsScreen() {
        super(Component.literal("Minimap Settings"));
        this.windowRadius = MinimapConfig.WINDOW_RADIUS.get();
        this.sizeMultiplier = MinimapConfig.SIZE_MULTIPLIER.get();
        this.corner = MinimapConfig.CORNER.get();
    }

    @Override
    protected void init() {
        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;
        sliderX = panelX + (PANEL_W - SLIDER_W) / 2;
        radiusSliderY = panelY + 60;
        sizeSliderY = panelY + 110;

        String[] cornerLabels = {"Top-Right", "Top-Left", "Bottom-Right", "Bottom-Left"};
        int btnW = 120;
        int btnAreaX = panelX + (PANEL_W - 2 * btnW - 8) / 2;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            int bx = btnAreaX + (i % 2) * (btnW + 8);
            int by = panelY + 150 + (i / 2) * 26;
            addRenderableWidget(Button.builder(Component.literal(cornerLabels[i]), btn -> {
                corner = idx;
            }).bounds(bx, by, btnW, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Save & Close"), btn -> {
            MinimapConfig.WINDOW_RADIUS.set(windowRadius);
            MinimapConfig.SIZE_MULTIPLIER.set(sizeMultiplier);
            MinimapConfig.CORNER.set(corner);
            MinimapConfig.SPEC.save();
            onClose();
        }).bounds(panelX + (PANEL_W - 120) / 2, panelY + PANEL_H - 30, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float partial) {
        renderBackground(gui);

        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        gui.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xCC111111);
        gui.renderOutline(panelX, panelY, PANEL_W, PANEL_H, 0xFF444444);
        gui.drawCenteredString(font, "§eMinimap Settings", width / 2, panelY + 12, 0xFFFFFF);

        gui.drawString(font, "§7View Radius: §f" + windowRadius + " blocks",
                sliderX, radiusSliderY - 14, 0xFFFFFF);
        drawSlider(gui, sliderX, radiusSliderY, SLIDER_W, SLIDER_H,
                radiusToFraction(), mx, my, draggingRadius);

        gui.drawString(font, "§7Map Size: §f" + String.format("%.1f", sizeMultiplier) + "x",
                sliderX, sizeSliderY - 14, 0xFFFFFF);
        drawSlider(gui, sliderX, sizeSliderY, SLIDER_W, SLIDER_H,
                sizeToFraction(), mx, my, draggingSize);

        gui.drawString(font, "§7Corner Position:",
                panelX + (PANEL_W - SLIDER_W) / 2, panelY + 138, 0xFFFFFF);

        int btnW = 120;
        int btnAreaX = panelX + (PANEL_W - 2 * btnW - 8) / 2;
        for (int i = 0; i < 4; i++) {
            if (i == corner) {
                int bx = btnAreaX + (i % 2) * (btnW + 8);
                int by = panelY + 150 + (i / 2) * 26;
                gui.renderOutline(bx - 1, by - 1, btnW + 2, 22, 0xFFFFAA00);
            }
        }

        super.render(gui, mx, my, partial);
    }

    private void drawSlider(GuiGraphics gui, int x, int y, int w, int h,
                            double fraction, int mx, int my, boolean dragging) {
        gui.fill(x, y + h / 2 - 1, x + w, y + h / 2 + 1, 0xFF666666);
        int fillW = (int) (fraction * w);
        gui.fill(x, y + h / 2 - 1, x + fillW, y + h / 2 + 1, 0xFFFFAA00);
        int thumbX = x + fillW;
        boolean hovered = dragging || (mx >= thumbX - 4 && mx <= thumbX + 4
                && my >= y - 2 && my <= y + h + 2);
        gui.fill(thumbX - 3, y - 1, thumbX + 3, y + h + 1, hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (isOverRadiusSlider(mx, my)) { draggingRadius = true; applyRadius(mx); return true; }
            if (isOverSizeSlider(mx, my))   { draggingSize = true;   applySize(mx);   return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingRadius) { applyRadius(mx); return true; }
        if (draggingSize)   { applySize(mx);   return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingRadius = false;
        draggingSize = false;
        return super.mouseReleased(mx, my, button);
    }

    private boolean isOverRadiusSlider(double mx, double my) {
        return mx >= sliderX - 4 && mx <= sliderX + SLIDER_W + 4
                && my >= radiusSliderY - 4 && my <= radiusSliderY + SLIDER_H + 4;
    }

    private boolean isOverSizeSlider(double mx, double my) {
        return mx >= sliderX - 4 && mx <= sliderX + SLIDER_W + 4
                && my >= sizeSliderY - 4 && my <= sizeSliderY + SLIDER_H + 4;
    }

    private void applyRadius(double mx) {
        double fraction = Math.max(0, Math.min(1, (mx - sliderX) / SLIDER_W));
        windowRadius = (int) Math.round(RADIUS_MIN + fraction * (RADIUS_MAX - RADIUS_MIN));
    }

    private void applySize(double mx) {
        double fraction = Math.max(0, Math.min(1, (mx - sliderX) / SLIDER_W));
        sizeMultiplier = Math.round((SIZE_MIN + fraction * (SIZE_MAX - SIZE_MIN)) * 10.0) / 10.0;
    }

    private double radiusToFraction() {
        return (double) (windowRadius - RADIUS_MIN) / (RADIUS_MAX - RADIUS_MIN);
    }

    private double sizeToFraction() {
        return (sizeMultiplier - SIZE_MIN) / (SIZE_MAX - SIZE_MIN);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}