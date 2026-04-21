package com.frosty.bedgunwars.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

public class BossBarManager {

    private static ServerBossEvent bossBar = null;

    public static void show(MinecraftServer server, String title, float progress) {
        if (bossBar == null) {
            bossBar = new ServerBossEvent(
                    Component.literal(title),
                    BossEvent.BossBarColor.GREEN,
                    BossEvent.BossBarOverlay.PROGRESS
            );
        }
        bossBar.setName(Component.literal(title));
        bossBar.setProgress(Math.max(0f, Math.min(1f, progress)));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            bossBar.addPlayer(player);
        }
    }

    public static void remove(MinecraftServer server) {
        if (bossBar == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            bossBar.removePlayer(player);
        }
        bossBar = null;
    }

    public static void addPlayer(ServerPlayer player) {
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }
}