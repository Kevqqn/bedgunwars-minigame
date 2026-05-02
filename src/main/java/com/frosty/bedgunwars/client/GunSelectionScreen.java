package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.game.GunHelper;
import com.frosty.bedgunwars.game.SoundHelper;
import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.network.SelectAttachmentPacket;
import com.frosty.bedgunwars.network.SelectGunPacket;
import com.frosty.bedgunwars.network.SelectThrowablePacket;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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

    // Layout
    private static final int LIST_X       = 10;
    private static final int LIST_Y       = 70;
    private static final int LIST_W       = 200;
    private static final int ROW_H        = 36;
    private static final int ICON_SIZE    = 32;
    private static final int VISIBLE_ROWS = 9;

    private static final int SB_W = 8;
    private static final int SB_X = LIST_X + LIST_W + 2;

    private static final int CAT_X   = LIST_X + LIST_W + SB_W + 8;
    private static final int CAT_Y   = LIST_Y;
    private static final int CAT_W   = 80;
    private static final int CAT_H   = 16;
    private static final int CAT_GAP = 3;

    private static final int PANEL_W = 140;
    private static final int TAB_H   = 20;
    private static final int TAB_W   = 90;

    // Back button (attachment editor State 2)
    private static final int BACK_W = 70;
    private static final int BACK_H = 18;
    private static final int BACK_X = LIST_X;
    private static final int BACK_Y_OFFSET = 10;

    // Gun picker card dimensions (attachment tab State 1)
    private static final int CARD_W = 110;
    private static final int CARD_H = 130;
    private static final int CARD_GAP = 14;

    // State
    private int  activeTab      = 0;   // 0=guns, 1=attachments, 2=throwables
    private int  scrollOffset   = 0;
    private int  activeCategory = 0;
    private boolean draggingScrollbar = false;

    // Attachment editor state
    private int attachmentEditorGunSlot = -1; // -1 = gun picker, 0/1/2 = editor
    private List<ResourceLocation> editorCompatibleAttachments = new ArrayList<>();
    private final int[] colScrollOffsets = new int[6]; // one per attachment type column
    private int draggingColumnIndex = -1; // which column scrollbar is being dragged

    // Ordered attachment types for the 6-column grid
    private static final AttachmentType[] COLUMN_TYPES = {
            AttachmentType.SCOPE, AttachmentType.GRIP,     AttachmentType.MUZZLE,
            AttachmentType.STOCK, AttachmentType.EXTENDED_MAG,  AttachmentType.LASER
    };
    private static final String[] COLUMN_LABELS = {
            "Scope", "Grip", "Muzzle", "Stock", "Magazine", "Laser"
    };

    // Editor scroll offset (separate from main list scroll)
    private int editorScrollOffset = 0;

    private EditBox searchBox;

    private static final String[] GUN_CATS   = {"All", "Rifle", "SMG", "Pistol", "Sniper", "Shotgun", "LMG"};
    private static final String[] THROW_CATS = {"All", "Frag", "Smoke", "Flash"};

    // AttachmentType display names
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

    // Sound for hover
    private int lastHoveredIndex = -1;

    /** Backwards-compatible open without gunAttachments (empty map). */
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
        gunAttachments.forEach((slot, typeMap) ->
                this.gunAttachments.merge(slot, new HashMap<>(typeMap), (o, n) -> { o.putAll(n); return o; }));
        this.attachmentEditorGunSlot = savedSlot;
        if (savedSlot >= 0 && savedSlot < this.selectedGuns.size())
            rebuildEditorCache(savedSlot);
    }

