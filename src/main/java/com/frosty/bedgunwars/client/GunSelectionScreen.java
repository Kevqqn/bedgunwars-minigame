package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.game.GunHelper;
import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.network.SelectAttachmentPacket;
import com.frosty.bedgunwars.network.SelectGunPacket;
import com.frosty.bedgunwars.network.SelectThrowablePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;

import java.util.ArrayList;
import java.util.List;

public class GunSelectionScreen extends Screen {

    private final List<ResourceLocation> allGuns;
    private final List<ResourceLocation> selectedGuns;
    private final List<ResourceLocation> allAttachments;
    private final List<ResourceLocation> selectedAttachments;   // duplicates allowed
    private final List<ResourceLocation> allThrowables;
    private final List<ResourceLocation> selectedThrowables;    // duplicates allowed

    private static final int MAX_GUN_SLOTS        = 3;
    private static final int MAX_ATTACHMENT_PICKS = 5;
    private static final int MAX_THROWABLE_PICKS  = 5;


    private static final int TAB_HEIGHT    = 20;
    private static final int ROW_HEIGHT    = 24;
    private static final int VISIBLE_ROWS  = 10;
    private static final int LIST_WIDTH    = 220;
    private static final int LIST_X_OFFSET = -120; // from screen centre
    private static final int LIST_Y        = 60;   // leaves room for tabs

    private static final int PANEL_X_OFFSET = 120; // from screen centre
    private static final int PANEL_WIDTH     = 130;


    private int activeTab = 0; // 0=guns, 1=attachments, 2=throwables
    private int scrollOffset = 0;

    public GunSelectionScreen(
            List<ResourceLocation> allGuns,         List<ResourceLocation> currentGuns,
            List<ResourceLocation> allAttachments,  List<ResourceLocation> currentAttachments,
            List<ResourceLocation> allThrowables,   List<ResourceLocation> currentThrowables) {
        super(Component.literal("Select Loadout"));
        this.allGuns             = allGuns;
        this.selectedGuns        = new ArrayList<>(currentGuns);
        this.allAttachments      = allAttachments;
        this.selectedAttachments = new ArrayList<>(currentAttachments);
        this.allThrowables       = allThrowables;
        this.selectedThrowables  = new ArrayList<>(currentThrowables);
    }

    public static void open(
            List<ResourceLocation> allGuns,         List<ResourceLocation> currentGuns,
            List<ResourceLocation> allAttachments,  List<ResourceLocation> currentAttachments,
            List<ResourceLocation> allThrowables,   List<ResourceLocation> currentThrowables) {
        Minecraft.getInstance().setScreen(new GunSelectionScreen(
                allGuns, currentGuns,
                allAttachments, currentAttachments,
                allThrowables, currentThrowables));
    }

    private List<ResourceLocation> activeCatalogue() {
        return switch (activeTab) {
            case 1  -> allAttachments;
            case 2  -> allThrowables;
            default -> allGuns;
        };
    }

    private List<ResourceLocation> activeSelection() {
        return switch (activeTab) {
            case 1  -> selectedAttachments;
            case 2  -> selectedThrowables;
            default -> selectedGuns;
        };
    }

    private int activeMaxPicks() {
        return switch (activeTab) {
            case 1  -> MAX_ATTACHMENT_PICKS;
            case 2  -> MAX_THROWABLE_PICKS;
            default -> MAX_GUN_SLOTS;
        };
    }

//    private String activeTabLabel() {
//        return switch (activeTab) {
//            case 1  -> "Attachments";
//            case 2  -> "Throwables";
//            default -> "Weapons";
//        };
//    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // Title
        g.drawCenteredString(font, "§6§lLoadout Selection", this.width / 2, 6, 0xFFFFFF);

        // Tabs
        renderTabs(g, mouseX, mouseY);

        int listX = this.width / 2 + LIST_X_OFFSET;
        int listY = LIST_Y;
        int listHeight = VISIBLE_ROWS * ROW_HEIGHT;

