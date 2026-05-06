package com.frosty.bedgunwars.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

public class BorderManager {
    public static void applyBorder(GameSession session) {
        ServerLevel level = session.getLevel();
        WorldBorder border = level.getWorldBorder();

        session.captureBorderState();

        double centerX = session.getBeaconPos().getX() + 0.5;
        double centerZ = session.getBeaconPos().getZ() + 0.5;
        double size = session.getBorderRadius() * 2.0;

        border.setCenter(centerX, centerZ);
        border.setSize(size);

        // Manually sync border to all players in this dimension
        net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket packet =
                new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(border);
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            p.connection.send(packet);
        }
    }

    private void shrinkBorder(GameSession session, double targetSize, int durationSeconds) {
        WorldBorder border = session.getLevel().getWorldBorder();
        double currentSize = border.getSize();
        double newSize = Math.max(10, currentSize - (targetSize * 2.0));
        border.lerpSizeBetween(currentSize, newSize, durationSeconds * 1000L);

        // sync player border shrink visually
        net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket packet =
                new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(border);
        for (net.minecraft.server.level.ServerPlayer p : session.getLevel().players()) {
            p.connection.send(packet);
        }
    }

    public static void restoreBorder(GameSession session) {
        if (!session.hasBorderSnapshot()) {
            return;
        }

        ServerLevel level = session.getLevel();
        WorldBorder border = level.getWorldBorder();
        border.setCenter(session.getOriginalBorderCenterX(), session.getOriginalBorderCenterZ());
        border.setSize(session.getOriginalBorderSize());

        net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket packet =
                new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(border);
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            p.connection.send(packet);
        }
    }
}