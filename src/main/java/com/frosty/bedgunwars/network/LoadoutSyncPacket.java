package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.LoadoutManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LoadoutSyncPacket {

    public final List<LoadoutManager.Loadout> loadouts;

    public LoadoutSyncPacket(List<LoadoutManager.Loadout> loadouts) {
        this.loadouts = loadouts;
    }

    public static void encode(LoadoutSyncPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.loadouts.size());
        for (LoadoutManager.Loadout l : p.loadouts) {
            buf.writeUtf(l.name);
            buf.writeInt(l.guns.size());
            for (String g : l.guns) buf.writeUtf(g);
            buf.writeInt(l.attachments.size());
            for (var slotEntry : l.attachments.entrySet()) {
                buf.writeInt(slotEntry.getKey());
                buf.writeInt(slotEntry.getValue().size());
                for (var e : slotEntry.getValue().entrySet()) {
                    buf.writeUtf(e.getKey()); buf.writeUtf(e.getValue());
                }
            }
            buf.writeInt(l.throwables.size());
            for (String t : l.throwables) buf.writeUtf(t);
        }
    }

    public static LoadoutSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<LoadoutManager.Loadout> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf();
            int gc = buf.readInt();
            List<String> guns = new ArrayList<>();
            for (int j = 0; j < gc; j++) guns.add(buf.readUtf());
            int ac = buf.readInt();
            java.util.Map<Integer, java.util.Map<String, String>> atts = new java.util.HashMap<>();
            for (int j = 0; j < ac; j++) {
                int slot = buf.readInt();
                int tc = buf.readInt();
                java.util.Map<String, String> typeMap = new java.util.HashMap<>();
                for (int k = 0; k < tc; k++) typeMap.put(buf.readUtf(), buf.readUtf());
                atts.put(slot, typeMap);
            }
            int thc = buf.readInt();
            List<String> throwables = new ArrayList<>();
            for (int j = 0; j < thc; j++) throwables.add(buf.readUtf());
            list.add(new LoadoutManager.Loadout(name, guns, atts, throwables));
        }
        return new LoadoutSyncPacket(list);
    }

    public static void handle(LoadoutSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                com.frosty.bedgunwars.client.GunSelectionScreen.updateLoadouts(pkt.loadouts));
        ctx.get().setPacketHandled(true);
    }

    public static void send(ServerPlayer player, List<LoadoutManager.Loadout> loadouts) {
        PacketHandler.sendToClient(player, new LoadoutSyncPacket(loadouts));
    }
}