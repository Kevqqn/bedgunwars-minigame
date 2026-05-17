package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.game.GunHelper;
import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.network.SelectAttachmentPacket;
import com.frosty.bedgunwars.network.SelectGunPacket;
import com.frosty.bedgunwars.network.SelectThrowablePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.item.ItemDisplayContext;
import com.frosty.bedgunwars.client.ClientTips;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


import java.util.*;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

public class GunSelectionScreen extends Screen {

    // Data
    private  List<ResourceLocation> allGuns = new ArrayList<>();
    private final List<ResourceLocation> selectedGuns;
    private  List<ResourceLocation> allAttachments = new ArrayList<>();
    private List<ResourceLocation> allThrowables;
    private  List<ResourceLocation> selectedThrowables;

    // Per-gun attachment map, gun slot (0/1/2) > (AttachmentType.name() > attachmentId.toString())
    private Map<Integer, Map<String, String>> gunAttachments = new HashMap<>();

    private static final int MAX_GUN_SLOTS       = 3;
    private static final int MAX_THROWABLE_PICKS = 5;


    // Layout constants — updated to match redesigned spec

    private static final int LIST_X       = 10;
    private static final int LIST_Y       = 76;
    private static final int LIST_W       = 200;
    private static final int ROW_H        = 36;
    private static final int ICON_SIZE    = 32;
    private static final int VISIBLE_ROWS = 10;

    private static final int SB_W = 8;
    private static final int SB_X = LIST_X + LIST_W + 2;         // 212

    private static final int CAT_X   = SB_X + SB_W;              // 220 — flush with scrollbar end
    private static final int CAT_Y   = LIST_Y;                    // 76
    private static final int CAT_W   = 70;
    private static final int CAT_H   = 14;
    private static final int CAT_GAP = 2;

    private static final int PANEL_W = 178;
    private static final int TAB_H   = 20;
    private static final int TAB_W   = 90;

    private static final int BACK_W = 70;
    private static final int BACK_H = 18;
    private static final int BACK_X = LIST_X;
    private static final int BACK_Y_OFFSET = 10;

    private static final int CARD_W = 110;
    private static final int CARD_H = 130;
    private static final int CARD_GAP = 14;

    // Dynamic layout helpers (depend on this.width / this.height)
    private int panelX()  { return this.width - PANEL_W - 6; }
    private int detailX() { return CAT_X + CAT_W + 8; }          // ≈ 298
    private int detailY() { return LIST_Y - 22; }                 // 54
    private int detailW() { return panelX() - detailX() - 6; }
    private int detailH() { return this.height - detailY() - 8; }

    // 3D gun preview state
    private float smoothRotX = -10f;
    private float smoothRotY = -120f;
    private float previewRotX = -10f;
    private float previewRotY = -120f;
    private float previewScale = 1.0f;
    private boolean isDraggingPreview = false;
    private double lastDragX, lastDragY;
    private ResourceLocation lastPreviewGun = null;

    // Preview box bounds (set during render, used for hit detection)
    private int previewBoxX, previewBoxY, previewBoxW, previewBoxH;

    // State
    private int  activeTab      = 0;   // 0=guns, 1=attachments, 2=throwables, 3=loadouts
    private boolean activePhase = false;
    private int  scrollOffset   = 0;
    private int  activeCategory = 0;
    private boolean draggingScrollbar = false;

    // Detail-pane hover / focus tracking
    private ResourceLocation hoveredItemId = null;
    private ResourceLocation focusedItemId = null;

    // Loadout state
    private static java.util.List<com.frosty.bedgunwars.game.LoadoutManager.Loadout> clientLoadouts
            = new java.util.ArrayList<>();
    private int namingLoadoutIndex = -1;
    private String loadoutNameBuffer = "";
    private boolean namingNew = false;
    private int confirmDeleteIndex = -1;
    private int selectedLoadoutIndex = -1;
    private int modifyingLoadoutIndex = -1; // -1 = not modifying

    public static void updateLoadouts(
            java.util.List<com.frosty.bedgunwars.game.LoadoutManager.Loadout> loadouts) {
        clientLoadouts = new java.util.ArrayList<>(loadouts);
    }

    // Sound / hover tracking
    private ResourceLocation lastHoveredForSound = null;
    private String lastHoveredZoneForSound = null;

