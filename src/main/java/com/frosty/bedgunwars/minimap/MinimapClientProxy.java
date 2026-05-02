package com.frosty.bedgunwars.minimap;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinimapClientProxy {

    public static void onGameStartClient(int beaconX, int beaconZ, int radius) {
        if (MinimapRenderer.isStarted()) return;
        MinimapRenderer.startWithParams(beaconX, beaconZ, radius);
    }

    public static void onGameEnd() {
        MinimapRenderer.onGameEnd();
    }
}