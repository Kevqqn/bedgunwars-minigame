package com.frosty.bedgunwars.network;

import com.frosty.bedgunwars.client.TabStatsScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class TabStatsPacket {

    public static final byte BED_INTACT   = 0;
    public static final byte BED_BROKEN   = 1;
    public static final byte BED_NONE     = 2;

    public final String gameMode;       // "SOLO" or "TEAMS"
    public final List<PlayerEntry> players;

    public TabStatsPacket(String gameMode, List<PlayerEntry> players) {
        this.gameMode = gameMode;
        this.players = players;
    }

    public static class PlayerEntry {
        public final UUID uuid;
        public final String name;
        public final String team;   // null for SOLO
        public final int teamColor; // ARGB
        public final int kills;
        public final int deaths;
        public final int money;
        public final byte bedStatus;
        public final boolean alive;

        public PlayerEntry(UUID uuid, String name, String team, int teamColor,
                           int kills, int deaths, int money, byte bedStatus, boolean alive) {
            this.uuid = uuid;
            this.name = name;
            this.team = team;
            this.teamColor = teamColor;
            this.kills = kills;
            this.deaths = deaths;
            this.money = money;
            this.bedStatus = bedStatus;
            this.alive = alive;
        }
    }

    public static void encode(TabStatsPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.gameMode);
        buf.writeInt(pkt.players.size());
        for (PlayerEntry e : pkt.players) {
            buf.writeUUID(e.uuid);
            buf.writeUtf(e.name);
            buf.writeBoolean(e.team != null);
            if (e.team != null) buf.writeUtf(e.team);
            buf.writeInt(e.teamColor);
            buf.writeInt(e.kills);
            buf.writeInt(e.deaths);
            buf.writeInt(e.money);
            buf.writeByte(e.bedStatus);
            buf.writeBoolean(e.alive);
        }
    }

    public static TabStatsPacket decode(FriendlyByteBuf buf) {
        String mode = buf.readUtf();
        int count = buf.readInt();
        List<PlayerEntry> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID uuid = buf.readUUID();
            String name = buf.readUtf();
            String team = buf.readBoolean() ? buf.readUtf() : null;
            int teamColor = buf.readInt();
            int kills = buf.readInt();
            int deaths = buf.readInt();
            int money = buf.readInt();
            byte bedStatus = buf.readByte();
            boolean alive = buf.readBoolean();
            players.add(new PlayerEntry(uuid, name, team, teamColor,
                    kills, deaths, money, bedStatus, alive));
        }
        return new TabStatsPacket(mode, players);
    }

    public static void handle(TabStatsPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> TabStatsScreen.updateCache(pkt));
        ctx.get().setPacketHandled(true);
    }
}