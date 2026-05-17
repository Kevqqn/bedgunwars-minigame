package com.frosty.bedgunwars.game;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import com.frosty.bedgunwars.network.PacketHandler;
import com.frosty.bedgunwars.network.ShowTipPacket;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TipsManager {

    private static Map<String, String> tips = null;

    public static void load() {
        try (InputStream is = TipsManager.class.getResourceAsStream("/assets/bedgunwars/tips.json")) {
            if (is == null) {
                System.err.println("[TipsManager] tips.json not found");
                return;
            }
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            tips = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            System.err.println("[TipsManager] Failed to load tips.json: " + e.getMessage());
        }
    }

    public static void sendTip(ServerPlayer player, String tipId) {
        if (tips == null) return;
        String text = tips.get(tipId);
        if (text == null) return;
        PacketHandler.sendToClient(player, new ShowTipPacket(text));
    }

    public static void sendTip(ServerPlayer player, String tipId, String... substitutions) {
        if (tips == null) return;
        String text = tips.get(tipId);
        if (text == null) return;
        for (int i = 0; i + 1 < substitutions.length; i += 2) {
            text = text.replace(substitutions[i], substitutions[i + 1]);
        }
        PacketHandler.sendToClient(player, new ShowTipPacket(text));
    }
}