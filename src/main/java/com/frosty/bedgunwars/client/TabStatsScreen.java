package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.network.TabStatsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class TabStatsScreen {

    private static TabStatsPacket cachedData = null;
    public static boolean visible = false;

    public static void updateCache(TabStatsPacket pkt) {
        cachedData = pkt;
    }

    public static boolean hasData() { return cachedData != null; }

    @SubscribeEvent
    public void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.VIGNETTE.type()) return;
        if (!visible || cachedData == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        render(event.getGuiGraphics(), mc);
    }

    private void render(GuiGraphics gui, Minecraft mc) {
        boolean isTeams = "TEAMS".equals(cachedData.gameMode);

        int colName  = 140;
        int colK     = 30;
        int colD     = 30;
        int colMoney = 60;
        int colBed   = 30;
        int totalW   = colName + colK + colD + colMoney + colBed + 20;
        int rowH     = 12;
        int headerH  = 22;
        int padding  = 8;

        int rowCount = cachedData.players.size();
        int teamHeaderCount = 0;
        if (isTeams) {
            Set<String> teams = new LinkedHashSet<>();
            for (TabStatsPacket.PlayerEntry e : cachedData.players)
                if (e.team != null) teams.add(e.team);
            teamHeaderCount = teams.size();
        }

        int totalH = headerH + rowH + 4 + (rowH * (rowCount + teamHeaderCount)) + padding * 2;

        int panelX = (mc.getWindow().getGuiScaledWidth() - totalW) / 2 - 4;
        int panelY = (mc.getWindow().getGuiScaledHeight() - totalH) / 2;

        // Background
        gui.fill(panelX - 4, panelY - 4, panelX + totalW + 4, panelY + totalH + 4, 0xCC000000);
        gui.renderOutline(panelX - 4, panelY - 4, totalW + 8, totalH + 8, 0xFF555555);

        // Title
        String title = "BedGunWars — " + (isTeams ? "TEAMS" : "SOLO");
        gui.drawCenteredString(mc.font, "§e§l" + title, panelX + totalW / 2, panelY + 4, 0xFFFFFF);
        long alive = cachedData.players.stream().filter(e -> e.alive).count();
        gui.drawCenteredString(mc.font, "§7" + alive + " / " + cachedData.players.size() + " alive",
                panelX + totalW / 2, panelY + 13, 0xAAAAAA);

        int y = panelY + headerH + padding;

        // Column headers
        int cx = panelX + 4;
        gui.drawString(mc.font, "§7Player",  cx, y, 0xAAAAAA); cx += colName;
        gui.drawString(mc.font, "§7K",       cx, y, 0xAAAAAA); cx += colK;
        gui.drawString(mc.font, "§7D",       cx, y, 0xAAAAAA); cx += colD;
        gui.drawString(mc.font, "§7Money",   cx, y, 0xAAAAAA); cx += colMoney;
        gui.drawString(mc.font, "§7Bed",     cx, y, 0xAAAAAA);
        y += rowH + 2;
        gui.fill(panelX, y - 1, panelX + totalW, y, 0xFF444444);

        if (isTeams) {
            Map<String, List<TabStatsPacket.PlayerEntry>> byTeam = new LinkedHashMap<>();
            for (TabStatsPacket.PlayerEntry e : cachedData.players) {
                String key = e.team != null ? e.team : "Unknown";
                byTeam.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            }
            for (var teamEntry : byTeam.entrySet()) {
                String teamName = teamEntry.getKey();
                int teamColor = teamEntry.getValue().isEmpty() ? 0xFFFFFFFF
                        : teamEntry.getValue().get(0).teamColor;
                gui.fill(panelX, y, panelX + totalW, y + rowH, 0x33000000 | (teamColor & 0x00FFFFFF));
                gui.drawString(mc.font, "§l" + teamName, panelX + 4, y + 2, teamColor);
                y += rowH;
                for (TabStatsPacket.PlayerEntry e : teamEntry.getValue()) {
                    y = drawRow(gui, mc, e, panelX, y, rowH, colName, colK, colD, colMoney, colBed, totalW);
                }
                gui.fill(panelX, y, panelX + totalW, y + 1, 0xFF333333);
                y += 2;
            }
        } else {
            List<TabStatsPacket.PlayerEntry> sorted = new ArrayList<>(cachedData.players);
            sorted.sort((a, b) -> b.kills - a.kills);
            for (TabStatsPacket.PlayerEntry e : sorted) {
                y = drawRow(gui, mc, e, panelX, y, rowH, colName, colK, colD, colMoney, colBed, totalW);
            }
        }
    }

    private int drawRow(GuiGraphics gui, Minecraft mc, TabStatsPacket.PlayerEntry e,
                        int panelX, int y, int rowH,
                        int colName, int colK, int colD, int colMoney, int colBed, int totalW) {
        if (!e.alive) gui.fill(panelX, y, panelX + totalW, y + rowH, 0x22000000);

        int nameColor = e.alive ? 0xFFFFFF : 0x888888;
        String dot = e.alive ? "§a● " : "§7○ ";
        int cx = panelX + 4;

        gui.drawString(mc.font, dot + (e.alive ? "" : "§7") + e.name, cx, y + 2, nameColor);
        cx += colName;
        gui.drawString(mc.font, "§f" + e.kills,  cx, y + 2, 0xFFFFFF); cx += colK;
        gui.drawString(mc.font, "§f" + e.deaths, cx, y + 2, 0xFFFFFF); cx += colD;
        gui.drawString(mc.font, "§a$" + e.money, cx, y + 2, 0x55FF55); cx += colMoney;

        String bed = switch (e.bedStatus) {
            case TabStatsPacket.BED_INTACT -> "§a✓";
            case TabStatsPacket.BED_BROKEN -> "§c✗";
            default                         -> "§7—";
        };
        gui.drawString(mc.font, bed, cx, y + 2, 0xFFFFFF);

        return y + rowH;
    }
}