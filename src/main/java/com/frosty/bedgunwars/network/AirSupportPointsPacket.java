package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class AirSupportPointsPacket {
    public final List<double[]> points;
    public AirSupportPointsPacket(List<double[]> p) { points = p; }

    public static void encode(AirSupportPointsPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.points.size());
        for (double[] pt : p.points) { buf.writeDouble(pt[0]); buf.writeDouble(pt[1]); }
    }

    public static AirSupportPointsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<double[]> pts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) pts.add(new double[]{buf.readDouble(), buf.readDouble()});
        return new AirSupportPointsPacket(pts);
    }

    public static void handle(AirSupportPointsPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null) return;
        // Schedule on server thread to ensure getTickCount() is accurate
        player.getServer().execute(() -> {
            if (!GameManager.hasGame()) return;
            GameSession session = GameManager.getSession();
            if (session == null) return;
            session.getKillstreakManager().fireAirSupport(
                    player.getUUID(), pkt.points, player.getServer(), session);
        });
    }
}