        List<ResourceLocation> catalogue = activeCatalogue();
        List<ResourceLocation> selection = activeSelection();
        int maxPicks = activeMaxPicks();

        // Subtitle
        String subtitle = "§7Choose up to " + maxPicks + "  •  Scroll  •  ESC to close";
        if (activeTab >= 1) subtitle = "§7Click to add (up to " + maxPicks + " picks)  •  Right-click to remove  •  ESC";
        g.drawCenteredString(font, subtitle, this.width / 2, LIST_Y - 36, 0xAAAAAA);

        // List background
        g.fill(listX - 2, listY - 2, listX + LIST_WIDTH + 2, listY + listHeight + 2, 0xFF222222);

        int maxScroll = Math.max(0, catalogue.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (catalogue.isEmpty()) {
            g.drawCenteredString(font, "§cNo items found", listX + LIST_WIDTH / 2, listY + listHeight / 2 - 4, 0xFF4444);
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= catalogue.size()) break;

            ResourceLocation itemId = catalogue.get(idx);
            int rowY = listY + i * ROW_HEIGHT;

            boolean isSelected = selection.contains(itemId);
            // For attachments/throwables count occurrences
            long count = selection.stream().filter(itemId::equals).count();
            boolean hovered = mouseX >= listX && mouseX <= listX + LIST_WIDTH
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;

            int bg = isSelected ? 0xCC1A5C1A : (hovered ? 0xCC2A2A2A : 0xCC111111);
            g.fill(listX, rowY, listX + LIST_WIDTH, rowY + ROW_HEIGHT, bg);
            if (isSelected) g.renderOutline(listX, rowY, LIST_WIDTH, ROW_HEIGHT, 0xFF44FF44);

            // Item icon
            ItemStack stack = activeTab == 0 ? buildGunStack(itemId) : buildNonGunStack(itemId);
            g.renderItem(stack, listX + 3, rowY + 4);

            // Name & category
            String name = activeTab == 1 ? GunHelper.getAttachmentDisplayName(itemId) : GunHelper.getGunDisplayName(itemId);
            String cat  = activeTab == 0 ? getCategoryLabel(itemId) : getAttachmentCategory(itemId, activeTab);
            g.drawString(font, "§f" + name, listX + 22, rowY + 4, 0xFFFFFF);
            g.drawString(font, "§7" + cat, listX + 22, rowY + 13, 0x888888);

            // Right side indicator
            if (activeTab == 0 && isSelected) {
                int slot = selection.indexOf(itemId) + 1;
                g.drawString(font, "§a[Slot " + slot + "]", listX + LIST_WIDTH - 50, rowY + 8, 0x44FF44);
            } else if (activeTab >= 1 && count > 0) {
                g.drawString(font, "§a×" + count, listX + LIST_WIDTH - 24, rowY + 8, 0x44FF44);
            }
        }