//    public static void open(
//            List<ResourceLocation> allGuns,        List<ResourceLocation> currentGuns,
//            List<ResourceLocation> allAttachments, List<ResourceLocation> currentAttachments,
//            List<ResourceLocation> allThrowables,  List<ResourceLocation> currentThrowables) {
//        open(allGuns, currentGuns, allAttachments, currentAttachments,
//                allThrowables, currentThrowables, new HashMap<>());
//    }

    public static void open(
            List<ResourceLocation> allGuns,        List<ResourceLocation> currentGuns,
            List<ResourceLocation> allAttachments, List<ResourceLocation> currentAttachments,
            List<ResourceLocation> allThrowables,  List<ResourceLocation> currentThrowables,
            Map<Integer, Map<String, String>> gunAttachments) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GunSelectionScreen existing) {
            existing.updateData(allGuns, currentGuns, allAttachments,
                    allThrowables, currentThrowables, gunAttachments);
            return;
        }
        mc.setScreen(new GunSelectionScreen(allGuns, currentGuns,
                allAttachments, currentAttachments,
                allThrowables, currentThrowables, gunAttachments));
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
    private boolean inAttachmentEditor() {
        return activeTab == 1 && attachmentEditorGunSlot >= 0;
    }

    private boolean inAttachmentGunPicker() {
        return activeTab == 1 && attachmentEditorGunSlot < 0;
    }

    private void updateEditorScrollFromMouse(double my) {
        int listH = VISIBLE_ROWS * ROW_H;
        // Estimate total items
        int total = editorCompatibleAttachments.size() + 6; // +6 for section headers approx
        int maxScroll = Math.max(1, total - VISIBLE_ROWS);
        double ratio = (my - LIST_Y) / listH;
        editorScrollOffset = (int) Math.max(0, Math.min(maxScroll, ratio * total));
    }

    /** Enter the attachment editor for a given gun slot. Rebuilds compatible attachment cache. */
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
        return switch (activeTab) {
            case 2  -> allThrowables;
            default -> allGuns;
        };
    }

    private List<ResourceLocation> activeSelection() {
        return switch (activeTab) {
            case 2  -> selectedThrowables;
            default -> selectedGuns;
        };
    }

    private int activeMaxPicks() {
        return switch (activeTab) {
            case 2  -> MAX_THROWABLE_PICKS;
            default -> MAX_GUN_SLOTS;
        };
    }

    private String[] activeCategories() {
        return activeTab == 2 ? THROW_CATS : GUN_CATS;
    }

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

    // Render
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int panelX = this.width - PANEL_W - 6;

        // Title bar
        g.fill(0, 0, this.width, 18, 0xFF0A0A0A);
        g.drawCenteredString(font, "§6§lLOADOUT SELECTION", this.width / 2, 5, 0xFFFFFF);

        // Tabs
        renderTabs(g, mouseX, mouseY);

        // Content depends on active tab and sub-state
        if (activeTab == 1) {
            if (searchBox != null) searchBox.visible = false;
            if (inAttachmentEditor()) {
                renderAttachmentEditor(g, mouseX, mouseY);
            } else {
                renderAttachmentGunPicker(g, mouseX, mouseY);
            }
        } else {
            if (searchBox != null) searchBox.visible = true;
            renderItemList(g, mouseX, mouseY);
            renderScrollbar(g, mouseX, mouseY);
            renderCategories(g, mouseX, mouseY);
        }

        // Right loadout panel always visible
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
            g.fill(tx, tabY, tx + TAB_W, tabY + TAB_H,
                    active ? 0xFF333333 : (hovered ? 0xFF222222 : 0xFF161616));
            if (active) g.fill(tx, tabY + TAB_H - 2, tx + TAB_W, tabY + TAB_H, 0xFFFFAA00);
            g.drawCenteredString(font, (active ? "§e" : "§7") + labels[t],
                    tx + TAB_W / 2, tabY + 6, 0xFFFFFF);
        }
    }

    private void renderAttachmentGunPicker(GuiGraphics g, int mx, int my) {
        int cardW = 120; int cardH = 120; int cardGap = 12;
        int startX = LIST_X; int startY = LIST_Y;
        g.drawString(font, "§7Select a weapon to modify attachments:",
                startX, startY - 12, 0x888888);
        for (int i = 0; i < MAX_GUN_SLOTS; i++) {
            int cx = startX + i * (cardW + cardGap);
            boolean hasGun = i < selectedGuns.size();
            boolean hovered = mx >= cx && mx < cx + cardW && my >= startY && my < startY + cardH;
            if (hovered && i != lastHoveredIndex) {
                lastHoveredIndex = i;
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT, SoundHelper.noteToPitch(18))
                );
            }
            g.fill(cx, startY, cx + cardW, startY + cardH,
                    hasGun ? (hovered ? 0xCC253525 : 0xCC1A2A1A) : 0xCC111111);
            g.renderOutline(cx, startY, cardW, cardH,
                    hasGun ? (hovered ? 0xFF55BB55 : 0xFF335533) : 0xFF2A2A2A);
            g.drawCenteredString(font, "§8Slot " + (i + 1), cx + cardW / 2, startY + 4, 0x555555);
            if (hasGun) {
                ResourceLocation gunId = selectedGuns.get(i);
                // Gun icon 2x centred
                g.pose().pushPose();
                g.pose().translate(cx + (cardW - 32) / 2, startY + 16, 0);
                g.pose().scale(2f, 2f, 1f);
                g.renderItem(buildGunStack(gunId), 0, 0);
                g.pose().popPose();
                g.drawCenteredString(font, "§f" + shorten(GunHelper.getGunDisplayName(gunId), 11),
                        cx + cardW / 2, startY + 54, 0xFFFFFF);
                g.fill(cx + 6, startY + 64, cx + cardW - 6, startY + 65, 0xFF333333);
                // Equipped attachment icons
                Map<String, String> equipped = gunAttachments.getOrDefault(i, new HashMap<>());
                int ix = cx + 4; int iy = startY + 68;
                for (var e : equipped.entrySet()) {
                    try {
                        ItemStack s = buildAttachmentStack(ResourceLocation.parse(e.getValue()));
                        if (!s.isEmpty()) { g.renderItem(s, ix, iy); ix += 18; }
                    } catch (Exception ignored) {}
                }
                if (equipped.isEmpty())
                    g.drawCenteredString(font, "§8No attachments", cx + cardW / 2, startY + 76, 0x444444);
                if (hovered) {
                    g.fill(cx, startY + cardH - 16, cx + cardW, startY + cardH, 0xAA000000);
                    g.drawCenteredString(font, "§aClick to modify", cx + cardW / 2, startY + cardH - 11, 0x44FF44);
                }
            } else {
                g.drawCenteredString(font, "§8— Empty —", cx + cardW / 2, startY + cardH / 2, 0x333333);
            }
        }
    }

    private void renderAttachmentEditor(GuiGraphics g, int mx, int my) {
        ResourceLocation gunId = selectedGuns.get(attachmentEditorGunSlot);
        Map<String, String> equipped = gunAttachments.getOrDefault(attachmentEditorGunSlot, new HashMap<>());

        // Build per-type attachment lists (only types with >=1 compatible attachment)
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
        for (AttachmentType t : COLUMN_TYPES) {
            if (byType.containsKey(t) && !byType.get(t).isEmpty()) visibleTypes.add(t);
        }

        int numCols = Math.min(visibleTypes.size(), 3); // max 3 per row
        int numRows = (visibleTypes.size() + 2) / 3;    // 1 or 2 rows

        // Available area: from LIST_X to start of loadout panel
        int areaW = this.width - PANEL_W - 16 - LIST_X;
        int areaH = (this.height - LIST_Y - 40) / numRows;
        int colW = (areaW - (numCols - 1) * 6) / numCols;
        int colItemH = 22; // height of each attachment row inside column
        int colHeaderH = 16;
        int colPinnedH = equipped.isEmpty() ? 0 : colItemH + 2;

        // Back button bottom-left
        int backY = this.height - 28;
        boolean backHov = mx >= LIST_X && mx < LIST_X + 70 && my >= backY && my < backY + 18;
        g.fill(LIST_X, backY, LIST_X + 70, backY + 18, backHov ? 0xFF333333 : 0xFF1A1A1A);
        g.renderOutline(LIST_X, backY, 70, 18, backHov ? 0xFFFFAA00 : 0xFF444444);
        if (backHov) g.fill(LIST_X, backY + 16, LIST_X + 70, backY + 18, 0xFFFFAA00);
        g.drawCenteredString(font, backHov ? "§e← Back" : "§7← Back", LIST_X + 35, backY + 5, 0xFFFFFF);

        // Gun header
        g.renderItem(buildGunStack(gunId), LIST_X + 74, backY);
        g.drawString(font, "§e" + GunHelper.getGunDisplayName(gunId) + " §8— Attachments",
                LIST_X + 94, backY + 5, 0xFFFFFF);

        // Render each column
        for (int ci = 0; ci < visibleTypes.size(); ci++) {
            AttachmentType type = visibleTypes.get(ci);
            int row = ci / 3; int col = ci % 3;
            int colX = LIST_X + col * (colW + 6);
            int colY = LIST_Y + row * (areaH + 6);
            int innerH = areaH - colHeaderH - 4;
            int visibleItems = innerH / colItemH;

            String equippedId = equipped.get(type.name());
            List<ResourceLocation> items = byType.get(type);
            // Unequipped items only (equipped is pinned separately)
            List<ResourceLocation> unequipped = new ArrayList<>();
            for (ResourceLocation id : items) {
                if (!id.toString().equals(equippedId)) unequipped.add(id);
            }

            // Column background
            g.fill(colX, colY, colX + colW, colY + areaH, 0xFF111111);
            g.renderOutline(colX, colY, colW, areaH, 0xFF2A2A2A);

            // Column header
            g.fill(colX, colY, colX + colW, colY + colHeaderH, 0xFF1E1E1E);
            g.fill(colX, colY, colX + 3, colY + colHeaderH, 0xFFFFAA00);
            String label = COLUMN_LABELS[indexOf(COLUMN_TYPES, type)];
            g.drawString(font, "§e" + label, colX + 6, colY + 4, 0xFFAA00);
            if (equippedId != null)
                g.drawString(font, "§a✔", colX + colW - 12, colY + 4, 0x44FF44);

            int itemAreaY = colY + colHeaderH + 2;

            // Pinned equipped attachment (if any)
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
                    // Remove button
                    int rbX = colX + colW - 24;
                    boolean rbHov = mx >= rbX && mx < rbX + 22 && my >= itemAreaY + 3 && my < itemAreaY + colItemH - 3;
                    g.fill(rbX, itemAreaY + 3, rbX + 22, itemAreaY + colItemH - 3,
                            rbHov ? 0xFFAA2222 : 0xFF661111);
                    g.drawCenteredString(font, "§c✕", rbX + 11, itemAreaY + 6, 0xFF4444);
                } catch (Exception ignored) {}
                itemAreaY += colItemH + 2;
                // Divider
                g.fill(colX + 4, itemAreaY - 1, colX + colW - 4, itemAreaY, 0xFF333333);
            }

            // Scrollable unequipped list
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
                    g.drawString(font, "§7+", colX + colW - 10, ry + 7, 0x888888);
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

    // Standard item list (Guns / Throwables)
    private void renderItemList(GuiGraphics g, int mx, int my) {
        List<ResourceLocation> filtered = filteredCatalogue();
        List<ResourceLocation> selection = activeSelection();
        int listH = VISIBLE_ROWS * ROW_H;

        int maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        g.fill(LIST_X - 1, LIST_Y - 1, LIST_X + LIST_W + 1, LIST_Y + listH + 1, 0xFF1A1A1A);

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
            boolean hovered = mx >= LIST_X && mx < LIST_X + LIST_W
                    && my >= rowY && my < rowY + ROW_H;
            if (hovered && (i + scrollOffset) != lastHoveredIndex) {
                lastHoveredIndex = i + scrollOffset;
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT, SoundHelper.noteToPitch(18))
                );
            }

            int rowBg = isSelected ? 0xCC1A4A1A
                    : (hovered ? 0xCC252525 : (i % 2 == 0 ? 0xCC111111 : 0xCC0E0E0E));
            g.fill(LIST_X, rowY, LIST_X + LIST_W, rowY + ROW_H, rowBg);
            if (isSelected) g.renderOutline(LIST_X, rowY, LIST_W, ROW_H, 0xFF33BB33);

            // Icon
            ItemStack stack;
            if (activeTab == 2) {
                stack = buildThrowableStack(id);
                // LesRaisins slot texture for throwables
                boolean textureRendered = false;
                try {
                    var display = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableDisplay(stack);
                    if (display.isPresent() && display.get().getSlotTexture() != null) {
                        ResourceLocation slotTex = display.get().getSlotTexture();
                        int iconX = LIST_X + 3;
                        int iconY = rowY + (ROW_H - ICON_SIZE) / 2;
                        g.pose().pushPose();
                        g.pose().translate(iconX, iconY, 0);
                        g.pose().scale(ICON_SIZE / 16.0f, ICON_SIZE / 16.0f, 1.0f);
                        g.blit(slotTex, 0, 0, 0, 0, 16, 16, 16, 16);
                        g.pose().popPose();
                        textureRendered = true;
                    }
                } catch (Exception ignored) {}
                if (!textureRendered) renderIcon(g, stack, LIST_X + 3, rowY + (ROW_H - ICON_SIZE) / 2);
            } else {
                stack = buildGunStack(id);
                renderIcon(g, stack, LIST_X + 3, rowY + (ROW_H - ICON_SIZE) / 2);
            }

            // Name + category
            int textX = LIST_X + ICON_SIZE + 8;
            g.drawString(font, "§f" + displayName(id), textX, rowY + 6, 0xFFFFFF);
            String cat = resolveCategory(id);
            g.drawString(font, "§7" + cat, textX, rowY + 17, getCategoryColor(cat));

            // Selection slot indicator
            if (isSelected) {
                int slot = selection.indexOf(id) + 1;
                String tag = "[" + slot + "]";
                g.drawString(font, "§a" + tag,
                        LIST_X + LIST_W - font.width(tag) - 4, rowY + ROW_H / 2 - 4, 0x44FF44);
            }
        }
    }

    private void renderIcon(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(ICON_SIZE / 16.0f, ICON_SIZE / 16.0f, 1.0f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
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
        g.fill(SB_X + 1, thumbY, SB_X + SB_W - 1, thumbY + thumbH,
                sbHovered || draggingScrollbar ? 0xFFAAAAAA : 0xFF666666);
    }

    private void renderCategories(GuiGraphics g, int mx, int my) {
        String[] cats = activeCategories();
        for (int i = 0; i < cats.length; i++) {
            int cy = CAT_Y + i * (CAT_H + CAT_GAP);
            boolean active  = activeCategory == i;
            boolean hovered = mx >= CAT_X && mx < CAT_X + CAT_W && my >= cy && my < cy + CAT_H;
            g.fill(CAT_X, cy, CAT_X + CAT_W, cy + CAT_H,
                    active ? 0xFF444444 : (hovered ? 0xFF2A2A2A : 0xFF1A1A1A));
            if (active) g.fill(CAT_X, cy, CAT_X + 2, cy + CAT_H, 0xFFFFAA00);
            g.drawString(font, (active ? "§e" : "§7") + cats[i], CAT_X + 6, cy + 4, 0xFFFFFF);
        }
        g.drawString(font, "§8Filter", CAT_X, CAT_Y - 10, 0x555555);
    }

    // Right loadout panel
    private void renderLoadoutPanel(GuiGraphics g, int panelX) {
        int panelY = LIST_Y;
        g.fill(panelX - 4, LIST_Y - 22, panelX + PANEL_W + 4, LIST_Y - 2, 0xFF111111);
        g.drawCenteredString(font, "§e§lYOUR LOADOUT", panelX + PANEL_W / 2, LIST_Y - 18, 0xFFAA00);

        // Weapons
        g.drawString(font, "§eWeapons", panelX, panelY, 0xFFAA00);
        for (int i = 0; i < MAX_GUN_SLOTS; i++) {
            int slotY = panelY + 12 + i * 36;
            g.fill(panelX, slotY, panelX + PANEL_W, slotY + 24, 0xFF111111);
            g.renderOutline(panelX, slotY, PANEL_W, 24,
                    i < selectedGuns.size() ? 0xFF335533 : 0xFF333333);
            if (i < selectedGuns.size()) {
                ResourceLocation gunId = selectedGuns.get(i);
                g.renderItem(buildGunStack(gunId), panelX + 2, slotY + 4);
                g.drawString(font, "§f" + shorten(GunHelper.getGunDisplayName(gunId), 8),
                        panelX + 22, slotY + 4, 0xFFFFFF);

                // Show attachment icons for this gun slot
                Map<String, String> attMap = gunAttachments.getOrDefault(i, new HashMap<>());
                int attIconX = panelX + PANEL_W - 4; // start from right edge
                int attIconY = slotY + 4;
                // Render right-to-left so first attachment is rightmost
                List<ItemStack> attStacks = new ArrayList<>();
                for (var entry : attMap.entrySet()) {
                    try {
                        ResourceLocation attId = ResourceLocation.parse(entry.getValue());
                        ItemStack attStack = buildAttachmentStack(attId);
                        if (!attStack.isEmpty()) attStacks.add(attStack);
                    } catch (Exception ignored) {}
                }
                for (int a = attStacks.size() - 1; a >= 0; a--) {
                    attIconX -= 16;
                    if (attIconX < panelX + 22) break; // don't overlap gun name
                    g.renderItem(attStacks.get(a), attIconX, attIconY);
                }
            } else {
                g.drawCenteredString(font, "§8- Empty -", panelX + PANEL_W / 2, slotY + 8, 0x444444);
            }
        }

        // Throwables
        int throwY = panelY + 12 + MAX_GUN_SLOTS * 36 + 8;
        g.drawString(font, "§cThrowables §8(" + selectedThrowables.size() + "/" + MAX_THROWABLE_PICKS + ")",
                panelX, throwY, 0xFF5555);
        for (int i = 0; i < MAX_THROWABLE_PICKS; i++) {
            int slotY = throwY + 12 + i * 20;
            g.fill(panelX, slotY, panelX + PANEL_W, slotY + 18, 0xFF111111);
            g.renderOutline(panelX, slotY, PANEL_W, 18,
                    i < selectedThrowables.size() ? 0xFF442222 : 0xFF222222);
            if (i < selectedThrowables.size()) {
                ResourceLocation throwId = selectedThrowables.get(i);
                ItemStack ts = buildThrowableStack(throwId);
                boolean rendered = false;
                try {
                    var disp = me.xjqsh.lrtactical.api.LrTacticalAPI.getThrowableDisplay(ts);
                    if (disp.isPresent() && disp.get().getSlotTexture() != null) {
                        g.blit(disp.get().getSlotTexture(), panelX + 1, slotY + 1, 0, 0, 16, 16, 16, 16);
                        rendered = true;
                    }
                } catch (Exception ignored) {}
                if (!rendered) g.renderItem(ts, panelX + 1, slotY + 1);
                g.drawString(font, "§f" + shorten(GunHelper.getThrowableDisplayName(throwId), 13),
                        panelX + 20, slotY + 5, 0xFFFFFF);
            } else {
                g.drawString(font, "§8- empty -", panelX + 4, slotY + 5, 0x333333);
            }
        }
    }

    // Mouse
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Tab clicks
        int tabY = 20;
        for (int t = 0; t < 3; t++) {
            int tx = LIST_X + t * (TAB_W + 2);
            if (mx >= tx && mx < tx + TAB_W && my >= tabY && my < tabY + TAB_H) {
                if (activeTab != t) { exitEditor(); activeCategory = 0; scrollOffset = 0;
                    if (searchBox != null) searchBox.setValue(""); }
                activeTab = t; return true;
            }
        }

        if (activeTab == 1) {
            if (inAttachmentEditor()) {
                // Back button
                int backY = this.height - 28;
                if (mx >= LIST_X && mx < LIST_X + 70 && my >= backY && my < backY + 18) {
                    exitEditor(); return true;
                }
                // Column interactions
                List<AttachmentType> visibleTypes = getVisibleTypes();
                Map<AttachmentType, List<ResourceLocation>> byType = buildByType();
                Map<String, String> equipped = gunAttachments.computeIfAbsent(
                        attachmentEditorGunSlot, k -> new HashMap<>());

                int numCols = Math.min(visibleTypes.size(), 3);
                int areaW = this.width - PANEL_W - 16 - LIST_X;
                int areaH = (this.height - LIST_Y - 40) / ((visibleTypes.size() + 2) / 3);
                int colW = (areaW - (numCols - 1) * 6) / numCols;
                int colItemH = 22; int colHeaderH = 16;

                for (int ci = 0; ci < visibleTypes.size(); ci++) {
                    AttachmentType type = visibleTypes.get(ci);
                    int row = ci / 3; int col = ci % 3;
                    int colX = LIST_X + col * (colW + 6);
                    int colY = LIST_Y + row * (areaH + 6);
                    int innerH = areaH - colHeaderH - 4;
                    int visibleItems = innerH / colItemH;
                    String equippedId = equipped.get(type.name());
                    List<ResourceLocation> items = byType.getOrDefault(type, new ArrayList<>());
                    List<ResourceLocation> unequipped = new ArrayList<>();
                    for (ResourceLocation id : items) if (!id.toString().equals(equippedId)) unequipped.add(id);

                    if (mx < colX || mx >= colX + colW || my < colY || my >= colY + areaH) continue;

                    int itemAreaY = colY + colHeaderH + 2;

                    // Scrollbar click
                    int sbX = colX + colW - 5;
                    int listEndY = colY + areaH - 2;
                    if (mx >= sbX && unequipped.size() > visibleItems) {
                        draggingColumnIndex = ci;
                        updateColScrollFromMouse(ci, my, itemAreaY, listEndY, unequipped.size());
                        return true;
                    }

                    // Pinned equipped row
                    if (equippedId != null) {
                        if (my >= itemAreaY && my < itemAreaY + colItemH) {
                            // Remove button
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

                    // Unequipped rows
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
            int cardW = 120; int cardGap = 12;
            for (int i = 0; i < MAX_GUN_SLOTS; i++) {
                int cx = LIST_X + i * (cardW + cardGap);
                if (mx >= cx && mx < cx + cardW && my >= LIST_Y && my < LIST_Y + 120) {
                    if (i < selectedGuns.size()) enterEditor(i);
                    return true;
                }
            }
            return true;
        }

        // Category filters
        String[] cats = activeCategories();
        for (int i = 0; i < cats.length; i++) {
            int cy = CAT_Y + i * (CAT_H + CAT_GAP);
            if (mx >= CAT_X && mx < CAT_X + CAT_W && my >= cy && my < cy + CAT_H) {
                activeCategory = i; scrollOffset = 0; return true;
            }
        }

        // Scrollbar
        List<ResourceLocation> filtered = filteredCatalogue();
        int listH = VISIBLE_ROWS * ROW_H;
        if (filtered.size() > VISIBLE_ROWS && mx >= SB_X && mx < SB_X + SB_W
                && my >= LIST_Y && my < LIST_Y + listH) {
            draggingScrollbar = true; updateScrollFromMouse(my); return true;
        }

        // Item list (Guns / Throwables)
        List<ResourceLocation> selection = activeSelection();
        int maxPicks = activeMaxPicks();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = i + scrollOffset;
            if (idx >= filtered.size()) break;
            int rowY = LIST_Y + i * ROW_H;
            if (mx >= LIST_X && mx < LIST_X + LIST_W && my >= rowY && my < rowY + ROW_H) {
                ResourceLocation id = filtered.get(idx);
                if (activeTab == 0) {
                    if (selection.contains(id)) selection.remove(id);
                    else if (selection.size() < maxPicks) selection.add(id);
                    PacketHandler.CHANNEL.sendToServer(new SelectGunPacket(new ArrayList<>(selection)));
                } else {
                    if (button == 1) { for (int j = selection.size()-1; j >= 0; j--)
                        if (selection.get(j).equals(id)) { selection.remove(j); break; } }
                    else if (selection.size() < maxPicks) selection.add(id);
                    PacketHandler.CHANNEL.sendToServer(new SelectThrowablePacket(new ArrayList<>(selection)));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingColumnIndex >= 0) {
            List<AttachmentType> visibleTypes = getVisibleTypes();
            if (draggingColumnIndex < visibleTypes.size()) {
                int numCols = Math.min(visibleTypes.size(), 3);
                int areaW = this.width - PANEL_W - 16 - LIST_X;
                int areaH = (this.height - LIST_Y - 40) / ((visibleTypes.size() + 2) / 3);
                int colW = (areaW - (numCols - 1) * 6) / numCols;
                int ci = draggingColumnIndex;
                int row = ci / 3; int col = ci % 3;
                int colY = LIST_Y + row * (areaH + 6);
                int itemAreaY = colY + 16 + 2;
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
        draggingScrollbar = false;
        draggingColumnIndex = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inAttachmentEditor()) {
            // Find which column mouse is over and scroll it
            List<AttachmentType> visibleTypes = getVisibleTypes();
            int numCols = Math.min(visibleTypes.size(), 3);
            int areaW = this.width - PANEL_W - 16 - LIST_X;
            int areaH = (visibleTypes.isEmpty() ? 1 : (this.height - LIST_Y - 40) / ((visibleTypes.size() + 2) / 3));
            int colW = numCols > 0 ? (areaW - (numCols - 1) * 6) / numCols : areaW;
            for (int ci = 0; ci < visibleTypes.size(); ci++) {
                int row = ci / 3; int col = ci % 3;
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
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) delta, Math.max(0, size - VISIBLE_ROWS)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && inAttachmentEditor()) { exitEditor(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    // Attachment type helper
    private AttachmentType getAttachmentType(ResourceLocation attId) {
        try {
            ItemStack s = buildAttachmentStack(attId);
            if (!s.isEmpty() && s.getItem() instanceof IAttachment iAtt) return iAtt.getType(s);
        } catch (Exception ignored) {}
        return null;
    }

    // Category helpers
    private String getCategoryLabel(ResourceLocation id) {
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
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}