    private void playUiHover() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.get(),
                net.minecraft.sounds.SoundSource.MASTER,
                0.4f, (float) Math.pow(2.0, (18 - 12) / 12.0), false);
    }

    private void playUiClick() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.get(),
                net.minecraft.sounds.SoundSource.MASTER,
                0.5f, (float) Math.pow(2.0, (22 - 12) / 12.0), false);
    }
    private int attachmentEditorGunSlot = -1;
    private List<ResourceLocation> editorCompatibleAttachments = new ArrayList<>();
    private final int[] colScrollOffsets = new int[6];
    private int draggingColumnIndex = -1;

    private static final AttachmentType[] COLUMN_TYPES = {
            AttachmentType.SCOPE, AttachmentType.GRIP,     AttachmentType.MUZZLE,
            AttachmentType.STOCK, AttachmentType.EXTENDED_MAG,  AttachmentType.LASER
    };
    private static final String[] COLUMN_LABELS = {
            "Scope", "Grip", "Muzzle", "Stock", "Magazine", "Laser"
    };

    private int editorScrollOffset = 0;

    private EditBox searchBox;

    private static final String[] GUN_CATS   = {"All", "Rifle", "SMG", "Pistol", "Sniper", "Shotgun", "LMG"};
    private static final String[] THROW_CATS = {"All", "Frag", "Smoke", "Flash"};

    private static final Map<String, String> TYPE_DISPLAY = new LinkedHashMap<>();
    static {
        TYPE_DISPLAY.put("SCOPE",    "Scope");
        TYPE_DISPLAY.put("GRIP",     "Grip");
        TYPE_DISPLAY.put("MUZZLE",   "Muzzle");
        TYPE_DISPLAY.put("STOCK",    "Stock");
        TYPE_DISPLAY.put("MAGAZINE", "Magazine");
        TYPE_DISPLAY.put("LASER",    "Laser");
    }


    // Constructor / open

    public GunSelectionScreen(
            List<ResourceLocation> allGuns,        List<ResourceLocation> currentGuns,
            List<ResourceLocation> allAttachments, List<ResourceLocation> currentAttachments,
            List<ResourceLocation> allThrowables,  List<ResourceLocation> currentThrowables,
            Map<Integer, Map<String, String>> gunAttachments) {
        super(Component.literal("Loadout Selection"));
        this.allGuns = new ArrayList<>(allGuns);
        this.selectedGuns        = new ArrayList<>(currentGuns);
        this.allAttachments = new ArrayList<>(allAttachments);
        this.allThrowables = new ArrayList<>(allThrowables);
        this.selectedThrowables  = new ArrayList<>(currentThrowables);
        gunAttachments.forEach((slot, typeMap) ->
                this.gunAttachments.put(slot, new HashMap<>(typeMap)));
    }

    public void updateData(
            List<ResourceLocation> allGuns, List<ResourceLocation> currentGuns,
            List<ResourceLocation> allAttachments, List<ResourceLocation> allThrowables,
            List<ResourceLocation> currentThrowables, Map<Integer, Map<String, String>> gunAttachments) {
        int savedSlot = this.attachmentEditorGunSlot;
        this.allGuns.clear(); this.allGuns.addAll(allGuns);
        this.selectedGuns.clear(); this.selectedGuns.addAll(currentGuns);
        this.allAttachments.clear(); this.allAttachments.addAll(allAttachments);
        this.allThrowables.clear(); this.allThrowables.addAll(allThrowables);
        this.selectedThrowables.clear(); this.selectedThrowables.addAll(currentThrowables);
        this.gunAttachments.clear();
        gunAttachments.forEach((slot, typeMap) ->
                this.gunAttachments.put(slot, new HashMap<>(typeMap)));
        this.attachmentEditorGunSlot = savedSlot;
        if (savedSlot >= 0 && savedSlot < this.selectedGuns.size())
            rebuildEditorCache(savedSlot);
    }

    public static void open(
            List<ResourceLocation> allGuns,        List<ResourceLocation> currentGuns,
            List<ResourceLocation> allAttachments, List<ResourceLocation> currentAttachments,
            List<ResourceLocation> allThrowables,  List<ResourceLocation> currentThrowables,
            Map<Integer, Map<String, String>> gunAttachments) {
        open(allGuns, currentGuns, allAttachments, currentAttachments,
                allThrowables, currentThrowables, gunAttachments, false);
    }

    public static void open(
            List<ResourceLocation> allGuns,        List<ResourceLocation> currentGuns,
            List<ResourceLocation> allAttachments, List<ResourceLocation> currentAttachments,
            List<ResourceLocation> allThrowables,  List<ResourceLocation> currentThrowables,
            Map<Integer, Map<String, String>> gunAttachments, boolean activePhase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GunSelectionScreen existing) {
            existing.updateData(allGuns, currentGuns, allAttachments,
                    allThrowables, currentThrowables, gunAttachments);
            existing.activePhase = activePhase;
            if (activePhase) existing.activeTab = 3;
            return;
        }
        GunSelectionScreen screen = new GunSelectionScreen(allGuns, currentGuns,
                allAttachments, currentAttachments,
                allThrowables, currentThrowables, gunAttachments);
        screen.activePhase = activePhase;
        if (activePhase) screen.activeTab = 3;
        mc.setScreen(screen);
    }

    @Override
    protected void init() {
        searchBox = new EditBox(font, LIST_X, LIST_Y - 20, LIST_W, 16, Component.literal("Search..."));
        searchBox.setMaxLength(40);
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(s -> scrollOffset = 0);
        addRenderableWidget(searchBox);
    }


    // Helpers

    private boolean inAttachmentEditor()    { return activeTab == 1 && attachmentEditorGunSlot >= 0; }
    private boolean inAttachmentGunPicker() { return activeTab == 1 && attachmentEditorGunSlot < 0; }

    private void updateEditorScrollFromMouse(double my) {
        int listH = VISIBLE_ROWS * ROW_H;
        int total = editorCompatibleAttachments.size() + 6;
        int maxScroll = Math.max(1, total - VISIBLE_ROWS);
        double ratio = (my - LIST_Y) / listH;
        editorScrollOffset = (int) Math.max(0, Math.min(maxScroll, ratio * total));
    }

    private void enterEditor(int slot) {
        attachmentEditorGunSlot = slot;
        java.util.Arrays.fill(colScrollOffsets, 0);
        rebuildEditorCache(slot);
    }

    private void exitEditor() {
        attachmentEditorGunSlot = -1;
        draggingColumnIndex = -1;
    }

    private void rebuildEditorCache(int slot) {
        if (slot < 0 || slot >= selectedGuns.size()) {
            editorCompatibleAttachments = new ArrayList<>();
            return;
        }
        editorCompatibleAttachments =
                GunHelper.getCompatibleAttachments(List.of(selectedGuns.get(slot)));
    }

    private List<ResourceLocation> activeCatalogue() {
        return switch (activeTab) { case 2 -> allThrowables; default -> allGuns; };
    }

    private List<ResourceLocation> activeSelection() {
        return switch (activeTab) { case 2 -> selectedThrowables; default -> selectedGuns; };
    }

    private int activeMaxPicks() {
        return switch (activeTab) { case 2 -> MAX_THROWABLE_PICKS; default -> MAX_GUN_SLOTS; };
    }

    private String[] activeCategories() { return activeTab == 2 ? THROW_CATS : GUN_CATS; }

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
        return activeTab == 2
                ? GunHelper.getThrowableDisplayName(id)
                : GunHelper.getGunDisplayName(id);
    }

    private String resolveCategory(ResourceLocation id) {
        return activeTab == 0 ? getCategoryLabel(id) : getThrowableCategory(id);
    }

    private List<AttachmentType> getVisibleTypes() {
        Map<AttachmentType, List<ResourceLocation>> byType = buildByType();
        List<AttachmentType> visible = new ArrayList<>();
        for (AttachmentType t : COLUMN_TYPES)
            if (byType.containsKey(t) && !byType.get(t).isEmpty()) visible.add(t);
        return visible;
    }

    private Map<AttachmentType, List<ResourceLocation>> buildByType() {
        Map<AttachmentType, List<ResourceLocation>> byType = new LinkedHashMap<>();
        for (ResourceLocation attId : editorCompatibleAttachments) {
            try {
                ItemStack s = buildAttachmentStack(attId);
                if (s.isEmpty()) continue;
                if (s.getItem() instanceof IAttachment iAtt)
                    byType.computeIfAbsent(iAtt.getType(s), k -> new ArrayList<>()).add(attId);
            } catch (Exception ignored) {}
        }
        return byType;
    }

    private void updateColScrollFromMouse(int ci, double my, int itemAreaY, int listEndY, int total) {
        int sbH = listEndY - itemAreaY;
        int maxScroll = Math.max(1, total - (sbH / 22));
        double ratio = (my - itemAreaY) / sbH;
        colScrollOffsets[ci] = (int) Math.max(0, Math.min(maxScroll, ratio * total));
    }

    /** Returns which filtered-catalogue item is under the mouse in the list, or null. */
    private ResourceLocation computeHoveredItem(int mx, int my) {
        List<ResourceLocation> filtered = filteredCatalogue();
        int listH = VISIBLE_ROWS * ROW_H;
        if (mx < LIST_X || mx >= LIST_X + LIST_W || my < LIST_Y || my >= LIST_Y + listH) return null;
        int i = (my - LIST_Y) / ROW_H;
        int idx = i + scrollOffset;
        return (idx >= 0 && idx < filtered.size()) ? filtered.get(idx) : null;
    }


    // Render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        renderTitleBar(g);
        renderTabs(g, mouseX, mouseY);

        if (activeTab == 3) {
            if (searchBox != null) searchBox.visible = false;
            renderLoadoutsTab(g, mouseX, mouseY);
        } else if (activeTab == 1) {
            if (searchBox != null) searchBox.visible = false;
            if (inAttachmentEditor()) {
                renderAttachmentEditor(g, mouseX, mouseY);
            } else {
                renderAttachmentGunPicker(g, mouseX, mouseY);
            }
        } else {
            if (searchBox != null) searchBox.visible = true;
            hoveredItemId = computeHoveredItem(mouseX, mouseY);
            renderItemList(g, mouseX, mouseY);
            renderScrollbar(g, mouseX, mouseY);
            renderCategories(g, mouseX, mouseY);
            renderDetailPane(g, mouseX, mouseY);
        }

        renderLoadoutPanel(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
        renderHoverTooltip(g, mouseX, mouseY);
        ClientTips.renderInScreen(g, this);
    }

    private void renderHoverTooltip(GuiGraphics g, int mx, int my) {
        ItemStack tooltipStack = ItemStack.EMPTY;

        if (activeTab == 0 || activeTab == 2) {
            // Item list rows
            ResourceLocation hovered = computeHoveredItem(mx, my);
            if (hovered != null) {
                tooltipStack = activeTab == 2 ? buildThrowableStack(hovered) : buildGunStack(hovered);
            }
        }

        if (activeTab == 1) {
            if (inAttachmentEditor()) {
                // Attachment editor rows
                List<AttachmentType> visibleTypes = getVisibleTypes();
                Map<AttachmentType, List<ResourceLocation>> byType = buildByType();
                Map<String, String> equipped = gunAttachments.getOrDefault(attachmentEditorGunSlot, new HashMap<>());
                int numCols = Math.min(visibleTypes.size(), 3);
                if (numCols > 0) {
                    int areaW = panelX() - 16 - LIST_X;
                    int areaH = (this.height - LIST_Y - 40) / ((visibleTypes.size() + 2) / 3);
                    int colW = (areaW - (numCols - 1) * 6) / numCols;
                    int colItemH = 22, colHeaderH = 18;
                    for (int ci = 0; ci < visibleTypes.size(); ci++) {
                        AttachmentType type = visibleTypes.get(ci);
                        int row = ci / 3, col = ci % 3;
                        int colX = LIST_X + col * (colW + 6);
                        int colY = LIST_Y + row * (areaH + 6);
                        if (mx < colX || mx >= colX + colW || my < colY || my >= colY + areaH) continue;
                        int itemAreaY = colY + colHeaderH + 2;
                        String equippedId = equipped.get(type.name());
                        List<ResourceLocation> items = byType.getOrDefault(type, new ArrayList<>());
                        List<ResourceLocation> unequipped = new ArrayList<>();
                        for (ResourceLocation id : items)
                            if (!id.toString().equals(equippedId)) unequipped.add(id);
                        // Check equipped pinned row
                        if (equippedId != null) {
                            if (my >= itemAreaY && my < itemAreaY + colItemH) {
                                try { tooltipStack = buildAttachmentStack(ResourceLocation.parse(equippedId)); } catch (Exception ignored) {}
                                break;
                            }
                            itemAreaY += colItemH + 2;
                        }
                        // Check unequipped rows
                        int innerH = areaH - colHeaderH - 4;
                        int visibleItems = innerH / colItemH;
                        int listEndY = colY + areaH - 2;
                        for (int ri = 0; ri < visibleItems && ri + colScrollOffsets[ci] < unequipped.size(); ri++) {
                            int ry = itemAreaY + ri * colItemH;
                            if (ry + colItemH > listEndY) break;
                            if (my >= ry && my < ry + colItemH) {
                                tooltipStack = buildAttachmentStack(unequipped.get(ri + colScrollOffsets[ci]));
                                break;
                            }
                        }
                        break;
                    }
                }
            } else {
                // Gun picker cards — tooltip on gun icon area
                int cardW = 155, cardH = 110, gap = 8;
                int availW = panelX() - 10 - LIST_X;
                int totalW = MAX_GUN_SLOTS * cardW + (MAX_GUN_SLOTS - 1) * gap;
                int startX = LIST_X + Math.max(0, (availW - totalW) / 2);
                int startY = LIST_Y + 30;
                for (int i = 0; i < MAX_GUN_SLOTS && i < selectedGuns.size(); i++) {
                    int cx = startX + i * (cardW + gap);
                    if (mx >= cx && mx < cx + cardW && my >= startY && my < startY + cardH) {
                        tooltipStack = buildGunStack(selectedGuns.get(i));
                        break;
                    }
                }
            }
        }

        // Loadout panel — weapon slots
        int px = panelX();
        int iy = LIST_Y - 22 + 20 + 6 + 12;
        for (int i = 0; i < selectedGuns.size(); i++) {
            int slotH = 42;
            if (mx >= px + 20 && mx < px + 36 && my >= iy && my < iy + slotH) {
                tooltipStack = buildGunStack(selectedGuns.get(i));
                break;
            }
            iy += slotH + 4;
        }

        if (!tooltipStack.isEmpty()) {
            g.renderTooltip(font, tooltipStack, mx, my);
        }
    }


    // Title bar

    private void renderTitleBar(GuiGraphics g) {
        g.fill(0, 0, this.width, 22, 0xFF0A0A0A);
        g.fill(0, 21, this.width, 22, 0xFFFFAA00);
        if (modifyingLoadoutIndex >= 0 && modifyingLoadoutIndex < clientLoadouts.size()) {
            String modName = clientLoadouts.get(modifyingLoadoutIndex).name;
            g.drawString(font, "MODIFYING  §e" + modName, 10, 6, 0xFF888888);
            String hint = "§7Make changes, then press  §a+ Save Loadout  §7to apply";
            g.drawString(font, hint, this.width - font.width(hint.replaceAll("§.", "")) - 10, 8, 0x555555);
        } else {
            g.drawString(font, "LOADOUT SELECTION", 10, 6, 0xFFAA00);
            String sub = "Loadout · ESC to close";
            g.drawString(font, sub, this.width - font.width(sub) - 10, 8, 0x666666);
        }
    }


    // Tabs

    private void renderTabs(GuiGraphics g, int mx, int my) {
        if (activePhase) {
            int tabY = LIST_Y - TAB_H - 2;
            int tx = LIST_X + 3 * (TAB_W + 2);
            g.fill(tx, tabY, tx + TAB_W, tabY + TAB_H, 0xFF333300);
            g.fill(tx, tabY + TAB_H - 2, tx + TAB_W, tabY + TAB_H, 0xFFFFAA00);
            g.drawCenteredString(font, "Loadouts", tx + TAB_W / 2, tabY + 6, 0xFFFFAA00);
            return;
        }
        String[] labels = {"Weapons", "Attachments", "Throwables", "Loadouts"};
        int tabY = 24;
        for (int t = 0; t < 4; t++) {
            int tx = LIST_X + t * (TAB_W + 2);
            boolean isLoadoutsTab = t == 3;
            boolean locked = modifyingLoadoutIndex >= 0 && isLoadoutsTab;
            boolean active  = activeTab == t;
            boolean hovered = !locked && mx >= tx && mx < tx + TAB_W && my >= tabY && my < tabY + TAB_H;
            g.fill(tx, tabY, tx + TAB_W, tabY + TAB_H,
                    locked ? 0xFF0D0D0D : (active ? 0xFF333333 : (hovered ? 0xFF222222 : 0xFF161616)));
            if (active && !locked) g.fill(tx, tabY + TAB_H - 2, tx + TAB_W, tabY + TAB_H, 0xFFFFAA00);
            g.drawCenteredString(font, locked ? "§8" + labels[t] : ((active ? "§e" : "§7") + labels[t]),
                    tx + TAB_W / 2, tabY + 6, 0xFFFFFF);
        }
    }


    // Item list (Weapons / Throwables)

    private void renderItemList(GuiGraphics g, int mx, int my) {
        List<ResourceLocation> filtered = filteredCatalogue();
        List<ResourceLocation> selection = activeSelection();
        int listH = VISIBLE_ROWS * ROW_H;

        int maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        g.fill(LIST_X - 1, LIST_Y - 1, LIST_X + LIST_W + 1, LIST_Y + listH + 1, 0xFF0E0E0E);
        g.renderOutline(LIST_X - 1, LIST_Y - 1, LIST_W + 2, listH + 2, 0xFF2A2A2A);

        if (filtered.isEmpty()) {
            g.drawCenteredString(font, "§7No items found",
                    LIST_X + LIST_W / 2, LIST_Y + listH / 2 - 4, 0x666666);
            return;
        }

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= filtered.size()) break;

            ResourceLocation id = filtered.get(idx);
            int rowY = LIST_Y + i * ROW_H;
            boolean isSelected = selection.contains(id);
            boolean isFocused  = id.equals(focusedItemId) && !isSelected;
            boolean hovered    = id.equals(hoveredItemId);

            int rowBg = isSelected ? 0xCC1A4A1A
                    : (isFocused  ? 0xCC28200C
                       : (hovered    ? 0xCC252525
                          : (i % 2 == 0 ? 0xCC111111 : 0xCC0E0E0E)));
            g.fill(LIST_X, rowY, LIST_X + LIST_W, rowY + ROW_H, rowBg);

            // Left accent strip
            int accentColor = isSelected ? 0xFF33BB33 : (isFocused ? 0xFFFFAA00 : 0);
            if (accentColor != 0) g.fill(LIST_X, rowY, LIST_X + 3, rowY + ROW_H, accentColor);

            // Icon container
            int catColor = getCategoryColor(resolveCategory(id)) | 0xFF000000;
            g.fill(LIST_X + 3, rowY + 3, LIST_X + 36, rowY + ROW_H - 3, 0xFF0A0A0A);
            g.renderOutline(LIST_X + 3, rowY + 3, 33, ROW_H - 6, catColor);

            ItemStack stack = (activeTab == 2) ? buildThrowableStack(id) : buildGunStack(id);
            boolean iconRendered = false;
            if (activeTab == 2) {
                try {
                    var display = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableDisplay(stack);
                    if (display.isPresent() && display.get().getSlotTexture() != null) {
                        g.blit(display.get().getSlotTexture(),
                                LIST_X + 11, rowY + ROW_H / 2 - 8, 0, 0, 16, 16, 16, 16);
                        iconRendered = true;
                    }
                } catch (Exception ignored) {}
            }
            if (!iconRendered) {
                g.renderItem(stack, LIST_X + 11, rowY + ROW_H / 2 - 8);
            }

            // Name + category
            int textX = LIST_X + 40;
            String dName = displayName(id);
            g.drawString(font, "§f" + dName, textX, rowY + 8, 0xFFFFFF);
            g.drawString(font, resolveCategory(id), textX, rowY + 20, catColor);

            // Slot badge
            if (isSelected) {
                int slot = selection.indexOf(id) + 1;
                String tag = "[" + slot + "]";
                int tagX = LIST_X + LIST_W - font.width(tag) - 5;
                g.fill(tagX - 2, rowY + ROW_H / 2 - 6, tagX + font.width(tag) + 2, rowY + ROW_H / 2 + 4, 0xFF33BB33);
                g.drawString(font, "§f" + tag, tagX, rowY + ROW_H / 2 - 4, 0x0A0A0A);
            }
        }
    }


    // Scrollbar

    private void renderScrollbar(GuiGraphics g, int mx, int my) {
        List<ResourceLocation> filtered = filteredCatalogue();
        int listH = VISIBLE_ROWS * ROW_H;
        if (filtered.size() <= VISIBLE_ROWS) return;
        g.fill(SB_X, LIST_Y, SB_X + SB_W, LIST_Y + listH, 0xFF111111);
        int maxScroll = filtered.size() - VISIBLE_ROWS;
        int thumbH = Math.max(16, listH * VISIBLE_ROWS / filtered.size());
        int thumbY = LIST_Y + (listH - thumbH) * scrollOffset / maxScroll;
        boolean sbHov = mx >= SB_X && mx < SB_X + SB_W && my >= LIST_Y && my < LIST_Y + listH;
        g.fill(SB_X + 1, thumbY, SB_X + SB_W - 1, thumbY + thumbH,
                sbHov || draggingScrollbar ? 0xFFAAAAAA : 0xFF666666);
    }


    // Category filter buttons

    private void renderCategories(GuiGraphics g, int mx, int my) {
        String[] cats = activeCategories();
        g.drawString(font, "§8FILTER", CAT_X, CAT_Y - 10, 0x555555);
        for (int i = 0; i < cats.length; i++) {
            int cy = CAT_Y + i * (CAT_H + CAT_GAP);
            boolean active  = activeCategory == i;
            boolean hovered = mx >= CAT_X && mx < CAT_X + CAT_W && my >= cy && my < cy + CAT_H;
            g.fill(CAT_X, cy, CAT_X + CAT_W, cy + CAT_H,
                    active ? 0xFFFFAA00 : (hovered ? 0xFF2A2A2A : 0xFF1A1A1A));
            if (!active) g.renderOutline(CAT_X, cy, CAT_W, CAT_H, 0xFF2A2A2A);
            int textColor = active ? 0xFFFFFFFF : (hovered ? 0xFFFFFF55 : 0xFF888888);
            g.drawString(font, cats[i], CAT_X + 6, cy + 3, textColor);
        }
    }


    // Detail / Hero pane — center column

    private void renderDetailPane(GuiGraphics g, int mx, int my) {
        int x = detailX(), y = detailY(), w = detailW(), h = detailH();
        boolean isThrowable = (activeTab == 2);
        List<ResourceLocation> catalogue = activeCatalogue();
        List<ResourceLocation> selection = activeSelection();

        // Priority: hover → focus → first selected → first filtered
        ResourceLocation item = null;
        if (hoveredItemId != null) item = hoveredItemId;
        else if (focusedItemId != null && catalogue.contains(focusedItemId)) item = focusedItemId;
        else if (!selection.isEmpty()) item = selection.get(0);
        else { List<ResourceLocation> f = filteredCatalogue(); if (!f.isEmpty()) item = f.get(0); }

        int catColor = (item != null)
                ? (getCategoryColor(isThrowable ? getThrowableCategory(item) : getCategoryLabel(item)) | 0xFF000000)
                : 0xFF444444;

        // Panel background + border
        g.fill(x, y, x + w, y + h, 0xE6141414);
        g.renderOutline(x, y, w, h, catColor);

        if (item == null) {
            renderDetailEmpty(g, x, y, w, h);
            return;
        }

        String name = isThrowable ? GunHelper.getThrowableDisplayName(item) : GunHelper.getGunDisplayName(item);
        String catLabel = isThrowable ? getThrowableCategory(item) : getCategoryLabel(item);
        int slotIdx = selection.indexOf(item);
        boolean equipped = slotIdx >= 0;

        int px = x + 14;
        int py = y + 14;

        // Equipped badge (top-right)
        if (equipped) {
            String badge = "EQUIPPED · SLOT " + (slotIdx + 1);
            int bw = font.width(badge) + 10;
            g.fill(x + w - bw, y, x + w, y + 14, 0xFF33BB33);
            g.drawString(font, badge, x + w - bw + 5, y + 3, 0xFFFFFFFF);
        }

        // Gun name
        g.drawString(font, "§f§l" + shorten(name, 24), px, py, 0xFFFFFF);
        py += 14;
        // Category label
        g.drawString(font, catLabel.toUpperCase(), px, py, catColor);
        py += 14;

        // ---------- Gun renderer area ----------
        int statsH   = isThrowable ? 0 : 68;  // 5 stat lines × 11px + header 10px + margin
        int actionH  = 28;
        int rendererH = h - (py - y) - 8 - statsH - 6 - actionH - 8;
        if (rendererH < 40) rendererH = 40;
        int rw = w - 28;

        g.fill(px, py, px + rw, py + rendererH, 0x661E1E1E);
        g.renderOutline(px, py, rw, rendererH, 0xFF1A1A1A);

        // Corner brackets (10px)
        int bs = 10;
        // TL
        g.fill(px,          py,          px + bs,      py + 1,  catColor);
        g.fill(px,          py,          px + 1,       py + bs, catColor);
        // TR
        g.fill(px + rw - bs, py,         px + rw,      py + 1,  catColor);
        g.fill(px + rw - 1,  py,         px + rw,      py + bs, catColor);
        // BL
        g.fill(px,           py + rendererH - 1, px + bs,     py + rendererH, catColor);
        g.fill(px,           py + rendererH - bs, px + 1,     py + rendererH, catColor);
        // BR
        g.fill(px + rw - bs, py + rendererH - 1, px + rw,    py + rendererH, catColor);
        g.fill(px + rw - 1,  py + rendererH - bs, px + rw,   py + rendererH, catColor);

        // Store preview box bounds for mouse interaction
        previewBoxX = px;
        previewBoxY = py;
        previewBoxW = rw;
        previewBoxH = rendererH;

        // Reset rotation when switching guns
        if (!item.equals(lastPreviewGun)) {
            previewRotX = -10f;
            previewRotY = -120f;
            smoothRotX = -10f;
            smoothRotY = -120f;
            previewScale = 1.0f;
            lastPreviewGun = item;
        }

        // 3D gun model or throwable icon
        if (!isThrowable) {
            renderGun3D(item, px, py, rw, rendererH);
        } else {
            ItemStack stack = buildThrowableStack(item);
            int iconScale = 4;
            int iconPx = iconScale * 16;
            int iconX = px + (rw - iconPx) / 2;
            int iconY = py + (rendererH - iconPx) / 2;
            g.pose().pushPose();
            g.pose().translate(iconX, iconY, 200);
            g.pose().scale(iconScale, iconScale, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }

        // Drag-to-rotate hint (guns only)
        if (!isThrowable) {
            String hint = isDraggingPreview ? "< ROTATING >" : "< DRAG TO ROTATE · SCROLL TO ZOOM >";
            g.drawCenteredString(font, hint, px + rw / 2, py + rendererH - 10,
                    isDraggingPreview ? 0xFFFFAA00 : 0x444444);
        }

        py += rendererH + 6;

        // ---------- Stats (guns only) ----------
        if (!isThrowable) {
            g.drawString(font, "SPECS", px, py, 0x666666);
            py += 10;
            List<Component> stats = GunHelper.getGunStats(item);
            for (int si = 0; si < Math.min(5, stats.size()); si++) {
                g.drawString(font, "§7" + stats.get(si).getString(), px, py, 0x888888);
                py += 11;
            }
            if (stats.isEmpty()) {
                g.drawString(font, "§8No stats available", px, py, 0x444444);
            }
        }

        // ---------- Action bar ----------
        int actionY = y + h - actionH - 6;
        int aw = rw;
        int maxPicks = activeMaxPicks();

        if (equipped) {
            g.fill(px, actionY, px + aw - 36, actionY + 20, 0x991A3C1A);
            g.renderOutline(px, actionY, aw - 36, 20, 0xFF2A6A2A);
            g.drawCenteredString(font, "§aEQUIPPED — RIGHT-CLICK TO REMOVE",
                    px + (aw - 36) / 2, actionY + 6, 0xFF88DD88);
        } else if (selection.size() >= maxPicks) {
            g.fill(px, actionY, px + aw - 36, actionY + 20, 0xFF3A1111);
            g.renderOutline(px, actionY, aw - 36, 20, 0xFF663333);
            g.drawCenteredString(font, "§cLoadout full — remove one first",
                    px + (aw - 36) / 2, actionY + 6, 0xFFFF5555);
        } else {
            boolean btnHov = mx >= px && mx < px + aw - 36 && my >= actionY && my < actionY + 20;
            g.fill(px, actionY, px + aw - 36, actionY + 20, btnHov ? 0xFF2A5A2A : 0xFF1A3A1A);
            g.renderOutline(px, actionY, aw - 36, 20, btnHov ? 0xFF55BB55 : 0xFF33BB33);
            g.drawCenteredString(font, "§a+  ADD TO LOADOUT",
                    px + (aw - 36) / 2, actionY + 6, 0xFF55FF55);
        }
        String cntStr = selection.size() + "/" + maxPicks;
        g.drawString(font, "§8" + cntStr, px + aw - 32, actionY + 7, 0x555555);
    }

    private void renderGun3D(ResourceLocation gunId, int bx, int by, int bw, int bh) {
        ItemStack stack = buildGunStack(gunId);
        Minecraft mc = Minecraft.getInstance();

        // Get TACZ display — fall back to flat icon if unavailable
        java.util.Optional<GunDisplayInstance> displayOpt = TimelessAPI.getGunDisplay(stack);
        if (displayOpt.isEmpty()) {
            // Fallback: render flat icon centred
            int iconPx = 64;
            int iconX = bx + (bw - iconPx) / 2;
            int iconY = by + (bh - iconPx) / 2;
            // Can't use g here, use renderItem via a temporary pose
            return;
        }
        GunDisplayInstance display = displayOpt.get();
        BedrockGunModel gunModel = display.getGunModel();
        ResourceLocation texture = display.getModelTexture();
        if (gunModel == null || texture == null) return;

        // Scissoring method gives me the hahas to the preview box so the model doesnt bleed outside
        double scale = mc.getWindow().getGuiScale();
        int scissorX = (int) (bx * scale);
        int scissorY = (int) (mc.getWindow().getHeight() - (by + bh) * scale);
        int scissorW = (int) (bw * scale);
        int scissorH = (int) (bh * scale);
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);

//        // Set up 3D item lighting
//        RenderSystem.setShaderLights(
//                new org.joml.Vector3f(-0.4f, -0.4f, -0.8f),
//                new org.joml.Vector3f(0.4f, 0.4f, 0.2f)
//        );

        // Build PoseStack for the render
        PoseStack poseStack = new PoseStack();

        // Translate to center of preview box in screen space
        // GUI coordinates: origin top-left, Z goes into screen
        float cx = bx + bw / 2f;
        float cy = by + bh / 2f;
        poseStack.translate(cx, cy, 200f);

        // Scale
        float baseScale = (bh * 0.35f) * previewScale;
        // scale(-1, 1, 1): flips X — fixes winding order (inside-out) AND one axis flip
        // then ZP 180° rotation handles the upside-down
        poseStack.scale(-baseScale, -baseScale, -baseScale);
        poseStack.mulPose(Axis.XP.rotationDegrees(180f));

        // Smooth rotation
        float lerpSpeed = 0.2f;
        smoothRotX += (previewRotX - smoothRotX) * lerpSpeed;
        smoothRotY += (previewRotY - smoothRotY) * lerpSpeed;
        poseStack.mulPose(Axis.XP.rotationDegrees(smoothRotX));
        poseStack.mulPose(Axis.YP.rotationDegrees(smoothRotY));

        // Use model root bone position as rotation pivot
        com.tacz.guns.client.model.bedrock.BedrockPart root = gunModel.getRootNode();
        float pivotX = root != null ? -root.x / 16f : 0f;
        float pivotY = root != null ? -root.y / 16f : 0f;
        float pivotZ = root != null ? -root.z / 16f : 0f;
        poseStack.translate(pivotX, pivotY, pivotZ);

        // Render
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        int fullbright = LightTexture.pack(15, 15);
        RenderType renderType = RenderType.entityCutout(texture);
        try {
            gunModel.render(poseStack, stack, ItemDisplayContext.FIXED, renderType,
                    fullbright, OverlayTexture.NO_OVERLAY);
            bufferSource.endBatch(renderType);
            gunModel.cleanAnimationTransform();
        } finally {
            RenderSystem.disableScissor();
        }
    }

    private void renderDetailEmpty(GuiGraphics g, int x, int y, int w, int h) {
        int cx = x + w / 2, cy = y + h / 2;
        int boxS = 64;
        g.fill(cx - boxS / 2, cy - 56, cx + boxS / 2, cy - 56 + boxS, 0xFF0A0A0A);
        g.renderOutline(cx - boxS / 2, cy - 56, boxS, boxS, 0xFF1A1A1A);
        g.drawCenteredString(font, "§8?", cx, cy - 56 + boxS / 2 - 4, 0x222222);
        g.drawCenteredString(font, "§6§lGEAR UP, SOLDIER", cx, cy - 2, 0xFFAA00);
        g.drawCenteredString(font, "§7Hover an item to inspect it.", cx, cy + 14, 0x888888);
        g.drawCenteredString(font, "§7Click to add it to your loadout.", cx, cy + 26, 0x888888);
        g.drawCenteredString(font, "§eLMB §7add  ·  §eRMB §7remove  ·  §eWheel §7scroll",
                cx, cy + 42, 0x555555);
    }


    // Attachment gun picker (Tab 1, state 0)

    private void renderAttachmentGunPicker(GuiGraphics g, int mx, int my) {
        int cardW = 155, cardH = 110, gap = 8;
        int totalW = MAX_GUN_SLOTS * cardW + (MAX_GUN_SLOTS - 1) * gap;
        int availW = panelX() - 10 - LIST_X;
        int startX = LIST_X + Math.max(0, (availW - totalW) / 2);
        int startY = LIST_Y + 30;

        g.drawString(font, "§6§lMODIFY ATTACHMENTS", LIST_X, 50, 0xFFAA00);
        g.drawString(font, "§7Pick a weapon slot to customize.", LIST_X, 66, 0x888888);

        for (int i = 0; i < MAX_GUN_SLOTS; i++) {
            int cx = startX + i * (cardW + gap);
            boolean hasGun = i < selectedGuns.size();
            boolean hovered = hasGun && mx >= cx && mx < cx + cardW
                    && my >= startY && my < startY + cardH;

            int bgColor = hasGun ? (hovered ? 0xCC253525 : 0xCC1A2A1A) : 0xCC111111;
            int bdColor = hasGun ? (hovered ? 0xFF55BB55 : (getCategoryColor(
                    getCategoryLabel(hasGun ? selectedGuns.get(i) : null)) | 0xFF000000)) : 0xFF2A2A2A;
            if (!hasGun) bdColor = 0xFF2A2A2A;

            g.fill(cx, startY, cx + cardW, startY + cardH, bgColor);
            g.renderOutline(cx, startY, cardW, cardH, bdColor);

            // Slot ribbon
            int ribbonColor = hasGun ? bdColor : 0xFF1A1A1A;
            g.fill(cx, startY, cx + cardW, startY + 14, ribbonColor);
            g.drawString(font, "SLOT " + (i + 1), cx + 8, startY + 3,
                    hasGun ? 0xFFFFFFFF : 0xFF444444);
            if (hasGun) {
                String cat = getCategoryLabel(selectedGuns.get(i));
                g.drawString(font, cat.toUpperCase(), cx + cardW - font.width(cat) - 8, startY + 3, 0xFFFFFFFF);
            }

            if (hasGun) {
                ResourceLocation gunId = selectedGuns.get(i);
                // Gun icon 2x
                g.pose().pushPose();
                g.pose().translate(cx + 10, startY + 20, 0);
                g.pose().scale(2f, 2f, 1f);
                g.renderItem(buildGunStack(gunId), 0, 0);
                g.pose().popPose();
                // Gun name
                g.drawString(font, "§f§l" + shorten(GunHelper.getGunDisplayName(gunId), 15),
                        cx + 44, startY + 22, 0xFFFFFF);
                g.drawString(font, "§8" + getCategoryLabel(gunId).toUpperCase(),
                        cx + 44, startY + 33, 0x555555);
                // Divider
                g.fill(cx + 6, startY + 50, cx + cardW - 6, startY + 51, 0xFF222222);
                // Attachments
                Map<String, String> equipped = gunAttachments.getOrDefault(i, new HashMap<>());
                g.drawString(font, "ATTACHMENTS", cx + 8, startY + 56, 0xFF666666);
                g.drawString(font, equipped.size() + "/6",
                        cx + cardW - font.width(equipped.size() + "/6") - 8, startY + 56, 0xFF888888);
                int ix = cx + 8, iy = startY + 68;
                for (var e : equipped.entrySet()) {
                    try {
                        ItemStack s = buildAttachmentStack(ResourceLocation.parse(e.getValue()));
                        if (!s.isEmpty()) { g.renderItem(s, ix, iy); ix += 18; }
                    } catch (Exception ignored) {}
                }
                if (equipped.isEmpty())
                    g.drawString(font, "§8none equipped", cx + 8, startY + 72, 0x444444);
                // Bottom CTA
                int btnY = startY + cardH - 16;
                g.fill(cx + 4, btnY, cx + cardW - 4, startY + cardH - 2,
                        hovered ? 0xFFFFAA00 : 0xFF1A1A1A);
                g.renderOutline(cx + 4, btnY, cardW - 8, 14,
                        hovered ? 0xFFFFAA00 : 0xFF333333);
                g.drawCenteredString(font, hovered ? "§f CLICK TO MODIFY" : "§eCONFIGURE",
                        cx + cardW / 2, btnY + 3, hovered ? 0xFFFFFFFF : 0xFFAA00);
            } else {
                g.drawCenteredString(font, "§8— Empty —", cx + cardW / 2,
                        startY + cardH / 2 - 4, 0x333333);
                g.drawCenteredString(font, "§8Pick in Weapons tab", cx + cardW / 2,
                        startY + cardH / 2 + 8, 0x222222);
            }
        }

        g.drawString(font, "§8Empty slots: go to Weapons tab first.", LIST_X, this.height - 10, 0x444444);
    }


    // Attachment editor (Tab 1, state 1)

    private void renderAttachmentEditor(GuiGraphics g, int mx, int my) {
        ResourceLocation gunId = selectedGuns.get(attachmentEditorGunSlot);
        Map<String, String> equipped = gunAttachments.getOrDefault(attachmentEditorGunSlot, new HashMap<>());

        List<AttachmentType> visibleTypes = new ArrayList<>();
        Map<AttachmentType, List<ResourceLocation>> byType = new LinkedHashMap<>();
        for (ResourceLocation attId : editorCompatibleAttachments) {
            try {
                ItemStack s = buildAttachmentStack(attId);
                if (s.isEmpty()) continue;
                if (s.getItem() instanceof IAttachment iAtt) {
                    AttachmentType t = iAtt.getType(s);
                    byType.computeIfAbsent(t, k -> new ArrayList<>()).add(attId);
                }
            } catch (Exception ignored) {}
        }
        for (AttachmentType t : COLUMN_TYPES)
            if (byType.containsKey(t) && !byType.get(t).isEmpty()) visibleTypes.add(t);

        int numCols = Math.min(visibleTypes.size(), 3);
        int numRows = (visibleTypes.size() + 2) / 3;

        if (numCols == 0) {
            g.drawCenteredString(font, "§7No attachments available for this gun.",
                    LIST_X + (panelX() - 16 - LIST_X) / 2, LIST_Y + 40, 0xFFFFFF);
        }

        int areaW = panelX() - 16 - LIST_X;
        int areaH = (this.height - LIST_Y - 40) / Math.max(1, numRows);
        int colW  = numCols > 0 ? (areaW - (numCols - 1) * 6) / numCols : areaW;
        int colItemH = 22, colHeaderH = 18;

        // Back button + gun header
        int backY = this.height - 28;
        boolean backHov = mx >= LIST_X && mx < LIST_X + 70 && my >= backY && my < backY + 18;
        g.fill(LIST_X, backY, LIST_X + 70, backY + 18, backHov ? 0xFF333333 : 0xFF1A1A1A);
        g.renderOutline(LIST_X, backY, 70, 18, backHov ? 0xFFFFAA00 : 0xFF444444);
        if (backHov) g.fill(LIST_X, backY + 16, LIST_X + 70, backY + 18, 0xFFFFAA00);
        g.drawCenteredString(font, backHov ? "§e← Back" : "§7← Back", LIST_X + 35, backY + 5, 0xFFFFFF);

        g.renderItem(buildGunStack(gunId), LIST_X + 74, backY);
        g.drawString(font, "§e" + GunHelper.getGunDisplayName(gunId) + "  §8—  Attachments",
                LIST_X + 94, backY + 5, 0xFFFFFF);

        // Attachment columns
        for (int ci = 0; ci < visibleTypes.size(); ci++) {
            AttachmentType type = visibleTypes.get(ci);
            int row = ci / 3, col = ci % 3;
            int colX = LIST_X + col * (colW + 6);
            int colY = LIST_Y + row * (areaH + 6);
            int innerH = areaH - colHeaderH - 4;
            int visibleItems = innerH / colItemH;

            String equippedId = equipped.get(type.name());
            List<ResourceLocation> items = byType.getOrDefault(type, new ArrayList<>());
            List<ResourceLocation> unequipped = new ArrayList<>();
            for (ResourceLocation id : items)
                if (!id.toString().equals(equippedId)) unequipped.add(id);

            g.fill(colX, colY, colX + colW, colY + areaH, 0xFF0E0E0E);
            g.renderOutline(colX, colY, colW, areaH, 0xFF2A2A2A);

            // Column header
            boolean hasEquipped = equippedId != null;
            g.fill(colX, colY, colX + colW, colY + colHeaderH,
                    hasEquipped ? 0xFF1A2A1A : 0xFF1A1A1A);
            g.fill(colX, colY, colX + 3, colY + colHeaderH,
                    hasEquipped ? 0xFF33BB33 : 0xFFFFAA00);
            String label = COLUMN_LABELS[indexOf(COLUMN_TYPES, type)];
            g.drawString(font, (hasEquipped ? "§a" : "§e") + label.toUpperCase(),
                    colX + 8, colY + 5, 0xFFFFFF);
            if (hasEquipped)
                g.drawString(font, "§a✔", colX + colW - 12, colY + 5, 0x44FF44);
            else {
                String countStr = items.size() + "";
                g.drawString(font, "§8" + countStr,
                        colX + colW - font.width(countStr) - 6, colY + 5, 0x555555);
            }

            int itemAreaY = colY + colHeaderH + 2;

            // Pinned equipped row
            if (equippedId != null) {
                try {
                    ResourceLocation eId = ResourceLocation.parse(equippedId);
                    ItemStack eStack = buildAttachmentStack(eId);
                    boolean eHov = mx >= colX && mx < colX + colW
                            && my >= itemAreaY && my < itemAreaY + colItemH;
                    g.fill(colX, itemAreaY, colX + colW, itemAreaY + colItemH, 0xCC1A4A1A);
                    g.renderOutline(colX, itemAreaY, colW, colItemH, 0xFF33BB33);
                    if (!eStack.isEmpty()) g.renderItem(eStack, colX + 2, itemAreaY + 3);
                    g.drawString(font, "§a" + shorten(GunHelper.getAttachmentDisplayName(eId), 9),
                            colX + 20, itemAreaY + 7, 0xAAFFAA);
                    int rbX = colX + colW - 24;
                    boolean rbHov = mx >= rbX && mx < rbX + 22
                            && my >= itemAreaY + 3 && my < itemAreaY + colItemH - 3;
                    g.fill(rbX, itemAreaY + 3, rbX + 22, itemAreaY + colItemH - 3,
                            rbHov ? 0xFFAA2222 : 0xFF661111);
                    g.drawCenteredString(font, "§c✕", rbX + 11, itemAreaY + 6, 0xFF5555);
                } catch (Exception ignored) {}
                itemAreaY += colItemH + 2;
                g.fill(colX + 4, itemAreaY - 1, colX + colW - 4, itemAreaY, 0xFF333333);
            }

            // Scrollable list
            int maxScroll = Math.max(0, unequipped.size() - visibleItems);
            colScrollOffsets[ci] = Math.max(0, Math.min(colScrollOffsets[ci], maxScroll));
            int listEndY = colY + areaH - 2;

            for (int ri = 0; ri < visibleItems && ri + colScrollOffsets[ci] < unequipped.size(); ri++) {
                ResourceLocation id = unequipped.get(ri + colScrollOffsets[ci]);
                int ry = itemAreaY + ri * colItemH;
                if (ry + colItemH > listEndY) break;
                boolean rowHov = mx >= colX && mx < colX + colW && my >= ry && my < ry + colItemH;
                g.fill(colX, ry, colX + colW, ry + colItemH,
                        rowHov ? 0xCC252525 : (ri % 2 == 0 ? 0xCC111111 : 0xCC0E0E0E));
                ItemStack s = buildAttachmentStack(id);
                if (!s.isEmpty()) g.renderItem(s, colX + 2, ry + 3);
                g.drawString(font, "§f" + shorten(GunHelper.getAttachmentDisplayName(id), 9),
                        colX + 20, ry + 7, 0xFFFFFF);
                if (rowHov)
                    g.drawString(font, "§a+", colX + colW - 10, ry + 7, 0x55FF55);
            }

            // Column scrollbar
            if (unequipped.size() > visibleItems) {
                int sbX = colX + colW - 5;
                int sbH = listEndY - itemAreaY;
                g.fill(sbX, itemAreaY, sbX + 4, listEndY, 0xFF1A1A1A);
                int thumbH = Math.max(10, sbH * visibleItems / unequipped.size());
                int thumbY = itemAreaY + (sbH - thumbH) * colScrollOffsets[ci] / Math.max(1, maxScroll);
                boolean sbHov = mx >= sbX && mx < sbX + 4 && my >= itemAreaY && my < listEndY;
                g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH,
                        sbHov || draggingColumnIndex == ci ? 0xFFAAAAAA : 0xFF555555);
            }
        }
    }

    private int indexOf(AttachmentType[] arr, AttachmentType t) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == t) return i;
        return 0;
    }


    // Right loadout panel persistent

    private void renderLoadoutPanel(GuiGraphics g, int mx, int my) {
        int px = panelX();
        int py = LIST_Y - 22;  // 54
        int pw = PANEL_W;

        // Gold header
        g.fill(px, py, px + pw, py + 20, 0xFFFFAA00);
        g.drawString(font, "YOUR LOADOUT", px + 8, py + 6, 0xFFFFFFFF);
        String cnt = selectedGuns.size() + "/" + MAX_GUN_SLOTS;
        g.drawString(font, cnt, px + pw - font.width(cnt) - 8, py + 7, 0x80FFFFFF);
        py += 20;

        // Weapons section background
        int weapH = 6 + 12 + MAX_GUN_SLOTS * 46 + (MAX_GUN_SLOTS > 0 ? (MAX_GUN_SLOTS - 1) * 4 : 0) + 6;
        g.fill(px, py, px + pw, py + weapH, 0xD60A0A0A);
        g.renderOutline(px, py, pw, weapH, 0xFF2A2A2A);

        int iy = py + 6;
        g.drawString(font, "WEAPONS", px + 6, iy, 0xFFFF55);
        iy += 12;

        for (int i = 0; i < MAX_GUN_SLOTS; i++) {
            boolean hasGun = i < selectedGuns.size();
            ResourceLocation gunId = hasGun ? selectedGuns.get(i) : null;
            int catColor = (gunId != null)
                    ? (getCategoryColor(getCategoryLabel(gunId)) | 0xFF000000) : 0xFF2A2A2A;

            int slotH = hasGun ? 42 : 28;

            g.fill(px + 4, iy, px + pw - 4, iy + slotH,
                    hasGun ? 0x9914201 : 0xFF0A0A0A);
            // fix: 0x99141E14 (alpha=99, dark green)
            g.fill(px + 4, iy, px + pw - 4, iy + slotH,
                    hasGun ? 0x99141E14 : 0xFF0A0A0A);
            g.renderOutline(px + 4, iy, pw - 8, slotH,
                    hasGun ? catColor : 0xFF222222);

            // Slot number
            g.drawString(font, "§8" + (i + 1), px + 8, iy + (slotH / 2) - 4, 0x666666);

            if (hasGun) {
                g.renderItem(buildGunStack(gunId), px + 20, iy + (slotH / 2) - 8);
                g.drawString(font, "§f" + shorten(GunHelper.getGunDisplayName(gunId), 11),
                        px + 38, iy + (slotH / 2) - 9, 0xFFFFFF);

                // Attachment icons
                Map<String, String> atts = gunAttachments.getOrDefault(i, new HashMap<>());
                if (!atts.isEmpty()) {
                    int ax = px + 38, ay = iy + (slotH / 2) + 2;
                    int shown = 0;
                    for (var e : atts.entrySet()) {
                        if (shown >= 4) break;
                        try {
                            ItemStack s = buildAttachmentStack(ResourceLocation.parse(e.getValue()));
                            if (!s.isEmpty()) { g.renderItem(s, ax, ay); ax += 14; shown++; }
                        } catch (Exception ignored) {}
                    }
                } else {
                    g.drawString(font, "§8no attachments", px + 38, iy + (slotH / 2) + 4, 0x444444);
                }

            } else {
                g.drawCenteredString(font, "§8— empty slot —",
                        px + pw / 2, iy + slotH / 2 - 4, 0x333333);
            }
            iy += slotH + 4;
        }

        py += weapH + 4;

        // Throwables section
        int thrSlotSize = 30, thrGap = 3;
        int thrGridW = MAX_THROWABLE_PICKS * thrSlotSize + (MAX_THROWABLE_PICKS - 1) * thrGap;
        int thrSectionH = 6 + 14 + thrSlotSize + 8;
        g.fill(px, py, px + pw, py + thrSectionH, 0xD60A0A0A);
        g.renderOutline(px, py, pw, thrSectionH, 0xFF2A2A2A);

        int ty = py + 6;
        g.drawString(font, "THROWABLES", px + 6, ty, 0xFFFF5555);
        String thrCnt = selectedThrowables.size() + "/" + MAX_THROWABLE_PICKS;
        g.drawString(font, thrCnt, px + pw - font.width(thrCnt) - 8, ty, 0x666666);
        ty += 14;

        int startThrX = px + (pw - thrGridW) / 2;
        for (int i = 0; i < MAX_THROWABLE_PICKS; i++) {
            int sx = startThrX + i * (thrSlotSize + thrGap);
            boolean hasThr = i < selectedThrowables.size();
            ResourceLocation thrId = hasThr ? selectedThrowables.get(i) : null;

            g.fill(sx, ty, sx + thrSlotSize, ty + thrSlotSize, 0xFF0A0A0A);
            g.renderOutline(sx, ty, thrSlotSize, thrSlotSize,
                    hasThr ? 0xFF663333 : 0xFF222222);

            if (thrId != null) {
                ItemStack ts = buildThrowableStack(thrId);
                boolean rendered = false;
                try {
                    var disp = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableDisplay(ts);
                    if (disp.isPresent() && disp.get().getSlotTexture() != null) {
                        int ox = sx + (thrSlotSize - 16) / 2;
                        int oy = ty + (thrSlotSize - 16) / 2;
                        g.blit(disp.get().getSlotTexture(), ox, oy, 0, 0, 16, 16, 16, 16);
                        rendered = true;
                    }
                } catch (Exception ignored) {}
                if (!rendered)
                    g.renderItem(ts, sx + (thrSlotSize - 16) / 2, ty + (thrSlotSize - 16) / 2);
            } else {
                g.drawCenteredString(font, "·", sx + thrSlotSize / 2, ty + thrSlotSize / 2 - 3, 0x333333);
            }
        }
        if (modifyingLoadoutIndex >= 0 && modifyingLoadoutIndex < clientLoadouts.size()) {
            int barY = py + thrSectionH + 4;
            boolean saveHov = mx >= px && mx < px + pw - 54 && my >= barY && my < barY + 20;
            g.fill(px, barY, px + pw - 54, barY + 20, saveHov ? 0xFF1A4400 : 0xFF0F2800);
            g.renderOutline(px, barY, pw - 54, 20, saveHov ? 0xFF88FF00 : 0xFF446600);
            g.drawCenteredString(font, "§a+ Save Loadout", px + (pw - 54) / 2, barY + 6, 0xFFFFFF);
            int cancelX = px + pw - 52;
            boolean cancelHov = mx >= cancelX && mx < cancelX + 52 && my >= barY && my < barY + 20;
            g.fill(cancelX, barY, cancelX + 52, barY + 20, cancelHov ? 0xFF3A1111 : 0xFF1A0A0A);
            g.renderOutline(cancelX, barY, 52,20, cancelHov ? 0xFFBB3333 : 0xFF552222);
            g.drawCenteredString(font, "§cCancel", cancelX + 26, barY + 6, 0xFFFFFF);
        }
    }


    // Loadouts tab

    private void renderLoadoutsTab(GuiGraphics g, int mx, int my) {
        int px = panelX();
        int contentW = px - LIST_X - 10;
        int x = LIST_X, y = LIST_Y;
        int cardH = 64, cardGap = 6;

        // Delete confirmation overlay
        if (confirmDeleteIndex >= 0) {
            String name = confirmDeleteIndex < clientLoadouts.size()
                    ? clientLoadouts.get(confirmDeleteIndex).name : "this loadout";
            int ovW = 300, ovH = 90;
            int ovX = this.width / 2 - ovW / 2, ovY = this.height / 2 - ovH / 2;
            g.fill(0, 0, this.width, this.height, 0xAA000000);
            g.fill(ovX, ovY, ovX + ovW, ovY + ovH, 0xFF0E0E0E);
            g.renderOutline(ovX, ovY, ovW, ovH, 0xFFFF4444);
            g.drawCenteredString(font, "§cDELETE LOADOUT", ovX + ovW / 2, ovY + 10, 0xFFFFFF);
            g.drawCenteredString(font, "§f\"" + name + "\"", ovX + ovW / 2, ovY + 24, 0xFFFFFF);
            g.drawCenteredString(font, "§7This cannot be undone.", ovX + ovW / 2, ovY + 38, 0x888888);
            // Confirm
            int cbX = ovX + ovW / 2 - 75, cbY = ovY + ovH - 28;
            boolean confHov = mx >= cbX && mx < cbX + 65 && my >= cbY && my < cbY + 22;
            g.fill(cbX, cbY, cbX + 65, cbY + 22, confHov ? 0xFF882222 : 0xFF551111);
            g.renderOutline(cbX, cbY, 65, 22, 0xFFBB3333);
            g.drawCenteredString(font, "§cDelete", cbX + 32, cbY + 7, 0xFFFFFF);
            // Cancel
            int ccX = ovX + ovW / 2 + 10, ccY = cbY;
            boolean cancHov = mx >= ccX && mx < ccX + 65 && my >= ccY && my < ccY + 22;
            g.fill(ccX, ccY, ccX + 65, ccY + 22, cancHov ? 0xFF333333 : 0xFF1A1A1A);
            g.renderOutline(ccX, ccY, 65, 22, 0xFF444444);
            g.drawCenteredString(font, "§7Cancel", ccX + 32, ccY + 7, 0xCCCCCC);
            return;
        }

        // Rename input
        if (namingLoadoutIndex >= 0) {
            g.fill(x, y, x + contentW, y + 50, 0xFF0E0E0E);
            g.renderOutline(x, y, contentW, 50, 0xFFFFAA00);
            g.drawString(font, "§eLOADOUT NAME", x + 6, y + 4, 0xFFAA00);
            g.drawString(font, "§f" + loadoutNameBuffer + "§e|", x + 6, y + 18, 0xFFFFFF);
            // Hint row — moved 8 px down relative to the original, now sits inside the 50px box
            g.drawCenteredString(font, "§eEnter §7save  ·  §eEsc §7cancel",
                    x + contentW / 2, y + 38, 0x555555);
            return;
        }

        // Header
        g.drawString(font, "§6§lSAVED LOADOUTS", x, 50, 0xFFAA00);
        g.drawString(font, "§8" + clientLoadouts.size() + "/8", x + 140, 52, 0x666666);

        // Empty state
        if (clientLoadouts.isEmpty()) {
            g.drawCenteredString(font, "§7No saved loadouts yet.", x + contentW / 2, y + 30, 0xAAAAAA);
            g.drawCenteredString(font, "§7Build one in Weapons / Throwables / Attachments,",
                    x + contentW / 2, y + 44, 0x666666);
            g.drawCenteredString(font, "§7then save below.",
                    x + contentW / 2, y + 56, 0x666666);
        }

        // Loadout cards
        for (int i = 0; i < clientLoadouts.size(); i++) {
            var loadout = clientLoadouts.get(i);
            int sy = y + i * (cardH + cardGap);
            boolean isSelected = selectedLoadoutIndex == i;
            boolean cardHov = mx >= x && mx < x + contentW && my >= sy && my < sy + cardH;

            // Card background + border
            int bgColor = isSelected ? 0xFF141E14 : (cardHov ? 0xFF121212 : 0xFF0E0E0E);
            int bdColor = isSelected ? 0xFF55BB55 : (cardHov ? 0xFF333333 : 0xFF2A2A2A);
            g.fill(x, sy, x + contentW, sy + cardH, bgColor);
            g.renderOutline(x, sy, contentW, cardH, bdColor);

            // Left accent — gold normally, green when selected
            int accentColor = isSelected ? 0xFF33BB33 : 0xFFFFAA00;
            g.fill(x, sy, x + 4, sy + cardH, accentColor);

            // Active indicator badge
            if (isSelected) {
                String badge = "ACTIVE";
                int bw = font.width(badge) + 8;
                g.fill(x + contentW - bw, sy, x + contentW, sy + 14, 0xFF33BB33);
                g.drawString(font, "§f" + badge, x + contentW - bw + 4, sy + 3, 0xFFFFFF);
            }

            // Name
            g.drawString(font, (isSelected ? "§a§l" : "§f§l") + loadout.name, x + 12, sy + 6, 0xFFFFFF);
            g.drawString(font, "§8" + loadout.guns.size() + " guns - " + loadout.throwables.size() + " throwables",
                    x + 12 + font.width(loadout.name) + 10, sy + 6, 0x555555);

            // Gun icons + names
            int gx = x + 12, gy = sy + 20;
            for (String gunStr : loadout.guns) {
                try {
                    ResourceLocation gid = ResourceLocation.parse(gunStr);
                    ItemStack gs = buildGunStack(gid);
                    if (!gs.isEmpty()) {
                        g.renderItem(gs, gx, gy);
                        String gname = shorten(GunHelper.getGunDisplayName(gid), 8);
                        g.drawString(font, "§7" + gname, gx + 18, gy + 4, 0x999999);
                        gx += 18 + font.width(gname) + 10;
                    }
                } catch (Exception ignored) {}
            }

            // Throwable icons — second row, below gun names
            int thrRowY = sy + 40;
            int thrX = x + 12;
            for (int ti = 0; ti < loadout.throwables.size(); ti++) {
                try {
                    ResourceLocation tid = ResourceLocation.parse(loadout.throwables.get(ti));
                    ItemStack ts = buildThrowableStack(tid);
                    if (!ts.isEmpty()) {
                        boolean rendered = false;
                        try {
                            var disp = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableDisplay(ts);
                            if (disp.isPresent() && disp.get().getSlotTexture() != null) {
                                g.blit(disp.get().getSlotTexture(), thrX, thrRowY, 0, 0, 16, 16, 16, 16);
                                rendered = true;
                            }
                        } catch (Exception ignored2) {}
                        if (!rendered) g.renderItem(ts, thrX, thrRowY);
                        thrX += 18;
                    }
                } catch (Exception ignored) {}
            }

            // Modify + Rename + Delete buttons (right side)
            int btnAreaX = x + contentW - 184;
            int btnY = sy + (cardH - 18) / 2;
            // Modify
            boolean modHov = mx >= btnAreaX && mx < btnAreaX + 52 && my >= btnY && my < btnY + 18;
            boolean isModifying = modifyingLoadoutIndex == i;
            g.fill(btnAreaX, btnY, btnAreaX + 52, btnY + 18,
                    isModifying ? 0xFF2A5500 : (modHov ? 0xFF3A4400 : 0xFF1A2200));
            g.renderOutline(btnAreaX, btnY, 52, 18,
                    isModifying ? 0xFF88FF00 : (modHov ? 0xFF99BB00 : 0xFF556600));
            g.drawCenteredString(font, isModifying ? "§a✏ Active" : "§eModify",
                    btnAreaX + 26, btnY + 5, 0xFFFFFF);
            // Rename
            int renX = btnAreaX + 58;
            boolean renHov = mx >= renX && mx < renX + 58 && my >= btnY && my < btnY + 18;
            g.fill(renX, btnY, renX + 58, btnY + 18, renHov ? 0xFF223355 : 0xFF111A2A);
            g.renderOutline(renX, btnY, 58, 18, renHov ? 0xFF4488BB : 0xFF224466);
            g.drawCenteredString(font, "§bRename", renX + 29, btnY + 5, 0xFFFFFF);
            // Delete
            int delX = renX + 64;
            boolean delHov = mx >= delX && mx < delX + 52 && my >= btnY && my < btnY + 18;
            g.fill(delX, btnY, delX + 52, btnY + 18, delHov ? 0xFF5A1A1A : 0xFF3A1111);
            g.renderOutline(delX, btnY, 52, 18, delHov ? 0xFFBB4444 : 0xFF663333);
            g.drawCenteredString(font, "§cDelete", delX + 26, btnY + 5, 0xFFFFFF);
        }

        if (modifyingLoadoutIndex >= 0) {
            int saveY = y + clientLoadouts.size() * (cardH + cardGap) + 6;
            int saveW = contentW - 70;
            boolean saveHov = mx >= x && mx < x + saveW && my >= saveY && my < saveY + 22;
            g.fill(x, saveY, x + saveW, saveY + 22, saveHov ? 0xFF1A4400 : 0xFF0F2800);
            g.renderOutline(x, saveY, saveW, 22, saveHov ? 0xFF88FF00 : 0xFF446600);
            String saveLbl = modifyingLoadoutIndex < clientLoadouts.size()
                    ? "§a+  Save Loadout  \"" + clientLoadouts.get(modifyingLoadoutIndex).name + "\""
                    : "§a+  Save Loadout";
            g.drawCenteredString(font, saveLbl, x + saveW / 2, saveY + 7, 0xFFFFFF);
            // Cancel button
            int cancelX = x + saveW + 4;
            int cancelW = contentW - saveW - 4;
            boolean cancelHov = mx >= cancelX && mx < cancelX + cancelW && my >= saveY && my < saveY + 22;
            g.fill(cancelX, saveY, cancelX + cancelW, saveY + 22, cancelHov ? 0xFF3A1111 : 0xFF1A0A0A);
            g.renderOutline(cancelX, saveY, cancelW, 22, cancelHov ? 0xFFBB3333 : 0xFF552222);
            g.drawCenteredString(font, "§cCancel", cancelX + cancelW / 2, saveY + 7, 0xFFFFFF);
        } else if (activePhase) {
            int noticeY = y + clientLoadouts.size() * (cardH + cardGap) + 6;
            g.drawCenteredString(font, "§7Changes take effect on next respawn.",
                    x + contentW / 2, noticeY + 7, 0x888888);
        } else if (clientLoadouts.size() < com.frosty.bedgunwars.game.LoadoutManager.MAX_LOADOUTS) {
            int saveY = y + clientLoadouts.size() * (cardH + cardGap) + 6;
            boolean saveHov = mx >= x && mx < x + contentW && my >= saveY && my < saveY + 22;
            g.fill(x, saveY, x + contentW, saveY + 22,
                    saveHov ? 0xFF3A3A00 : 0xFF252500);
            g.renderOutline(x, saveY, contentW, 22,
                    saveHov ? 0xFFFFDD00 : 0xFF666600);
            g.drawCenteredString(font, "§e+  SAVE CURRENT LOADOUT",
                    x + contentW / 2, saveY + 7, 0xFFFFFF);
        } else {
            int saveY = y + clientLoadouts.size() * (cardH + cardGap) + 6;
            g.fill(x, saveY, x + contentW, saveY + 22, 0xFF181818);
            g.renderOutline(x, saveY, contentW, 22, 0xFF333333);
            g.drawCenteredString(font, "§8Max loadouts reached (8/8)",
                    x + contentW / 2, saveY + 7, 0x444444);
        }
    }


    // Mouse

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        playUiClick();
        // Tab clicks
        int tabY = 24;
        for (int t = 0; t < 4; t++) {
            int tx = LIST_X + t * (TAB_W + 2);
            if (mx >= tx && mx < tx + TAB_W && my >= tabY && my < tabY + TAB_H) {
                if (activePhase) return true;
                // Block Loadouts tab while modifying a loadout
                if (t == 3 && modifyingLoadoutIndex >= 0) return true;
                if (activeTab != t) {
                    exitEditor();
                    activeCategory = 0;
                    scrollOffset = 0;
                    namingLoadoutIndex = -1;
                    confirmDeleteIndex = -1;
                    focusedItemId = null;
                    if (searchBox != null) searchBox.setValue("");
                    if (t == 1) ClientTips.show("2");
                    else if (t == 3) ClientTips.show("3");
                }
                activeTab = t;
                return true;
            }
        }

        // Right-click on loadout panel (gun slots / throwable slots)
        if (button == 1) {
            int px = panelX();
            int py = LIST_Y - 22 + 20 + 6 + 12;  // after header + section padding + label
            for (int i = 0; i < MAX_GUN_SLOTS; i++) {
                boolean hasGun = i < selectedGuns.size();
                int slotH = hasGun ? 42 : 28;
                if (hasGun && mx >= px + 4 && mx < px + PANEL_W - 4
                        && my >= py && my < py + slotH) {
                    selectedGuns.remove(i);
                    PacketHandler.CHANNEL.sendToServer(new SelectGunPacket(new ArrayList<>(selectedGuns)));
                    return true;
                }
                py += slotH + 4;
            }
            // Throwable slots
            int weapH = 6 + 12 + MAX_GUN_SLOTS * 46 + (MAX_GUN_SLOTS > 0 ? (MAX_GUN_SLOTS - 1) * 4 : 0) + 6;
            int thrSlotSize = 30, thrGap = 3;
            int thrGridW = MAX_THROWABLE_PICKS * thrSlotSize + (MAX_THROWABLE_PICKS - 1) * thrGap;
            int thrTopY = LIST_Y - 22 + 20 + weapH + 4 + 6 + 14;
            int startThrX = panelX() + (PANEL_W - thrGridW) / 2;
            for (int i = 0; i < MAX_THROWABLE_PICKS; i++) {
                int sx = startThrX + i * (thrSlotSize + thrGap);
                if (i < selectedThrowables.size()
                        && mx >= sx && mx < sx + thrSlotSize
                        && my >= thrTopY && my < thrTopY + thrSlotSize) {
                    selectedThrowables.remove(i);
                    PacketHandler.CHANNEL.sendToServer(new SelectThrowablePacket(new ArrayList<>(selectedThrowables)));
                    return true;
                }
            }
        }

        // Save/Cancel buttons when modifying loadout
        if (modifyingLoadoutIndex >= 0 && modifyingLoadoutIndex < clientLoadouts.size()) {
            int px = panelX(), pw = PANEL_W;
            int weapH = 6 + 12 + MAX_GUN_SLOTS * 46 + (MAX_GUN_SLOTS > 0 ? (MAX_GUN_SLOTS - 1) * 4 : 0) + 6;
            int thrSectionH = 6 + 14 + 30 + 8;
            int barY = LIST_Y - 22 + 20 + weapH + 4 + thrSectionH + 4;
            if (my >= barY && my < barY + 20) {
                if (mx >= px && mx < px + pw - 54) {
                    PacketHandler.CHANNEL.sendToServer(new com.frosty.bedgunwars.network.LoadoutPacket(
                            com.frosty.bedgunwars.network.LoadoutPacket.Action.SAVE_OVER,
                            modifyingLoadoutIndex, ""));
                    modifyingLoadoutIndex = -1;
                    return true;
                }
                int cancelX = px + pw - 52;
                if (mx >= cancelX && mx < cancelX + 52) {
                    modifyingLoadoutIndex = -1;
                    return true;
                }
            }
        }

        // Loadouts tab
        if (activeTab == 3) {
            int px = panelX();
            int contentW = px - LIST_X - 10;
            int x = LIST_X, y = LIST_Y;
            int cardH = 64, cardGap = 6;

            if (namingLoadoutIndex >= 0) return true;

            if (confirmDeleteIndex >= 0) {
                int ovW = 300, ovH = 90;
                int ovX = this.width / 2 - ovW / 2, ovY = this.height / 2 - ovH / 2;
                int cbX = ovX + ovW / 2 - 75, cbY = ovY + ovH - 28;
                if (mx >= cbX && mx < cbX + 65 && my >= cbY && my < cbY + 22) {
                    PacketHandler.CHANNEL.sendToServer(new com.frosty.bedgunwars.network.LoadoutPacket(
                            com.frosty.bedgunwars.network.LoadoutPacket.Action.DELETE,
                            confirmDeleteIndex, ""));
                    confirmDeleteIndex = -1;
                    return true;
                }
                int ccX = ovX + ovW / 2 + 10;
                if (mx >= ccX && mx < ccX + 65 && my >= cbY && my < cbY + 22) {
                    confirmDeleteIndex = -1;
                    return true;
                }
                return true;
            }

            for (int i = 0; i < clientLoadouts.size(); i++) {
                int sy = y + i * (cardH + cardGap);
                int btnAreaX = x + contentW - 184;
                int btnY = sy + (cardH - 18) / 2;
                // Modify button
                if (mx >= btnAreaX && mx < btnAreaX + 52 && my >= btnY && my < btnY + 18) {
                    if (modifyingLoadoutIndex == i) {
                        // Toggle off
                        modifyingLoadoutIndex = -1;
                    } else {
                        modifyingLoadoutIndex = i;
                        // Switch to Weapons tab to start modifying
                        activeTab = 0;
                        activeCategory = 0;
                        scrollOffset = 0;
                    }
                    return true;
                }
                // Rename button
                int renX = btnAreaX + 58;
                if (mx >= renX && mx < renX + 58 && my >= btnY && my < btnY + 18) {
                    namingLoadoutIndex = i;
                    namingNew = false;
                    loadoutNameBuffer = clientLoadouts.get(i).name;
                    return true;
                }
                // Delete button
                int delX = renX + 64;
                if (mx >= delX && mx < delX + 52 && my >= btnY && my < btnY + 18) {
                    confirmDeleteIndex = i;
                    return true;
                }
                // Card click — select and apply
                if (mx >= x && mx < x + contentW && my >= sy && my < sy + cardH) {
                    selectedLoadoutIndex = i;
                    PacketHandler.CHANNEL.sendToServer(new com.frosty.bedgunwars.network.LoadoutPacket(
                            com.frosty.bedgunwars.network.LoadoutPacket.Action.APPLY, i, ""));
                    return true;
                }
            }

            // Save Loadout / Cancel buttons (shown when modifying)
            if (modifyingLoadoutIndex >= 0) {
                int saveY = y + clientLoadouts.size() * (cardH + cardGap) + 6;
                int saveW = contentW - 70;
                if (mx >= x && mx < x + saveW && my >= saveY && my < saveY + 22) {
                    PacketHandler.CHANNEL.sendToServer(new com.frosty.bedgunwars.network.LoadoutPacket(
                            com.frosty.bedgunwars.network.LoadoutPacket.Action.SAVE_OVER,
                            modifyingLoadoutIndex, ""));
                    modifyingLoadoutIndex = -1;
                    return true;
                }
                int cancelX = x + saveW + 4;
                int cancelW = contentW - saveW - 4;
                if (mx >= cancelX && mx < cancelX + cancelW && my >= saveY && my < saveY + 22) {
                    modifyingLoadoutIndex = -1;
                    return true;
                }
            }

            if (!activePhase && clientLoadouts.size() < com.frosty.bedgunwars.game.LoadoutManager.MAX_LOADOUTS) {
                int saveY = y + clientLoadouts.size() * (cardH + cardGap) + 6;
                if (mx >= x && mx < x + contentW && my >= saveY && my < saveY + 22) {
                    namingLoadoutIndex = clientLoadouts.size();
                    namingNew = true;
                    loadoutNameBuffer = "Loadout " + (clientLoadouts.size() + 1);
                    return true;
                }
            }
            return false;
        }

        // Attachment tab
        if (activeTab == 1) {
            if (inAttachmentEditor()) {
                int backY = this.height - 28;
                if (mx >= LIST_X && mx < LIST_X + 70 && my >= backY && my < backY + 18) {
                    exitEditor();
                    return true;
                }
                // Column interactions
                List<AttachmentType> visibleTypes = getVisibleTypes();
                Map<AttachmentType, List<ResourceLocation>> byType = buildByType();
                Map<String, String> equipped = gunAttachments.computeIfAbsent(
                        attachmentEditorGunSlot, k -> new HashMap<>());

                int numCols = Math.min(visibleTypes.size(), 3);
                if (numCols == 0) return true;
                int areaW = panelX() - 16 - LIST_X;
                int areaH = (this.height - LIST_Y - 40) / ((visibleTypes.size() + 2) / 3);
                int colW = (areaW - (numCols - 1) * 6) / numCols;
                int colItemH = 22, colHeaderH = 18;

                for (int ci = 0; ci < visibleTypes.size(); ci++) {
                    AttachmentType type = visibleTypes.get(ci);
                    int row = ci / 3, col = ci % 3;
                    int colX = LIST_X + col * (colW + 6);
                    int colY = LIST_Y + row * (areaH + 6);
                    int innerH = areaH - colHeaderH - 4;
                    int visibleItems = innerH / colItemH;
                    String equippedId = equipped.get(type.name());
                    List<ResourceLocation> items = byType.getOrDefault(type, new ArrayList<>());
                    List<ResourceLocation> unequipped = new ArrayList<>();
                    for (ResourceLocation id : items)
                        if (!id.toString().equals(equippedId)) unequipped.add(id);

                    if (mx < colX || mx >= colX + colW || my < colY || my >= colY + areaH) continue;

                    int itemAreaY = colY + colHeaderH + 2;

                    int sbX = colX + colW - 5;
                    int listEndY = colY + areaH - 2;
                    if (mx >= sbX && unequipped.size() > visibleItems) {
                        draggingColumnIndex = ci;
                        updateColScrollFromMouse(ci, my, itemAreaY, listEndY, unequipped.size());
                        return true;
                    }

                    if (equippedId != null) {
                        if (my >= itemAreaY && my < itemAreaY + colItemH) {
                            int rbX = colX + colW - 24;
                            if (mx >= rbX) {
                                equipped.remove(type.name());
                                PacketHandler.CHANNEL.sendToServer(
                                        new SelectAttachmentPacket(attachmentEditorGunSlot, type, null));
                                return true;
                            }
                        }
                        itemAreaY += colItemH + 2;
                    }

                    for (int ri = 0; ri < visibleItems && ri + colScrollOffsets[ci] < unequipped.size(); ri++) {
                        int ry = itemAreaY + ri * colItemH;
                        if (my >= ry && my < ry + colItemH && ry + colItemH <= listEndY) {
                            ResourceLocation id = unequipped.get(ri + colScrollOffsets[ci]);
                            equipped.put(type.name(), id.toString());
                            PacketHandler.CHANNEL.sendToServer(
                                    new SelectAttachmentPacket(attachmentEditorGunSlot, type, id));
                            return true;
                        }
                    }
                }
                return true;
            }

            // Gun picker clicks
            int cardW = 155, cardGap = 8;
            int availW = panelX() - 10 - LIST_X;
            int totalW = MAX_GUN_SLOTS * cardW + (MAX_GUN_SLOTS - 1) * cardGap;
            int startX = LIST_X + Math.max(0, (availW - totalW) / 2);
            int startY = LIST_Y + 30;
            for (int i = 0; i < MAX_GUN_SLOTS; i++) {
                int cx = startX + i * (cardW + cardGap);
                if (mx >= cx && mx < cx + cardW && my >= startY && my < startY + 110) {
                    if (i < selectedGuns.size()) enterEditor(i);
                    return true;
                }
            }
            return true;
        }

        // 3D preview drag — start when clicking inside the preview box on guns/throwables tab
        if ((activeTab == 0 || activeTab == 2) && activeTab == 0 && button == 0
                && mx >= previewBoxX && mx < previewBoxX + previewBoxW
                && my >= previewBoxY && my < previewBoxY + previewBoxH) {
            isDraggingPreview = true;
            lastDragX = mx;
            lastDragY = my;
            return true;
        }

        // Category filter
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
        if (filtered.size() > VISIBLE_ROWS && mx >= SB_X && mx < SB_X + SB_W
                && my >= LIST_Y && my < LIST_Y + listH) {
            draggingScrollbar = true;
            updateScrollFromMouse(my);
            return true;
        }

        // Item list click
        List<ResourceLocation> selection = activeSelection();
        int maxPicks = activeMaxPicks();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= filtered.size()) break;
            int rowY = LIST_Y + i * ROW_H;
            if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= rowY && my < rowY + ROW_H) {
                ResourceLocation id = filtered.get(idx);
                focusedItemId = id;
                if (activeTab == 2) {
                    // Throwables: allow duplicates; right-click removes last occurrence
                    if (button == 1) {
                        int lastIdx = -1;
                        for (int j = selection.size() - 1; j >= 0; j--)
                            if (selection.get(j).equals(id)) { lastIdx = j; break; }
                        if (lastIdx >= 0) selection.remove(lastIdx);
                    } else {
                        if (selection.size() < maxPicks) selection.add(id);
                    }
                    PacketHandler.CHANNEL.sendToServer(new SelectThrowablePacket(new ArrayList<>(selection)));
                } else {
                    if (button == 1) {
                        selection.remove(id);
                    } else {
                        if (selection.contains(id)) selection.remove(id);
                        else if (selection.size() < maxPicks) selection.add(id);
                    }
                    PacketHandler.CHANNEL.sendToServer(new SelectGunPacket(new ArrayList<>(selection)));
                }
                return true;
            }
        }

        // ADD TO LOADOUT button in detail pane
        if (activeTab == 0 || activeTab == 2) {
            ResourceLocation item = null;
            if (hoveredItemId != null) item = hoveredItemId;
            else if (focusedItemId != null) item = focusedItemId;
            if (item != null && !selection.contains(item) && selection.size() < maxPicks) {
                int ax = detailX() + 14;
                int aw = detailW() - 28 - 36;
                int actionY = detailY() + detailH() - 28 - 6;
                if (mx >= ax && mx < ax + aw && my >= actionY && my < actionY + 20) {
                    selection.add(item);
                    focusedItemId = item;
                    if (activeTab == 0)
                        PacketHandler.CHANNEL.sendToServer(new SelectGunPacket(new ArrayList<>(selection)));
                    else
                        PacketHandler.CHANNEL.sendToServer(new SelectThrowablePacket(new ArrayList<>(selection)));
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        // Hover sound — fire when hovering a new interactive element
        String zone = computeHoverZone((int) mx, (int) my);
        if (zone != null && !zone.equals(lastHoveredZoneForSound)) {
            playUiHover();
        }
        lastHoveredZoneForSound = zone;
        super.mouseMoved(mx, my);
    }

    /** Returns a stable string key for whatever interactive zone the mouse is over, or null. */
    private String computeHoverZone(int mx, int my) {
        // Tabs
        int tabY = 24;
        for (int t = 0; t < 4; t++) {
            int tx = LIST_X + t * (TAB_W + 2);
            if (mx >= tx && mx < tx + TAB_W && my >= tabY && my < tabY + TAB_H) return "tab:" + t;
        }
        // Item list rows
        if (activeTab == 0 || activeTab == 2) {
            List<ResourceLocation> filtered = filteredCatalogue();
            int listH = VISIBLE_ROWS * ROW_H;
            if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= LIST_Y && my < LIST_Y + listH) {
                int i = (my - LIST_Y) / ROW_H;
                int idx = i + scrollOffset;
                if (idx >= 0 && idx < filtered.size()) return "item:" + filtered.get(idx);
            }
            // Category buttons
            String[] cats = activeCategories();
            for (int i = 0; i < cats.length; i++) {
                int cy = CAT_Y + i * (CAT_H + CAT_GAP);
                if (mx >= CAT_X && mx < CAT_X + CAT_W && my >= cy && my < cy + CAT_H) return "cat:" + i;
            }
            // Detail pane add button
            List<ResourceLocation> sel = activeSelection();
            ResourceLocation item = hoveredItemId != null ? hoveredItemId : focusedItemId;
            if (item != null && !sel.contains(item) && sel.size() < activeMaxPicks()) {
                int ax = detailX() + 14, aw = detailW() - 28 - 36;
                int actionY = detailY() + detailH() - 28 - 6;
                if (mx >= ax && mx < ax + aw && my >= actionY && my < actionY + 20) return "detail:add";
            }
        }
        // Attachment gun picker cards
        if (activeTab == 1 && !inAttachmentEditor()) {
            int cw = 155, ch = 110, cg = 8;
            int aw = panelX() - 10 - LIST_X;
            int tw = MAX_GUN_SLOTS * cw + (MAX_GUN_SLOTS - 1) * cg;
            int sx = LIST_X + Math.max(0, (aw - tw) / 2), sy = LIST_Y + 30;
            for (int i = 0; i < MAX_GUN_SLOTS; i++) {
                int cx = sx + i * (cw + cg);
                if (mx >= cx && mx < cx + cw && my >= sy && my < sy + ch) return "card:" + i;
            }
        }
        // Attachment editor back button
        if (inAttachmentEditor()) {
            int backY = this.height - 28;
            if (mx >= LIST_X && mx < LIST_X + 70 && my >= backY && my < backY + 18) return "editor:back";
        }
        // Loadout tab buttons
        if (activeTab == 3 && confirmDeleteIndex < 0 && namingLoadoutIndex < 0) {
            int px = panelX(), contentW = px - LIST_X - 10;
            int x = LIST_X, y = LIST_Y, ch = 64, cg = 6;
            for (int i = 0; i < clientLoadouts.size(); i++) {
                int sy = y + i * (ch + cg);
                int btnAreaX = x + contentW - 178, btnY = sy + (ch - 18) / 2;
                if (my >= btnY && my < btnY + 18) {
                    if (mx >= btnAreaX && mx < btnAreaX + 52) return "loadout:load:" + i;
                    int renX = btnAreaX + 58;
                    if (mx >= renX && mx < renX + 58) return "loadout:rename:" + i;
                    int delX = renX + 64;
                    if (mx >= delX && mx < delX + 52) return "loadout:delete:" + i;
                }
            }
            int saveY = y + clientLoadouts.size() * (ch + cg) + 6;
            if (mx >= x && mx < x + contentW && my >= saveY && my < saveY + 22) return "loadout:save";
        }
        return null;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingPreview) {
            previewRotY += (float)(mx - lastDragX) * 0.8f;
            previewRotX += (float)(my - lastDragY) * 0.8f;
            previewRotX = Math.max(-89f, Math.min(89f, previewRotX));
            lastDragX = mx;
            lastDragY = my;
            return true;
        }
        if (draggingColumnIndex >= 0) {
            List<AttachmentType> visibleTypes = getVisibleTypes();
            if (draggingColumnIndex < visibleTypes.size()) {
                int numCols = Math.min(visibleTypes.size(), 3);
                int areaW = panelX() - 16 - LIST_X;
                int areaH = (this.height - LIST_Y - 40) / ((visibleTypes.size() + 2) / 3);
                int colW = (areaW - (numCols - 1) * 6) / numCols;
                int ci = draggingColumnIndex;
                int row = ci / 3, col = ci % 3;
                int colY = LIST_Y + row * (areaH + 6);
                int itemAreaY = colY + 18;
                Map<String, String> equipped = gunAttachments.getOrDefault(attachmentEditorGunSlot, new HashMap<>());
                AttachmentType type = visibleTypes.get(ci);
                if (equipped.containsKey(type.name())) itemAreaY += 24;
                int listEndY = colY + areaH - 2;
                Map<AttachmentType, List<ResourceLocation>> byType = buildByType();
                List<ResourceLocation> items = byType.getOrDefault(type, new ArrayList<>());
                updateColScrollFromMouse(ci, my, itemAreaY, listEndY, items.size());
            }
            return true;
        }
        if (draggingScrollbar) { updateScrollFromMouse(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingPreview = false;
        draggingScrollbar = false;
        draggingColumnIndex = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // Zoom 3D preview when scrolling over it
        if (activeTab == 0 && mx >= previewBoxX && mx < previewBoxX + previewBoxW
                && my >= previewBoxY && my < previewBoxY + previewBoxH) {
            previewScale = (float) Math.max(0.3f, Math.min(3.0f, previewScale + delta * 0.1f));
            return true;
        }
        if (inAttachmentEditor()) {
            List<AttachmentType> visibleTypes = getVisibleTypes();
            int numCols = Math.min(visibleTypes.size(), 3);
            int areaW = panelX() - 16 - LIST_X;
            int areaH = (visibleTypes.isEmpty() ? 1 : (this.height - LIST_Y - 40) / ((visibleTypes.size() + 2) / 3));
            int colW = numCols > 0 ? (areaW - (numCols - 1) * 6) / numCols : areaW;
            for (int ci = 0; ci < visibleTypes.size(); ci++) {
                int row = ci / 3, col = ci % 3;
                int colX = LIST_X + col * (colW + 6);
                int colY = LIST_Y + row * (areaH + 6);
                if (mx >= colX && mx < colX + colW && my >= colY && my < colY + areaH) {
                    colScrollOffsets[ci] = Math.max(0, colScrollOffsets[ci] - (int) delta);
                    return true;
                }
            }
            return true;
        }
        int size = filteredCatalogue().size();
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) delta,
                Math.max(0, size - VISIBLE_ROWS)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (namingLoadoutIndex >= 0) {
            if (keyCode == 257 || keyCode == 335) { // Enter
                if (!loadoutNameBuffer.isEmpty()) {
                    if (namingNew) {
                        PacketHandler.CHANNEL.sendToServer(
                                new com.frosty.bedgunwars.network.LoadoutPacket(
                                        com.frosty.bedgunwars.network.LoadoutPacket.Action.SAVE,
                                        -1, loadoutNameBuffer));
                    } else {
                        PacketHandler.CHANNEL.sendToServer(
                                new com.frosty.bedgunwars.network.LoadoutPacket(
                                        com.frosty.bedgunwars.network.LoadoutPacket.Action.RENAME,
                                        namingLoadoutIndex, loadoutNameBuffer));
                    }
                }
                namingLoadoutIndex = -1;
                loadoutNameBuffer = "";
                return true;
            }
            if (keyCode == 256) { namingLoadoutIndex = -1; loadoutNameBuffer = ""; return true; }
            if (keyCode == 259 && !loadoutNameBuffer.isEmpty()) {
                loadoutNameBuffer = loadoutNameBuffer.substring(0, loadoutNameBuffer.length() - 1);
                return true;
            }
            return true;
        }
        if (keyCode == 256 && modifyingLoadoutIndex >= 0) { modifyingLoadoutIndex = -1; return true; }
        if (keyCode == 256 && confirmDeleteIndex >= 0) { confirmDeleteIndex = -1; return true; }
        if (keyCode == 256 && inAttachmentEditor()) { exitEditor(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (namingLoadoutIndex >= 0 && loadoutNameBuffer.length() < 32) {
            loadoutNameBuffer += c;
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    private void updateScrollFromMouse(double my) {
        List<ResourceLocation> filtered = filteredCatalogue();
        int listH = VISIBLE_ROWS * ROW_H;
        int maxScroll = Math.max(1, filtered.size() - VISIBLE_ROWS);
        double ratio = (my - LIST_Y) / listH;
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, ratio * filtered.size()));
    }

    @Override
    public void onClose() {
        ClientTips.show("4");
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }


    // Item stack builders

    private ItemStack buildGunStack(ResourceLocation id) {
        try {
            ItemStack s = GunHelper.buildGun(id);
            return s.isEmpty() ? new ItemStack(Items.BOW) : s;
        } catch (Exception e) { return new ItemStack(Items.BOW); }
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
            ItemStack s = GunHelper.buildThrowable(id);
            if (!s.isEmpty()) return s;
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }


    // Category helpers

    private String getCategoryLabel(ResourceLocation id) {
        if (id == null) return "Rifle";
        return GunHelper.getGunCategory(id);
    }

    private String getThrowableCategory(ResourceLocation id) {
        String p = id.getPath();
        if (p.contains("smoke")) return "Smoke";
        if (p.contains("flash")) return "Flash";
        if (p.contains("frag") || p.contains("grenade")) return "Frag";
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
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}