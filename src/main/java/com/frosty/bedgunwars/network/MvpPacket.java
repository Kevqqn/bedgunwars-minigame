package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.client.MvpCutsceneClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class MvpPacket {

    public final UUID winnerUUID;
    public final String winnerName;
    public final int kills;
    public final long startTick;
    public final boolean isSlim;
    public final boolean cameraEnabled;

    public MvpPacket(UUID winnerUUID, String winnerName, int kills, long startTick, boolean isSlim, boolean cameraEnabled) {
        this.winnerUUID = winnerUUID;
        this.winnerName = winnerName;
        this.kills = kills;
        this.startTick = startTick;
        this.isSlim = isSlim;
        this.cameraEnabled = cameraEnabled;
    }

    public static void encode(MvpPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.winnerUUID);
        buf.writeUtf(pkt.winnerName);
        buf.writeInt(pkt.kills);
        buf.writeLong(pkt.startTick);
        buf.writeBoolean(pkt.isSlim);
        buf.writeBoolean(pkt.cameraEnabled);
    }

    public static MvpPacket decode(FriendlyByteBuf buf) {
        UUID winnerUUID = buf.readUUID();
        String winnerName = buf.readUtf();
        int kills = buf.readInt();
        long startTick = buf.readLong();
        boolean isSlim = buf.readBoolean();
        boolean cameraEnabled = buf.readBoolean();
        return new MvpPacket(winnerUUID, winnerName, kills, startTick, isSlim, cameraEnabled);
    }

    public static void handle(MvpPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            MvpCutsceneClient.start(pkt.winnerUUID, pkt.winnerName, pkt.kills, pkt.startTick, pkt.isSlim);
            MvpCutsceneClient.setCameraEnabled(pkt.cameraEnabled);
        });
        ctx.get().setPacketHandled(true);
    }
}