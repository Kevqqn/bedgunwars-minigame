package com.frosty.bedgunwars.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.List;

public class PlayerSnapshot {
    private final double x, y, z;
    private final float yaw, pitch;
    private final GameType gameType;
    private final List<ItemStack> inventory;
    private final int selectedSlot;

    private PlayerSnapshot(double x, double y, double z, float yaw, float pitch, GameType gameType, List<ItemStack> inventory, int selectedSlot) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.gameType = gameType;
        this.inventory = inventory;
        this.selectedSlot = selectedSlot;
    }

    // Captures the player's current state (position, game mode, inventory, etc.)
    public static PlayerSnapshot capture(ServerPlayer player) {
        return new PlayerSnapshot(
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer(),
                List.copyOf(player.getInventory().items), player.getInventory().selected
        );
    }

    // Restores the player's state (teleport, inventory, game mode, etc.)
    public void restore(ServerPlayer player) {
        player.teleportTo(player.level, x, y, z, yaw, pitch);  // Correct method for Forge 1.20.1
        player.setGameMode(gameType);
        player.getInventory().clearContent();
        for (int i = 0; i < inventory.size(); i++) {
            player.getInventory().setItem(i, inventory.get(i).copy());
        }
        player.getInventory().selected = selectedSlot;
    }
}