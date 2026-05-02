package com.frosty.bedgunwars.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.frosty.bedgunwars.game.GameModeType;

import java.util.UUID;

public class BedUpgradeMenu {

    public static void open(ServerPlayer player, GameSession session) {
        String team = getTeamKey(player, session);
        BedUpgradeManager mgr = session.getBedUpgradeManager();

        SimpleContainer container = new SimpleContainer(27) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return false; // prevent placing items
            }
        };

        // Fill slots
        fillSlots(container, mgr, team, player, session);

        // Open as chest
        player.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("§6§lBed Upgrades");
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int id, net.minecraft.world.entity.player.Inventory inv,
                    net.minecraft.world.entity.player.Player p) {
                return new ChestMenu(MenuType.GENERIC_9x3, id, inv, container, 3) {
                    @Override
                    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                        return true;
                    }

                    @Override
                    public void clicked(int slotId, int dragType,
                                        net.minecraft.world.inventory.ClickType clickType,
                                        net.minecraft.world.entity.player.Player p) {
                        if (slotId < 0 || slotId >= 27) return;
                        if (!(p instanceof ServerPlayer sp)) return;
                        handleUpgradeClick(slotId, sp, session, mgr, team, container);
                    }
                };
            }
        });
    }

    private static void fillSlots(SimpleContainer container, BedUpgradeManager mgr,
                                  String team, ServerPlayer player, GameSession session) {
        BedUpgradeManager.UpgradeType[] types = BedUpgradeManager.UpgradeType.values();

        // Slot layout
        int[] upgradeSlots = {0, 1, 2, 3, 9, 10, 11, 12, 18}; // row1: 0-3, row2: 9-12, row3: 18

        for (int i = 0; i < upgradeSlots.length; i++) {
            BedUpgradeManager.UpgradeType type = types[i];
            int currentTier = mgr.getTier(team, type);
            int nextTier = currentTier + 1;
            int maxTier = mgr.getMaxTier(type);

            ItemStack display = BedUpgradeManager.getDisplayItem(type, currentTier);
            String tierText = currentTier == 0 ? "§7Not purchased" : "§aTier " + currentTier;
            String nextText;
            if (currentTier >= maxTier) {
                nextText = "§aMAX";
            } else {
                int price = mgr.getPrice(type, nextTier);
                nextText = "§eNext: §f$" + price;
            }
            int money = session.getMoney(player.getUUID());
            String moneyText = "§7Your money: §a$" + money;

            display.setHoverName(Component.literal("§e" + formatName(type)));
            net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
            lore.add(net.minecraft.nbt.StringTag.valueOf(
                    net.minecraft.network.chat.Component.Serializer.toJson(
                            Component.literal(tierText))));
            lore.add(net.minecraft.nbt.StringTag.valueOf(
                    net.minecraft.network.chat.Component.Serializer.toJson(
                            Component.literal(nextText))));
            lore.add(net.minecraft.nbt.StringTag.valueOf(
                    net.minecraft.network.chat.Component.Serializer.toJson(
                            Component.literal(moneyText))));
            display.getOrCreateTagElement("display").put("Lore", lore);

            container.setItem(upgradeSlots[i], display);
        }

        // Buyable items — slot 25 = End Stone, slot 26 = Stone
        ItemStack endStone = new ItemStack(Items.END_STONE, 16);
        endStone.setHoverName(Component.literal("§eEnd Stone x16"));
        addLore(endStone, "§7Cost: §a$150", "§7Click to buy");
        container.setItem(25, endStone);

        ItemStack stone = new ItemStack(Items.STONE, 32);
        stone.setHoverName(Component.literal("§eStone x32"));
        addLore(stone, "§7Cost: §a$100", "§7Click to buy");
        container.setItem(26, stone);

        // Fill empty slots with gray glass
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.setHoverName(Component.literal(" "));
        for (int i = 0; i < 27; i++) {
            if (container.getItem(i).isEmpty()) container.setItem(i, filler);
        }
    }

    private static void handleUpgradeClick(int slotId, ServerPlayer player,
                                           GameSession session, BedUpgradeManager mgr, String team,
                                           SimpleContainer container) {
        BedUpgradeManager.UpgradeType[] types = BedUpgradeManager.UpgradeType.values();
        int[] upgradeSlots = {0, 1, 2, 3, 9, 10, 11, 12, 18};

        // Check if it's an upgrade slot
        for (int i = 0; i < upgradeSlots.length; i++) {
            if (upgradeSlots[i] == slotId) {
                BedUpgradeManager.UpgradeType type = types[i];
                purchaseUpgrade(player, session, mgr, team, type, container, upgradeSlots);
                return;
            }
        }

        // Buyable items
        if (slotId == 25) buyItem(player, session, new ItemStack(Items.END_STONE, 16), 150, container, upgradeSlots, mgr, team);
        if (slotId == 26) buyItem(player, session, new ItemStack(Items.STONE, 32), 100, container, upgradeSlots, mgr, team);
    }

    private static void purchaseUpgrade(ServerPlayer player, GameSession session,
                                        BedUpgradeManager mgr, String team, BedUpgradeManager.UpgradeType type,
                                        SimpleContainer container, int[] upgradeSlots) {
        int currentTier = mgr.getTier(team, type);
        int maxTier = mgr.getMaxTier(type);
        if (currentTier >= maxTier) {
            player.sendSystemMessage(Component.literal("§cThis upgrade is already at max tier!"));
            return;
        }
        int nextTier = currentTier + 1;
        int price = mgr.getPrice(type, nextTier);
        if (!session.spendMoney(player.getUUID(), price)) {
            player.sendSystemMessage(Component.literal("§cNot enough money! Need §e$" + price));
            return;
        }
        mgr.setTier(team, type, nextTier);
        applyUpgrade(player, session, team, type, nextTier);
        player.sendSystemMessage(Component.literal("§a✔ " + formatName(type) + " upgraded to Tier " + nextTier + "!"));
        // Refresh chest
        fillSlots(container, mgr, team, player, session);
    }

    private static void buyItem(ServerPlayer player, GameSession session,
                                ItemStack item, int cost, SimpleContainer container,
                                int[] upgradeSlots, BedUpgradeManager mgr, String team) {
        if (!session.spendMoney(player.getUUID(), cost)) {
            player.sendSystemMessage(Component.literal("§cNot enough money! Need §e$" + cost));
            return;
        }
        player.getInventory().add(item.copy());
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal("§aBought " + item.getCount() + "x " + item.getHoverName().getString() + "!"));
        fillSlots(container, mgr, team, player, session);
    }

    private static void applyUpgrade(ServerPlayer player, GameSession session,
                                     String team, BedUpgradeManager.UpgradeType type, int tier) {
        switch (type) {
            case PICKAXE -> {
                // Replace pickaxe in inventory
                replacePickaxe(player, tier);
            }
            case SPEED -> {
                // Apply to all team members
                applyToTeam(player, session, team, p ->
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0, false, false)));
            }
            case AXE -> {
                // Give axe
                ItemStack axe = BedUpgradeManager.getAxeForTier(tier);
                player.getInventory().add(axe);
            }
            case TP_TO_BED -> {
                // Give compass to all team members
                applyToTeam(player, session, team, p ->
                        p.getInventory().add(BedUpgradeManager.makeBedTeleportCompass()));
            }
            default -> {} // passive upgrades handled in tick
        }
    }

    private static void replacePickaxe(ServerPlayer player, int tier) {
        ItemStack newPick = BedUpgradeManager.getPickaxeForTier(tier);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.PickaxeItem) {
                player.getInventory().setItem(i, newPick);
                player.containerMenu.broadcastChanges();
                return;
            }
        }
        // No pickaxe found, just add it
        player.getInventory().add(newPick);
    }

    private static void applyToTeam(ServerPlayer buyer, GameSession session,
                                    String team, java.util.function.Consumer<ServerPlayer> action) {
        for (UUID uuid : session.getPlayers()) {
            if (!team.equals(getTeamKey(session.getLevel().getServer()
                    .getPlayerList().getPlayer(uuid), session))) continue;
            ServerPlayer p = session.getLevel().getServer().getPlayerList().getPlayer(uuid);
            if (p != null) action.accept(p);
        }
    }

    public static String getTeamKey(ServerPlayer player, GameSession session) {
        if (session.getMode() == GameModeType.SOLO) {
            return player.getUUID().toString();
        }
        String team = session.getPlayerTeam(player.getUUID());
        return team != null ? team : player.getUUID().toString();
    }

    private static void addLore(ItemStack stack, String... lines) {
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        for (String line : lines) {
            lore.add(net.minecraft.nbt.StringTag.valueOf(
                    net.minecraft.network.chat.Component.Serializer.toJson(
                            Component.literal(line))));
        }
        stack.getOrCreateTagElement("display").put("Lore", lore);
    }

    private static String formatName(BedUpgradeManager.UpgradeType type) {
        return switch (type) {
            case MINING_FATIGUE -> "Mining Fatigue Trap";
            case ALARM          -> "Bed Alarm";
            case SLOWNESS       -> "Slowness Trap";
            case TP_TO_BED      -> "TP To Bed";
            case PICKAXE        -> "Pickaxe Upgrade";
            case SPEED          -> "Speed Boost";
            case AXE            -> "Axe Upgrade";
            case BED_SENSE      -> "Bed Sense";
            case HEALING_STATION -> "Healing Station";
        };
    }

    private static java.util.UUID getUUID(ServerPlayer player) {
        return player.getUUID();
    }
}