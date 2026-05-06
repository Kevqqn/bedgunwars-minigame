package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.game.KillstreakType;
import com.frosty.bedgunwars.minimap.MinimapRenderer;
import com.frosty.bedgunwars.network.KillstreakActivatePacket;
import com.frosty.bedgunwars.network.KillstreakStatePacket;
import com.frosty.bedgunwars.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class KillstreakHudRenderer {

    // Cached state
    private static int streak, uavStacks, glowStacks, airStacks, juggStacks, uavTicks, glowTicks;

    // Activation overlay
    public static boolean overlayOpen = false;
    public static int selectedIndex = 0;

    public static void updateState(KillstreakStatePacket pkt) {
        streak = pkt.streak; uavStacks = pkt.uavStacks; glowStacks = pkt.glowStacks;
        airStacks = pkt.airStacks; juggStacks = pkt.juggStacks;
        uavTicks = pkt.uavTicks; glowTicks = pkt.glowTicks;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!MinimapRenderer.isStarted()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics gui = event.getGuiGraphics();
        int W = mc.getWindow().getGuiScaledWidth();
        int H = mc.getWindow().getGuiScaledHeight();
        if (overlayOpen) renderOverlay(gui, mc, W, H);
        else renderHud(gui, mc, H);
    }

    // ── Bottom-left HUD ──

    private void renderHud(GuiGraphics gui, Minecraft mc, int H) {
        int W = mc.getWindow().getGuiScaledWidth();
        int slotSz = 24, pad = 4;
        int totalW = 4 * (slotSz + pad) - pad;
        int x0 = W - totalW - 6;
        int y0 = H - slotSz - 58;
        int[] stacks = {uavStacks, glowStacks, airStacks, juggStacks};
        int[] req    = {3, 5, 8, 12};
        int[] timers = {uavTicks, glowTicks, 0, 0};
        KillstreakType[] types = KillstreakType.values();

        for (int i = 0; i < 4; i++) {
            int x = x0 + i * (slotSz + pad), y = y0;
            boolean has = stacks[i] > 0;
            gui.fill(x, y, x + slotSz, y + slotSz, has ? 0xCC222222 : 0x88111111);
            gui.renderOutline(x, y, slotSz, slotSz, has ? 0xFFFFAA00 : 0xFF444444);
            gui.renderItem(icon(types[i]), x + 4, y + 4);
            if (!has) gui.fill(x + 4, y + 4, x + 20, y + 20, 0x88000000);
            gui.drawCenteredString(mc.font, "§7" + req[i], x + slotSz / 2, y + slotSz - 8, 0xAAAAAA);
            if (stacks[i] > 1)
                gui.drawString(mc.font, "§e" + stacks[i], x + slotSz - 8, y + 2, 0xFFFF55);
            if (timers[i] > 0) {
                float frac = Math.min(1f, timers[i] / 300f);
                gui.fill(x, y + slotSz - 2, x + slotSz, y + slotSz, 0xFF333333);
                gui.fill(x, y + slotSz - 2, x + (int)(slotSz * frac), y + slotSz, 0xFF00FF88);
            }
        }
        if (streak > 0)
            gui.drawString(mc.font, "§eKS: §f" + streak, x0, y0 - 10, 0xFFFFFF);
    }

    // ── Centered activation overlay ───────────────────────────────────────────

    private void renderOverlay(GuiGraphics gui, Minecraft mc, int W, int H) {
        KillstreakType[] types = KillstreakType.values();
        int[] stacks = {uavStacks, glowStacks, airStacks, juggStacks};
        int[] timers = {uavTicks, glowTicks, 0, 0};
        int slotW = 80, slotH = 60, gap = 10;
        int totalW = types.length * slotW + (types.length - 1) * gap;
        int x0 = (W - totalW) / 2, y0 = H / 2 - slotH / 2;

        gui.fill(0, 0, W, H, 0x66000000);
        gui.drawCenteredString(mc.font, "§e§lSelect Killstreak", W / 2, y0 - 20, 0xFFFFFF);
        gui.drawCenteredString(mc.font, "§7Scroll • Click to activate • V to close",
                W / 2, y0 - 10, 0x888888);

        for (int i = 0; i < types.length; i++) {
            int x = x0 + i * (slotW + gap), y = y0;
            boolean sel = i == selectedIndex, has = stacks[i] > 0;
            gui.fill(x, y, x + slotW, y + slotH, sel ? 0xCC333300 : has ? 0xCC1A1A1A : 0xCC111111);
            gui.renderOutline(x, y, slotW, slotH, sel ? 0xFFFFAA00 : has ? 0xFF555555 : 0xFF333333);
            gui.renderItem(icon(types[i]), x + slotW / 2 - 8, y + 8);
            gui.drawCenteredString(mc.font, (has ? "§e" : "§8") + types[i].displayName,
                    x + slotW / 2, y + 28, 0xFFFFFF);
            if (has)
                gui.drawCenteredString(mc.font, "§a×" + stacks[i], x + slotW / 2, y + 38, 0x55FF55);
            else
                gui.drawCenteredString(mc.font, "§7" + types[i].killsRequired + " kills",
                        x + slotW / 2, y + 38, 0x888888);
            if (timers[i] > 0)
                gui.drawCenteredString(mc.font, "§a" + (timers[i] / 20) + "s",
                        x + slotW / 2, y + 48, 0x55FF55);
        }
        gui.drawCenteredString(mc.font, "§7" + types[selectedIndex].description,
                W / 2, y0 + slotH + 8, 0xAAAAAA);
    }

    private ItemStack icon(KillstreakType t) {
        return switch (t) {
            case UAV          -> new ItemStack(Items.COMPASS);
            case PRIVATE_GLOW -> new ItemStack(Items.GLOWSTONE_DUST);
            case AIR_SUPPORT  -> new ItemStack(Items.FIREWORK_ROCKET);
            case JUGGERNAUT   -> new ItemStack(Items.NETHERITE_CHESTPLATE);
        };
    }

    // ── Scroll & activate 

    public static void scrollSelection(double delta) {
        int n = KillstreakType.values().length;
        selectedIndex = (int)((selectedIndex - (int)Math.signum(delta) + n) % n);
    }

    public static void activateSelected() {
        KillstreakType type = KillstreakType.values()[selectedIndex];
        PacketHandler.CHANNEL.sendToServer(new KillstreakActivatePacket(type));
        overlayOpen = false;
    }
}