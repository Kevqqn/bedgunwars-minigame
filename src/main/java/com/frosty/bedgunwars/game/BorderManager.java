package com.frosty.bedgunwars.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

public class BorderManager {
    public static void applyBorder(GameSession session) {
        ServerLevel level = session.getLevel();
        WorldBorder border = level.getWorldBorder();

        double centerX = session.getBeaconPos().getX() + 0.5;
        double centerZ = session.getBeaconPos().getZ() + 0.5;
        double size = session.getBorderRadius() * 2.0;

        border.setCenter(centerX, centerZ);
        border.setSize(size);
    }
}