package com.frosty.bedgunwars.game;

import com.frosty.bedgunwars.network.PacketHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import com.frosty.bedgunwars.game.SoundHelper;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WinManager {

    public static void checkWinner(GameSession session) {
        if (session == null || !session.isActive()) return;
        if (session.getPhase() != GamePhase.ACTIVE && session.getPhase() != GamePhase.ENDING) return;

        // Safeguard: single-player testing, don't auto-end immediately
        if (session.getMatchStartPlayerCount() <= 1) return;

        if (session.getMode() == GameModeType.SOLO) {
            checkSoloWinner(session);
        } else {
            checkTeamWinner(session);
        }
    }

    private static void checkSoloWinner(GameSession session) {
        UUID lastAlive = null;
        int aliveCount = 0;

        for (UUID uuid : session.getPlayers()) {
            if (session.isEliminated(uuid)) continue;
            if (session.isOffline(uuid)) continue; // don't count offline players as alive for win check
            aliveCount++;
            lastAlive = uuid;
            if (aliveCount > 1) return;
        }

        if (aliveCount == 1 && lastAlive != null) {
            String winnerName = session.getPlayerTeam(lastAlive);
            if (winnerName == null) winnerName = "Unknown";
            announceWinner(session, winnerName);
        }
    }

    private static void checkTeamWinner(GameSession session) {
        Set<String> aliveTeams = new HashSet<>();

        for (UUID uuid : session.getPlayers()) {
            if (session.isEliminated(uuid)) continue;
            if (session.isOffline(uuid)) continue;
            String team = session.getPlayerTeam(uuid);
            if (team != null) aliveTeams.add(team);
            if (aliveTeams.size() > 1) return;
        }

        if (aliveTeams.size() == 1) {
            String winnerTeam = aliveTeams.iterator().next() + " Team";
            announceWinner(session, winnerTeam);
        }
    }

    public static void announceWinner(GameSession session, String winnerName) {
        session.setWinner(winnerName);
        UUID mvpUUID = null;
        for (UUID uuid : session.getPlayers()) {
            ServerPlayer p = session.getLevel().getServer().getPlayerList().getPlayer(uuid);
            if (p != null && p.getName().getString().equals(winnerName)) {
                mvpUUID = uuid;
                break;
            }
        }
        session.setWinnerUUID(mvpUUID);
        // MvpCutsceneManager.start() is called from GameTickHandler after title is shown
        // Start skin prefetch on clients immediately — gives full title display time to fetch
        PacketHandler.sendToAllClients(session.getLevel().getServer(),
                new com.frosty.bedgunwars.network.MvpSkinPrefetchPacket(
                        mvpUUID != null ? mvpUUID : new java.util.UUID(0, 0),
                        winnerName));
        for (UUID uuid : session.getPlayers()) {
            ServerPlayer player = session.getLevel().getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            sendTitle(player, winnerName + " won the game!", "");
            SoundHelper.playLevelUp(player);
        }
        SoundHelper.playToAll(session.getLevel().getServer(),
                com.frosty.bedgunwars.BedGunWars.GAME_END_MUSIC.get(), 4.0f);
        ServerPlayer winner = null;
        for (UUID uuid : session.getPlayers()) {
            ServerPlayer p = session.getLevel().getServer().getPlayerList().getPlayer(uuid);
            if (p != null && p.getName().getString().equals(winnerName)) { winner = p; break; }
        }
        if (winner != null) launchFirework(winner);
    }

    private static void launchFirework(ServerPlayer player) {
        Random random = new Random();
        int[] shapes = {0, 1, 2, 3, 4};
        int shape = shapes[random.nextInt(shapes.length)];

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag fireworks = new CompoundTag();
        ListTag explosions = new ListTag();
        CompoundTag explosion = new CompoundTag();
        explosion.putByte("Type", (byte) shape);
        explosion.putBoolean("Flicker", random.nextBoolean());
        explosion.putBoolean("Trail", random.nextBoolean());
        int[] colors = new int[random.nextInt(3) + 1];
        for (int i = 0; i < colors.length; i++) colors[i] = random.nextInt(0xFFFFFF);
        explosion.putIntArray("Colors", colors);
        explosions.add(explosion);
        fireworks.put("Explosions", explosions);
        fireworks.putByte("Flight", (byte) 1);
        rocket.getOrCreateTag().put("Fireworks", fireworks);

        FireworkRocketEntity firework = new FireworkRocketEntity(
                player.level(), player.getX(), player.getY(), player.getZ(), rocket);
        player.level().addFreshEntity(firework);
    }

    public static void sendTitle(ServerPlayer player, String title, String subtitle) {
        // Timing: fadeIn=10, stay=70, fadeOut=20 ticks
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
    }
    // Deathmatch win checks
    public static void checkDeathmatchWinner(GameSession session) {
        if (session == null || !session.isActive()) return;
        if (session.getPhase() != GamePhase.ACTIVE) return;
        if (session.getMatchStartPlayerCount() <= 1) return;

        String winner = session.getDeathmatchManager().checkKillLimitWinner(session.getKillLimit());
        if (winner == null) return;

        // Resolve display name
        String displayName = resolveDisplayName(session, winner);
        announceWinner(session, displayName);
    }

    public static void checkDeathmatchTimerWinner(GameSession session) {
        if (session == null || !session.isActive()) return;

        String winner = session.getDeathmatchManager().getMostKillsWinner();
        String displayName = winner != null ? resolveDisplayName(session, winner) : "Nobody";
        announceWinner(session, displayName);
    }
    
    private static String resolveDisplayName(GameSession session, String key) {
        if (session.getMode() == GameModeType.DEATHMATCH_SOLO) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(key);
                net.minecraft.server.level.ServerPlayer p =
                        session.getLevel().getServer().getPlayerList().getPlayer(uuid);
                return p != null ? p.getName().getString() : key;
            } catch (IllegalArgumentException e) {
                return key;
            }
        }
        return key + " Team";
    }

}