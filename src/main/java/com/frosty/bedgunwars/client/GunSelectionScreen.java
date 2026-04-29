package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.game.GunHelper;
import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.network.SelectAttachmentPacket;
import com.frosty.bedgunwars.network.SelectGunPacket;
import com.frosty.bedgunwars.network.SelectThrowablePacket;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GunSelectionScreen extends Screen {

    // ── Data ────────────────────────────────────────────────────────────────
    private final List<ResourceLocation> allGuns;
    private final List<ResourceLocation> selectedGuns;
    private final List<ResourceLocation> allAttachments;
    private final List<ResourceLocation> selectedAttachments;
    private final List<ResourceLocation> allThrowables;
    private final List<ResourceLocation> selectedThrowables;

    private static final int MAX_GUN_SLOTS        = 3;
    private static final int MAX_ATTACHMENT_PICKS = 5;
    private static final int MAX_THROWABLE_PICKS  = 5;

    // ── Layout ───────────────────────────────────────────────────────────────
    // Left panel: item list
    private static final int LIST_X      = 10;
    private static final int LIST_Y      = 70;
    private static final int LIST_W      = 200;
    private static final int ROW_H       = 36;   // taller rows for bigger icons
    private static final int ICON_SIZE   = 32;
    private static final int VISIBLE_ROWS = 9;

    // Scrollbar
    private static final int SB_W  = 8;
    private static final int SB_X  = LIST_X + LIST_W + 2;

    // Centre panel: category filter buttons
    private static final int CAT_X  = LIST_X + LIST_W + SB_W + 8;
    private static final int CAT_Y  = LIST_Y;
    private static final int CAT_W  = 80;
    private static final int CAT_H  = 16;
    private static final int CAT_GAP = 3;

    // Right panel: loadout summary
    private static final int PANEL_W = 140;

    // Tabs
    private static final int TAB_H = 20;
    private static final int TAB_W = 90;

    // ── State ────────────────────────────────────────────────────────────────
    private int  activeTab    = 0;   // 0=guns, 1=attachments, 2=throwables
    private int  scrollOffset = 0;
    private int  activeCategory = 0; // 0=All, then tab-specific
    private boolean draggingScrollbar = false;

    private EditBox searchBox;

    // Gun categories
    private static final String[] GUN_CATS  = {"All", "Rifle", "SMG", "Pistol", "Sniper", "Shotgun", "LMG"};
    // Attachment categories
    private static final String[] ATT_CATS  = {"All", "Scope", "Grip", "Muzzle", "Stock", "Magazine"};
    // Throwable categories
    private static final String[] THROW_CATS = {"All", "Frag", "Smoke", "Flash"};

    // ── Constructor / open ───────────────────────────────────────────────────
    public GunSelectionScreen(
            List<ResourceLocation> allGuns,        List<ResourceLocation> currentGuns,
            List<ResourceLocation> allAttachments, List<ResourceLocation> currentAttachments,
            List<ResourceLocation> allThrowables,  List<ResourceLocation> currentThrowables) {
        super(Component.literal("Loadout Selection"));
        this.allGuns             = allGuns;
        this.selectedGuns        = new ArrayList<>(currentGuns);
        this.allAttachments      = allAttachments;
        this.selectedAttachments = new ArrayList<>(currentAttachments);
        this.allThrowables       = allThrowables;
        this.selectedThrowables  = new ArrayList<>(currentThrowables);
    }

    public static void open(
            List<ResourceLocation> allGuns,        List<ResourceLocation> currentGuns,
            List<ResourceLocation> allAttachments, List<ResourceLocation> currentAttachments,
            List<ResourceLocation> allThrowables,  List<ResourceLocation> currentThrowables) {
        Minecraft.getInstance().setScreen(new GunSelectionScreen(
                allGuns, currentGuns,
                allAttachments, currentAttachments,
                allThrowables, currentThrowables));
    }

    @Override
    protected void init() {
        searchBox = new EditBox(font, LIST_X, LIST_Y - 20, LIST_W, 16, Component.literal("Search..."));
        searchBox.setMaxLength(40);
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(s -> { scrollOffset = 0; });
        addRenderableWidget(searchBox);
    }

    // ── Active helpers ────────────────────────────────────────────────────────
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

    private String[] activeCategories() {
        return switch (activeTab) {
            case 1  -> ATT_CATS;
            case 2  -> THROW_CATS;
            default -> GUN_CATS;
        };
    }

    /** Returns filtered + searched list for the current tab. */
    private List<ResourceLocation> filteredCatalogue() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
        String[] cats = activeCategories();
        String cat = activeCategory < cats.length ? cats[activeCategory] : "All";

        return activeCatalogue().stream().filter(id -> {
            String name = displayName(id).toLowerCase();
            String path = id.getPath();
            boolean matchSearch = query.isEmpty() || name.contains(query) || path.contains(query);
            boolean matchCat = cat.equals("All") || resolveCategory(id).equals(cat);
            return matchSearch && matchCat;
        }).collect(Collectors.toList());
    }

    private String displayName(ResourceLocation id) {
        return activeTab == 1
                ? GunHelper.getAttachmentDisplayName(id)
                : GunHelper.getGunDisplayName(id);
    }

    private String resolveCategory(ResourceLocation id) {
        return activeTab == 0 ? getCategoryLabel(id)
                : activeTab == 1 ? getAttachmentCategory(id, 1)
                  : getAttachmentCategory(id, 2);
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int panelX = this.width - PANEL_W - 6;

        // Title bar
        g.fill(0, 0, this.width, 18, 0xFF0A0A0A);
        g.drawCenteredString(font, "§6§lLOADOUT SELECTION", this.width / 2, 5, 0xFFFFFF);

        // Tabs
        renderTabs(g, mouseX, mouseY);

        // Search box rendered by super

        // Left list
        renderItemList(g, mouseX, mouseY);

        // Scrollbar
        renderScrollbar(g, mouseX, mouseY);

        // Category filters
        renderCategories(g, mouseX, mouseY);

        // Right loadout panel
        renderLoadoutPanel(g, panelX);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        String[] labels = {"Weapons", "Attachments", "Throwables"};
        int tabY = 20;
        for (int t = 0; t < 3; t++) {
            int tx = LIST_X + t * (TAB_W + 2);
            boolean active  = activeTab == t;
            boolean hovered = mx >= tx && mx < tx + TAB_W && my >= tabY && my < tabY + TAB_H;
            g.fill(tx, tabY, tx + TAB_W, tabY + TAB_H, active ? 0xFF333333 : (hovered ? 0xFF222222 : 0xFF161616));
            if (active) g.fill(tx, tabY + TAB_H - 2, tx + TAB_W, tabY + TAB_H, 0xFFFFAA00);
            g.drawCenteredString(font, (active ? "§e" : "§7") + labels[t], tx + TAB_W / 2, tabY + 6, 0xFFFFFF);
        }
    }

    private void renderItemList(GuiGraphics g, int mx, int my) {
        List<ResourceLocation> filtered = filteredCatalogue();
        List<ResourceLocation> selection = activeSelection();
        int listH = VISIBLE_ROWS * ROW_H;

        int maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Background
        g.fill(LIST_X - 1, LIST_Y - 1, LIST_X + LIST_W + 1, LIST_Y + listH + 1, 0xFF1A1A1A);

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, "§7No items found", LIST_X + LIST_W / 2, LIST_Y + listH / 2 - 4, 0x666666);
            return;
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= filtered.size()) break;

            ResourceLocation id = filtered.get(idx);
            int rowY = LIST_Y + i * ROW_H;
            boolean isSelected = selection.contains(id);
            long count = selection.stream().filter(id::equals).count();
            boolean hovered = mx >= LIST_X && mx < LIST_X + LIST_W && my >= rowY && my < rowY + ROW_H;

            // Row background
            int rowBg = isSelected ? 0xCC1A4A1A : (hovered ? 0xCC252525 : (i % 2 == 0 ? 0xCC111111 : 0xCC0E0E0E));
            g.fill(LIST_X, rowY, LIST_X + LIST_W, rowY + ROW_H, rowBg);
            if (isSelected) g.renderOutline(LIST_X, rowY, LIST_W, ROW_H, 0xFF33BB33);

            // Icon (bigger: ICON_SIZE x ICON_SIZE)
            ItemStack stack = activeTab == 0 ? buildGunStack(id) : buildAttachmentStack(id);
            g.pose().pushPose();
            float scale = ICON_SIZE / 16.0f;
            int iconX = LIST_X + 3;
            int iconY = rowY + (ROW_H - ICON_SIZE) / 2;
            g.pose().translate(iconX, iconY, 0);
            g.pose().scale(scale, scale, 1);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();

            // Name
            int textX = LIST_X + ICON_SIZE + 8;
            String name = displayName(id);
            g.drawString(font, "§f" + name, textX, rowY + 6, 0xFFFFFF);

            // Category tag
            String cat = resolveCategory(id);
            int catColor = getCategoryColor(cat);
            g.drawString(font, "§7" + cat, textX, rowY + 17, catColor);

            // Selection indicator
            if (activeTab == 0 && isSelected) {
                int slot = selection.indexOf(id) + 1;
                String tag = "[" + slot + "]";
                g.drawString(font, "§a" + tag, LIST_X + LIST_W - font.width(tag) - 4, rowY + ROW_H / 2 - 4, 0x44FF44);
            } else if (activeTab >= 1 && count > 0) {
                String tag = "x" + count;
                g.drawString(font, "§a" + tag, LIST_X + LIST_W - font.width(tag) - 4, rowY + ROW_H / 2 - 4, 0x44FF44);
            }
        }
    }

    private void renderScrollbar(GuiGraphics g, int mx, int my) {
        List<ResourceLocation> filtered = filteredCatalogue();
        int listH = VISIBLE_ROWS * ROW_H;
        if (filtered.size() <= VISIBLE_ROWS) return;

        g.fill(SB_X, LIST_Y, SB_X + SB_W, LIST_Y + listH, 0xFF111111);
        int maxScroll = filtered.size() - VISIBLE_ROWS;
        int thumbH = Math.max(16, listH * VISIBLE_ROWS / filtered.size());
        int thumbY = LIST_Y + (listH - thumbH) * scrollOffset / maxScroll;
        boolean sbHovered = mx >= SB_X && mx < SB_X + SB_W && my >= LIST_Y && my < LIST_Y + listH;
        g.fill(SB_X + 1, thumbY, SB_X + SB_W - 1, thumbY + thumbH, sbHovered || draggingScrollbar ? 0xFFAAAAAA : 0xFF666666);
    }

    private void renderCategories(GuiGraphics g, int mx, int my) {
        String[] cats = activeCategories();
        for (int i = 0; i < cats.length; i++) {
            int cx = CAT_X;
            int cy = CAT_Y + i * (CAT_H + CAT_GAP);
            boolean active  = activeCategory == i;
            boolean hovered = mx >= cx && mx < cx + CAT_W && my >= cy && my < cy + CAT_H;
            g.fill(cx, cy, cx + CAT_W, cy + CAT_H, active ? 0xFF444444 : (hovered ? 0xFF2A2A2A : 0xFF1A1A1A));
            if (active) g.fill(cx, cy, cx + 2, cy + CAT_H, 0xFFFFAA00);
            g.drawString(font, (active ? "§e" : "§7") + cats[i], cx + 6, cy + 4, 0xFFFFFF);
        }
        // Label above
        g.drawString(font, "§8Filter", CAT_X, CAT_Y - 10, 0x555555);
    }

    private void renderLoadoutPanel(GuiGraphics g, int panelX) {
        int panelY = LIST_Y;
        g.fill(panelX - 4, LIST_Y - 22, panelX + PANEL_W + 4, LIST_Y - 2, 0xFF111111);
        g.drawCenteredString(font, "§e§lYOUR LOADOUT", panelX + PANEL_W / 2, LIST_Y - 18, 0xFFAA00);

        // Weapons
        g.drawString(font, "§eWeapons", panelX, panelY, 0xFFAA00);
        for (int i = 0; i < MAX_GUN_SLOTS; i++) {
            int slotY = panelY + 12 + i * 28;
            g.fill(panelX, slotY, panelX + PANEL_W, slotY + 24, 0xFF111111);
            g.renderOutline(panelX, slotY, PANEL_W, 24, i < selectedGuns.size() ? 0xFF335533 : 0xFF333333);
            if (i < selectedGuns.size()) {
                ItemStack s = buildGunStack(selectedGuns.get(i));
                g.pose().pushPose();
                g.pose().translate(panelX + 2, slotY + 4, 0);
                g.pose().scale(1.0f, 1.0f, 1);
                g.renderItem(s, 0, 0);
                g.pose().popPose();
                g.drawString(font, "§f" + shorten(GunHelper.getGunDisplayName(selectedGuns.get(i)), 13), panelX + 22, slotY + 8, 0xFFFFFF);
            } else {
                g.drawCenteredString(font, "§8- Empty -", panelX + PANEL_W / 2, slotY + 8, 0x444444);
            }
        }

        // Attachments
        int attY = panelY + 12 + MAX_GUN_SLOTS * 28 + 8;
        g.drawString(font, "§bAttachments §8(" + selectedAttachments.size() + "/" + MAX_ATTACHMENT_PICKS + ")", panelX, attY, 0x55FFFF);
        for (int i = 0; i < MAX_ATTACHMENT_PICKS; i++) {
            int slotY = attY + 12 + i * 20;
            g.fill(panelX, slotY, panelX + PANEL_W, slotY + 18, 0xFF111111);
            g.renderOutline(panelX, slotY, PANEL_W, 18, i < selectedAttachments.size() ? 0xFF224444 : 0xFF222222);
            if (i < selectedAttachments.size()) {
                g.renderItem(buildAttachmentStack(selectedAttachments.get(i)), panelX + 1, slotY + 1);
                g.drawString(font, "§f" + shorten(GunHelper.getAttachmentDisplayName(selectedAttachments.get(i)), 13), panelX + 20, slotY + 5, 0xFFFFFF);
            } else {
                g.drawString(font, "§8- empty -", panelX + 4, slotY + 5, 0x333333);
            }
        }

        // Throwables
        int throwY = attY + 12 + MAX_ATTACHMENT_PICKS * 20 + 8;
        g.drawString(font, "§cThrowables §8(" + selectedThrowables.size() + "/" + MAX_THROWABLE_PICKS + ")", panelX, throwY, 0xFF5555);
        for (int i = 0; i < MAX_THROWABLE_PICKS; i++) {
            int slotY = throwY + 12 + i * 20;
            g.fill(panelX, slotY, panelX + PANEL_W, slotY + 18, 0xFF111111);
            g.renderOutline(panelX, slotY, PANEL_W, 18, i < selectedThrowables.size() ? 0xFF442222 : 0xFF222222);
            if (i < selectedThrowables.size()) {
                g.renderItem(buildThrowableStack(selectedThrowables.get(i)), panelX + 1, slotY + 1);
                g.drawString(font, "§f" + shorten(GunHelper.getGunDisplayName(selectedThrowables.get(i)), 13), panelX + 20, slotY + 5, 0xFFFFFF);
            } else {
                g.drawString(font, "§8- empty -", panelX + 4, slotY + 5, 0x333333);
            }
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Tabs
        int tabY = 20;
        for (int t = 0; t < 3; t++) {
            int tx = LIST_X + t * (TAB_W + 2);
            if (mx >= tx && mx < tx + TAB_W && my >= tabY && my < tabY + TAB_H) {
                activeTab = t;
                activeCategory = 0;
                scrollOffset = 0;
                if (searchBox != null) searchBox.setValue("");
                return true;
            }
        }

        // Category filters
        String[] cats = activeCategories();
        for (int i = 0; i < cats.length; i++) {
            int cy = CAT_Y + i * (CAT_H + CAT_GAP);
            if (mx >= CAT_X && mx < CAT_X + CAT_W && my >= cy && my < cy + CAT_H) {
                activeCategory = i;
                scrollOffset = 0;
                return true;
            }
        }

        // Scrollbar click
        List<ResourceLocation> filtered = filteredCatalogue();
        int listH = VISIBLE_ROWS * ROW_H;
        if (filtered.size() > VISIBLE_ROWS && mx >= SB_X && mx < SB_X + SB_W && my >= LIST_Y && my < LIST_Y + listH) {
            draggingScrollbar = true;
            updateScrollFromMouse(my);
            return true;
        }

        // List click
        List<ResourceLocation> selection = activeSelection();
        int maxPicks = activeMaxPicks();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx  = i + scrollOffset;
            if (idx >= filtered.size()) break;
            int rowY = LIST_Y + i * ROW_H;
            if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= rowY && my < rowY + ROW_H) {
                ResourceLocation id = filtered.get(idx);
                if (activeTab == 0) {
                    if (selection.contains(id)) selection.remove(id);
                    else if (selection.size() < maxPicks) selection.add(id);
                    PacketHandler.CHANNEL.sendToServer(new SelectGunPacket(new ArrayList<>(selection)));
                } else {
                    if (button == 1) {
                        for (int j = selection.size() - 1; j >= 0; j--) {
                            if (selection.get(j).equals(id)) { selection.remove(j); break; }
                        }
                    } else if (selection.size() < maxPicks) {
                        selection.add(id);
                    }
                    if (activeTab == 1) PacketHandler.CHANNEL.sendToServer(new SelectAttachmentPacket(new ArrayList<>(selection)));
                    else               PacketHandler.CHANNEL.sendToServer(new SelectThrowablePacket(new ArrayList<>(selection)));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScrollbar) {
            updateScrollFromMouse(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int size = filteredCatalogue().size();
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) delta, Math.max(0, size - VISIBLE_ROWS)));
        return true;
    }

    private void updateScrollFromMouse(double my) {
        List<ResourceLocation> filtered = filteredCatalogue();
        int listH = VISIBLE_ROWS * ROW_H;
        int maxScroll = Math.max(1, filtered.size() - VISIBLE_ROWS);
        double ratio = (my - LIST_Y) / listH;
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, ratio * filtered.size()));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Item stack builders ───────────────────────────────────────────────────
    private ItemStack buildGunStack(ResourceLocation id) {
        try {
            ItemStack s = GunHelper.buildGun(id);
            return s.isEmpty() ? new ItemStack(Items.BOW) : s;
        } catch (Exception e) {
            return new ItemStack(Items.BOW);
        }
    }

    private ItemStack buildAttachmentStack(ResourceLocation id) {
        try {
            ItemStack s = AttachmentItemBuilder.create().setId(id).build();
            if (!s.isEmpty()) return s;
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    private ItemStack buildThrowableStack(ResourceLocation id) {
        try {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) return new ItemStack(item);
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    // Category helpers
    private String getCategoryLabel(ResourceLocation id) {
        return GunHelper.getGunCategory(id);
    }

    private String getAttachmentCategory(ResourceLocation id, int tab) {
        if (tab == 2) {
            String p = id.getPath();
            if (p.contains("smoke")) return "Smoke";
            if (p.contains("flash")) return "Flash";
            if (p.contains("frag") || p.contains("grenade")) return "Frag";
            return "Other";
        }
        String p = id.getPath();
        if (p.contains("scope") || p.contains("sight") || p.contains("acog") || p.contains("red_dot")) return "Scope";
        if (p.contains("grip") || p.contains("foregrip")) return "Grip";
        if (p.contains("muzzle") || p.contains("suppressor") || p.contains("silencer") || p.contains("compensator")) return "Muzzle";
        if (p.contains("stock") || p.contains("butt")) return "Stock";
        if (p.contains("mag") || p.contains("magazine") || p.contains("drum")) return "Magazine";
        return "Other";
    }

    private int getCategoryColor(String cat) {
        return switch (cat) {
            case "Sniper"   -> 0x88AAFF;
            case "SMG"      -> 0xFFDD44;
            case "Pistol"   -> 0xAAFF88;
            case "Shotgun"  -> 0xFF8844;
            case "LMG"      -> 0xFF4444;
            case "Scope"    -> 0x44DDFF;
            case "Grip"     -> 0xFFAA44;
            case "Muzzle"   -> 0xBBAA88;
            case "Stock"    -> 0xAA88FF;
            case "Magazine" -> 0xFF6688;
            case "Frag"     -> 0xFF4422;
            case "Smoke"    -> 0xAABBAA;
            case "Flash"    -> 0xFFFFAA;
            default         -> 0x888888;
        };
    }

    private String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}