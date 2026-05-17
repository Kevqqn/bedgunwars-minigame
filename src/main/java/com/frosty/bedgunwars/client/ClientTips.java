package com.frosty.bedgunwars.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ClientTips {

    private static final long DISPLAY_MS = 6000;
    private static final int FADE_MS = 800;

    private static Map<String, String> tips = null;
    private static String activeText = null;
    private static long showUntil = 0;

    public static void load() {
        try (InputStream is = ClientTips.class.getResourceAsStream("/assets/bedgunwars/tips.json")) {
            if (is == null) return;
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            tips = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            System.err.println("[ClientTips] Failed to load tips.json: " + e.getMessage());
        }
    }

    public static String get(String tipId) {
        if (tips == null) return "";
        return tips.getOrDefault(tipId, "");
    }

    public static void show(String tipId) {
        String text = get(tipId);
        if (text.isEmpty()) return;
        activeText = "§e[Tip] §f" + resolveKeybinds(text);
        showUntil = System.currentTimeMillis() + DISPLAY_MS;
    }

    public static void showResolved(String resolvedText) {
        if (resolvedText.isEmpty()) return;
        activeText = "§e[Tip] §f" + resolvedText;
        showUntil = System.currentTimeMillis() + DISPLAY_MS;
    }

    public static boolean isActive() {
        return activeText != null && System.currentTimeMillis() < showUntil;
    }

    // Called from RenderGuiOverlayEvent (outside screens)
    public static void renderHud(GuiGraphics g) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        int W = mc.getWindow().getGuiScaledWidth();
        int H = mc.getWindow().getGuiScaledHeight();
        renderText(g, mc, W, H - 68);
    }

    // Called from GunSelectionScreen.render()
    public static void renderInScreen(GuiGraphics g, Screen screen) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        renderText(g, mc, screen.width, screen.height - 68);
    }

    private static void renderText(GuiGraphics g, Minecraft mc, int screenW, int y) {
        long remaining = showUntil - System.currentTimeMillis();
        float alpha = remaining < FADE_MS ? (float) remaining / FADE_MS : 1f;
        int a = (int) (alpha * 0xFF);

        String plain = activeText.replaceAll("§.", "");
        int textW = mc.font.width(plain);
        int x = (screenW - textW) / 2;

        int bgAlpha = (int) (alpha * 0x88);
        g.fill(x - 4, y - 2, x + textW + 4, y + 10, (bgAlpha << 24));
        g.drawString(mc.font,
                net.minecraft.network.chat.Component.literal(activeText),
                x, y, (a << 24) | 0xFFFFFF, false);
    }

    public static String resolveKeybindsPublic(String text) { return resolveKeybinds(text); }

    private static String resolveKeybinds(String text) {
        text = text.replace("<key.bedgunwars.gun_menu>",
                KeyBindings.GUN_MENU_KEY.getKey().getDisplayName().getString());
        text = text.replace("<key.bedgunwars.killstreak>",
                KeyBindings.KILLSTREAK_KEY.getKey().getDisplayName().getString());
        return text;
    }
}