        // Scroll
        if (catalogue.size() > VISIBLE_ROWS) {
            int sbX = listX + LIST_WIDTH + 4;
            int sbH = listHeight;
            g.fill(sbX, listY, sbX + 4, listY + sbH, 0xFF333333);
            int thumbH = Math.max(10, sbH * VISIBLE_ROWS / catalogue.size());
            int thumbY = listY + (sbH - thumbH) * scrollOffset / Math.max(1, catalogue.size() - VISIBLE_ROWS);
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF888888);
        }

        // Right panel – current loadout
        renderLoadoutPanel(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        String[] labels = {"⚔ Weapons", "🔧 Attachments", "(WIP) Throwables"}; // bruh throwables is not in official TACZ
        int tabW   = 100;
        int tabY   = LIST_Y - TAB_HEIGHT - 2;
        int startX = this.width / 2 + LIST_X_OFFSET;

        for (int t = 0; t < 3; t++) {
            int tx = startX + t * (tabW + 2);
            boolean active  = activeTab == t;
            boolean hovered = mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;
            int bg = active ? 0xFF444444 : (hovered ? 0xFF2A2A2A : 0xFF1A1A1A);
            g.fill(tx, tabY, tx + tabW, tabY + TAB_HEIGHT, bg);
            if (active) g.fill(tx, tabY + TAB_HEIGHT - 2, tx + tabW, tabY + TAB_HEIGHT, 0xFFFFAA00);
            g.drawCenteredString(font, (active ? "§e" : "§7") + labels[t], tx + tabW / 2, tabY + 6, 0xFFFFFF);
        }
    }

    private void renderLoadoutPanel(GuiGraphics g) {
        int panelX = this.width / 2 + PANEL_X_OFFSET;
        int panelY = LIST_Y;

        // Gun loadout
        g.drawString(font, "§eWeapons", panelX, panelY, 0xFFAA00);
        for (int i = 0; i < MAX_GUN_SLOTS; i++) {
            int slotY = panelY + 12 + i * 26;
            g.fill(panelX, slotY, panelX + PANEL_WIDTH, slotY + 22, 0xCC111111);
            g.renderOutline(panelX, slotY, PANEL_WIDTH, 22, 0xFF444444);
            if (i < selectedGuns.size()) {
                ItemStack s = buildGunStack(selectedGuns.get(i));
                g.renderItem(s, panelX + 3, slotY + 3);
                g.drawString(font, "§f" + shorten(GunHelper.getGunDisplayName(selectedGuns.get(i)), 12),
                        panelX + 22, slotY + 7, 0xFFFFFF);
            } else {
                g.drawCenteredString(font, "§7- Empty -", panelX + PANEL_WIDTH / 2, slotY + 7, 0x555555);
            }
        }

        // Attachment picks
        int attY = panelY + 12 + MAX_GUN_SLOTS * 26 + 10;
        g.drawString(font, "§bAttachments §7(" + selectedAttachments.size() + "/" + MAX_ATTACHMENT_PICKS + ")",
                panelX, attY, 0x55FFFF);
        for (int i = 0; i < MAX_ATTACHMENT_PICKS; i++) {
            int slotY = attY + 12 + i * 18;
            g.fill(panelX, slotY, panelX + PANEL_WIDTH, slotY + 16, 0xCC111111);
            g.renderOutline(panelX, slotY, PANEL_WIDTH, 16, 0xFF333333);
            if (i < selectedAttachments.size()) {
                ItemStack s = buildNonGunStack(selectedAttachments.get(i));
                g.renderItem(s, panelX + 1, slotY);
                g.drawString(font, "§f" + shorten(GunHelper.getAttachmentDisplayName(selectedAttachments.get(i)), 14),                        panelX + 18, slotY + 4, 0xFFFFFF);
            } else {
                g.drawString(font, "§7- empty -", panelX + 4, slotY + 4, 0x444444);
            }
        }

        // Throwable picks
        int throwY = attY + 12 + MAX_ATTACHMENT_PICKS * 18 + 10;
        g.drawString(font, "§cThrowables §7(" + selectedThrowables.size() + "/" + MAX_THROWABLE_PICKS + ")",
                panelX, throwY, 0xFF5555);
        for (int i = 0; i < MAX_THROWABLE_PICKS; i++) {
            int slotY = throwY + 12 + i * 18;
            g.fill(panelX, slotY, panelX + PANEL_WIDTH, slotY + 16, 0xCC111111);
            g.renderOutline(panelX, slotY, PANEL_WIDTH, 16, 0xFF333333);
            if (i < selectedThrowables.size()) {
                ItemStack s = buildNonGunStack(selectedThrowables.get(i));
                g.renderItem(s, panelX + 1, slotY);
                g.drawString(font, "§f" + shorten(GunHelper.getGunDisplayName(selectedThrowables.get(i)), 14),
                        panelX + 18, slotY + 4, 0xFFFFFF);
            } else {
                g.drawString(font, "§7- empty -", panelX + 4, slotY + 4, 0x444444);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Tab click
        int tabW   = 100;
        int tabY   = LIST_Y - TAB_HEIGHT - 2;
        int startX = this.width / 2 + LIST_X_OFFSET;
        for (int t = 0; t < 3; t++) {
            int tx = startX + t * (tabW + 2);
            if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT) {
                activeTab    = t;
                scrollOffset = 0;
                return true;
            }
        }

        // List click
        int listX = this.width / 2 + LIST_X_OFFSET;
        int listY = LIST_Y;
        List<ResourceLocation> catalogue = activeCatalogue();
        List<ResourceLocation> selection = activeSelection();
        int maxPicks = activeMaxPicks();

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx  = i + scrollOffset;
            if (idx >= catalogue.size()) break;
            int rowY = listY + i * ROW_HEIGHT;
            if (mouseX >= listX && mouseX <= listX + LIST_WIDTH && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                ResourceLocation itemId = catalogue.get(idx);

                if (activeTab == 0) {
                    // Guns: toggle exclusive
                    if (selection.contains(itemId)) {
                        selection.remove(itemId);
                    } else if (selection.size() < maxPicks) {
                        selection.add(itemId);
                    }
                    PacketHandler.CHANNEL.sendToServer(new SelectGunPacket(new ArrayList<>(selection)));
                } else {
                    // Attachments / throwables: left-click = add (if under limit), right-click = remove one
                    if (button == 1) { // right click
                        // Remove last occurrence
                        for (int j = selection.size() - 1; j >= 0; j--) {
                            if (selection.get(j).equals(itemId)) {
                                selection.remove(j);
                                break;
                            }
                        }
                    } else {
                        if (selection.size() < maxPicks) {
                            selection.add(itemId);
                        }
                    }
                    if (activeTab == 1) {
                        PacketHandler.CHANNEL.sendToServer(new SelectAttachmentPacket(new ArrayList<>(selection)));
                    } else {
                        PacketHandler.CHANNEL.sendToServer(new SelectThrowablePacket(new ArrayList<>(selection)));
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int size = activeCatalogue().size();
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) delta, Math.max(0, size - VISIBLE_ROWS)));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private ItemStack buildGunStack(ResourceLocation id) {
        try {
            ItemStack s = GunHelper.buildGun(id);
            return s.isEmpty() ? new ItemStack(Items.BOW) : s;
        } catch (Exception e) {
            return new ItemStack(Items.BOW);
        }
    }

    private ItemStack buildNonGunStack(ResourceLocation id) {
        try {
            ItemStack s = AttachmentItemBuilder.create().setId(id).build();
            if (!s.isEmpty()) return s;
        } catch (Exception ignored) {}
        try {
            var item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) return new ItemStack(item);
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    private String getCategoryLabel(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("m700") || path.contains("awp") || path.contains("sniper")
                || path.contains("kar") || path.contains("sv98")) return "Sniper";
        if (path.contains("mp5") || path.contains("mp7") || path.contains("p90")
                || path.contains("smg") || path.contains("ump"))  return "SMG";
        if (path.contains("shotgun") || path.contains("spas") || path.contains("m1014")) return "Shotgun";
        if (path.contains("pistol") || path.contains("desert")
                || path.contains("glock") || path.contains("deagle"))  return "Pistol";
        return "Rifle";
    }

    private String getAttachmentCategory(ResourceLocation id, int tab) {
        if (tab == 2) {
            String path = id.getPath();
            if (path.contains("smoke"))     return "Smoke";
            if (path.contains("flash"))     return "Flashbang";
            if (path.contains("frag"))      return "Frag";
            return "Throwable";
        }
        String path = id.getPath();
        if (path.contains("scope"))    return "Scope";
        if (path.contains("grip"))     return "Grip";
        if (path.contains("muzzle"))   return "Muzzle";
        if (path.contains("stock"))    return "Stock";
        if (path.contains("magazine")) return "Magazine";
        return "Attachment";
    }

    private String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
