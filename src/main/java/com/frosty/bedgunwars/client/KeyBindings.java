package com.frosty.bedgunwars.client;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.network.OpenGunMenuPacket;
import com.frosty.bedgunwars.network.RequestGunMenuPacket;
import com.frosty.bedgunwars.network.PacketHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class KeyBindings {

    public static final KeyMapping GUN_MENU_KEY = new KeyMapping(
            "key.bedgunwars.gun_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.bedgunwars"
    );

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (GUN_MENU_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            // Send request to server to open the menu (server sends back the packet with gun list)
            PacketHandler.CHANNEL.sendToServer(new RequestGunMenuPacket());
        }
    }
}