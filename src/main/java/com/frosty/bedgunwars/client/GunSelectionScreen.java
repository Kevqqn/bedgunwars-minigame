package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.game.GunHelper;
import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.network.SelectGunPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class GunSelectionScreen extends Screen {

    private final List<ResourceLocation> allGuns;
    private final List<ResourceLocation> selected;
    private static final int MAX_SLOTS = 3;

    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 10;
    private static final int LIST_WIDTH = 220;
    private static final int LIST_X_OFFSET = -120;

    public GunSelectionScreen(List<ResourceLocation> allGuns, List<ResourceLocation> currentSelections) {
        super(Component.literal("Select Weapons"));
        this.allGuns = allGuns;
        this.selected = new ArrayList<>(currentSelections);
    }

    public static void open(List<ResourceLocation> allGuns, List<ResourceLocation> currentSelections) {
        Minecraft.getInstance().setScreen(new GunSelectionScreen(allGuns, currentSelections));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int listX = this.width / 2 + LIST_X_OFFSET;
        int listY = 40;
        int listHeight = VISIBLE_ROWS * ROW_HEIGHT;

        graphics.drawCenteredString(font, "§6§lWeapon Selection", this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, "§7Choose up to " + MAX_SLOTS + " weapons  •  Scroll to browse  •  ESC to close", this.width / 2, 24, 0xAAAAAA);

        graphics.fill(listX - 2, listY - 2, listX + LIST_WIDTH + 2, listY + listHeight + 2, 0xFF222222);

        int maxScroll = Math.max(0, allGuns.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= allGuns.size()) break;

            ResourceLocation gunId = allGuns.get(idx);
            int rowY = listY + i * ROW_HEIGHT;
            boolean isSelected = selected.contains(gunId);
            boolean hovered = mouseX >= listX && mouseX <= listX + LIST_WIDTH
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;

            int bg = isSelected ? 0xCC1A5C1A : (hovered ? 0xCC2A2A2A : 0xCC111111);
            graphics.fill(listX, rowY, listX + LIST_WIDTH, rowY + ROW_HEIGHT, bg);

            if (isSelected) {
                graphics.renderOutline(listX, rowY, LIST_WIDTH, ROW_HEIGHT, 0xFF44FF44);
            }

            ItemStack stack;
            try {
                stack = GunHelper.buildGun(gunId);
                if (stack.isEmpty()) stack = new ItemStack(Items.BOW);
            } catch (Exception e) {
                stack = new ItemStack(Items.BOW);
            }
            graphics.renderItem(stack, listX + 3, rowY + 4);

            String name = GunHelper.getGunDisplayName(gunId);
            String category = getCategoryLabel(gunId);
            graphics.drawString(font, "§f" + name, listX + 22, rowY + 4, 0xFFFFFF);
            graphics.drawString(font, "§7" + category, listX + 22, rowY + 13, 0x888888);

            if (isSelected) {
                int slot = selected.indexOf(gunId) + 1;
                graphics.drawString(font, "§a[Slot " + slot + "]", listX + LIST_WIDTH - 46, rowY + 8, 0x44FF44);
            }
        }

        // Scrollbar
        if (allGuns.size() > VISIBLE_ROWS) {
            int sbX = listX + LIST_WIDTH + 4;
            int sbH = listHeight;
            graphics.fill(sbX, listY, sbX + 4, listY + sbH, 0xFF333333);
            int thumbH = Math.max(10, sbH * VISIBLE_ROWS / allGuns.size());
            int thumbY = listY + (sbH - thumbH) * scrollOffset / Math.max(1, allGuns.size() - VISIBLE_ROWS);
            graphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF888888);
        }

        // Selected loadout panel on the right
        int panelX = this.width / 2 + 120;
        int panelY = 40;
        graphics.drawString(font, "§eYour Loadout", panelX, panelY, 0xFFAA00);
        for (int i = 0; i < MAX_SLOTS; i++) {
            int slotY = panelY + 16 + i * 28;
            graphics.fill(panelX, slotY, panelX + 120, slotY + 24, 0xCC111111);
            graphics.renderOutline(panelX, slotY, 120, 24, 0xFF444444);
            if (i < selected.size()) {
                ItemStack s;
                try { s = GunHelper.buildGun(selected.get(i)); } catch (Exception e) { s = new ItemStack(Items.BOW); }
                graphics.renderItem(s, panelX + 3, slotY + 4);
                graphics.drawString(font, "§f" + GunHelper.getGunDisplayName(selected.get(i)), panelX + 22, slotY + 8, 0xFFFFFF);
            } else {
                graphics.drawCenteredString(font, "§7- Empty Slot -", panelX + 60, slotY + 8, 0x555555);
            }
        }

        graphics.drawString(font, "§7Click to add/remove", panelX, panelY + 16 + MAX_SLOTS * 28 + 4, 0x666666);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = this.width / 2 + LIST_X_OFFSET;
        int listY = 40;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= allGuns.size()) break;
            int rowY = listY + i * ROW_HEIGHT;
            if (mouseX >= listX && mouseX <= listX + LIST_WIDTH && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                ResourceLocation gunId = allGuns.get(idx);
                if (selected.contains(gunId)) {
                    selected.remove(gunId);
                } else if (selected.size() < MAX_SLOTS) {
                    selected.add(gunId);
                }
                PacketHandler.CHANNEL.sendToServer(new SelectGunPacket(new ArrayList<>(selected)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) delta, Math.max(0, allGuns.size() - VISIBLE_ROWS)));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private String getCategoryLabel(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("m700") || path.contains("awp") || path.contains("sniper") || path.contains("kar") || path.contains("sv98")) return "Sniper";
        if (path.contains("mp5") || path.contains("mp7") || path.contains("p90") || path.contains("smg") || path.contains("ump")) return "SMG";
        if (path.contains("shotgun") || path.contains("spas") || path.contains("m1014")) return "Shotgun";
        if (path.contains("pistol") || path.contains("desert") || path.contains("glock") || path.contains("deagle")) return "Pistol";
        return "Rifle";
    }
}