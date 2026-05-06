package com.frosty.bedgunwars.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;

@OnlyIn(Dist.CLIENT)
public class KillstreakClientProxy {
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new KillstreakHudRenderer());
        MinecraftForge.EVENT_BUS.register(new AirSupportMapScreen());
        MinecraftForge.EVENT_BUS.register(new ClientJetManager());
    }
}