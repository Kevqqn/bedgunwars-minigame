package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.game.GunHelper;
import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.network.SelectGunPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class GunSelectionScreen extends Screen {

    private final List<ResourceLocation> guns;
    private ResourceLocation selected;

    private static final int CARD_WIDTH = 90;
    private static final int CARD_HEIGHT = 110;
    private static final int CARD_GAP = 16;

    public GunSelectionScreen(List<ResourceLocation> guns, ResourceLocation currentSelection) {
        super(Component.literal("Select Your Weapon"));
        this.guns = guns;
        this.selected = currentSelection != null ? currentSelection : (guns.isEmpty() ? null : guns.get(0));
    }

    public static void open(List<ResourceLocation> guns, ResourceLocation currentSelection) {
        Minecraft.getInstance().setScreen(new GunSelectionScreen(guns, currentSelection));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int totalWidth = guns.size() * CARD_WIDTH + (guns.size() - 1) * CARD_GAP;
        int startX = (this.width - totalWidth) / 2;
        int startY = (this.height - CARD_HEIGHT) / 2 - 20;

        graphics.drawCenteredString(font, "§6§lSelect Your Weapon", this.width / 2, startY - 24, 0xFFFFFF);
        graphics.drawCenteredString(font, "§7Press ESC to close • Selection carries into match", this.width / 2, startY - 12, 0xAAAAAA);

        for (int i = 0; i < guns.size(); i++) {
            ResourceLocation gunId = guns.get(i);
            int x = startX + i * (CARD_WIDTH + CARD_GAP);
            int y = startY;

            boolean isSelected = gunId.equals(selected);
            boolean isHovered = mouseX >= x && mouseX <= x + CARD_WIDTH && mouseY >= y && mouseY <= y + CARD_HEIGHT;

            int bgColor = isSelected ? 0xCC1A6B2A : (isHovered ? 0xCC2A2A2A : 0xCC111111);
            int borderColor = isSelected ? 0xFF44FF44 : (isHovered ? 0xFF888888 : 0xFF444444);

            graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, bgColor);
            graphics.renderOutline(x, y, CARD_WIDTH, CARD_HEIGHT, borderColor);

            // Gun icon — render as item using a placeholder item for visual
            // On real server with TACZ loaded this renders the actual gun item
            ItemStack stack;
            try {
                stack = GunHelper.buildGun(gunId);
                if (stack.isEmpty()) stack = new ItemStack(Items.BOW);
            } catch (Exception e) {
                stack = new ItemStack(Items.BOW);
            }
            int iconX = x + (CARD_WIDTH - 32) / 2;
            int iconY = y + 12;
            graphics.pose().pushPose();
            graphics.pose().translate(iconX + 16, iconY + 16, 0);
            graphics.pose().scale(2.0f, 2.0f, 1.0f);
            graphics.pose().translate(-8, -8, 0);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();

            String displayName = GunHelper.getGunDisplayName(gunId);
            graphics.drawCenteredString(font, "§f" + displayName, x + CARD_WIDTH / 2, y + 60, 0xFFFFFF);

            if (isSelected) {
                graphics.drawCenteredString(font, "§a✔ Selected", x + CARD_WIDTH / 2, y + 74, 0x44FF44);
            } else {
                graphics.drawCenteredString(font, "§7Click to select", x + CARD_WIDTH / 2, y + 74, 0x888888);
            }

            String category = getCategoryLabel(gunId);
            graphics.drawCenteredString(font, "§e" + category, x + CARD_WIDTH / 2, y + 88, 0xFFAA00);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int totalWidth = guns.size() * CARD_WIDTH + (guns.size() - 1) * CARD_GAP;
        int startX = (this.width - totalWidth) / 2;
        int startY = (this.height - CARD_HEIGHT) / 2 - 20;

        for (int i = 0; i < guns.size(); i++) {
            int x = startX + i * (CARD_WIDTH + CARD_GAP);
            int y = startY;
            if (mouseX >= x && mouseX <= x + CARD_WIDTH && mouseY >= y && mouseY <= y + CARD_HEIGHT) {
                selected = guns.get(i);
                PacketHandler.CHANNEL.sendToServer(new SelectGunPacket(selected));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String getCategoryLabel(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("m700") || path.contains("awp") || path.contains("sniper") || path.contains("kar")) return "Sniper";
        if (path.contains("mp5") || path.contains("mp7") || path.contains("p90") || path.contains("smg")) return "SMG";
        if (path.contains("shotgun") || path.contains("spas") || path.contains("m1014")) return "Shotgun";
        if (path.contains("pistol") || path.contains("desert") || path.contains("glock")) return "Pistol";
        return "Rifle";
    }
}