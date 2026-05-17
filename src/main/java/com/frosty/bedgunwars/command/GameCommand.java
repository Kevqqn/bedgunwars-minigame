package com.frosty.bedgunwars.command;

import com.frosty.bedgunwars.game.*;
import com.frosty.bedgunwars.network.PacketHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class GameCommand {

    public static int startGame(CommandSourceStack source, GameModeType mode, int teamCount) {
        if (GameManager.hasGame()) { source.sendFailure(Component.literal("Game already running")); return 0; }
        if (!(source.getEntity() instanceof ServerPlayer player)) { source.sendFailure(Component.literal("Must be a player")); return 0; }

        ServerLevel level = player.serverLevel();
        BlockPos beacon = findNearestBeacon(level, player.blockPosition(), 10);
        if (beacon == null) { source.sendFailure(Component.literal("No beacon nearby")); return 0; }

        GameSession session = new GameSession(level, beacon, mode, player.getUUID());
        session.setPhase(GamePhase.STARTING);
        session.setTeamCount(teamCount);
        ExcludedGunsConfig.load();
        GameManager.start(session);
        session.addJoinedPlayer(player.getUUID());

        source.sendSuccess(() -> Component.literal("Game created in " + mode.name() + " mode."), true);
        source.sendSuccess(() -> Component.literal("You are the host. Other players can now use /game join."), false);
        source.sendSuccess(() -> Component.literal("Set border with /game border <size>"), false);
        source.getServer().getPlayerList().getPlayers().forEach(p ->
                p.sendSystemMessage(Component.literal("§6[NOTICE] §eA game has been started. Type §f/game join §eto participate.")));
        return 1;
    }

    public static int startDeathmatch(CommandSourceStack source, GameModeType mode, int teamCount) {
        if (GameManager.hasGame()) { source.sendFailure(Component.literal("Game already running")); return 0; }
        if (!(source.getEntity() instanceof ServerPlayer player)) { source.sendFailure(Component.literal("Must be a player")); return 0; }

        GameSession session = new GameSession(player.serverLevel(), player.blockPosition(), mode, player.getUUID());
        session.setPhase(GamePhase.STARTING);
        session.setTeamCount(teamCount);
        ExcludedGunsConfig.load();
        GameManager.start(session);
        session.addJoinedPlayer(player.getUUID());
        source.sendSuccess(() -> Component.literal("Deathmatch created in " + mode.name() + " mode."), true);
        source.sendSuccess(() -> Component.literal("Players can /game join. Then /game border <size> to detect beacons."), false);
        source.getServer().getPlayerList().getPlayers().forEach(p ->
                p.sendSystemMessage(Component.literal("§6[NOTICE] §eA deathmatch has been started. Type §f/game join §eto participate.")));
        return 1;
    }

    public static int setWinKills(CommandSourceStack source, int kills) {
        if (!GameManager.hasGame()) { source.sendFailure(Component.literal("No active game")); return 0; }
        if (kills < 10) { source.sendFailure(Component.literal("Kill limit must be at least 10")); return 0; }
        if (!isHost(source)) { source.sendFailure(Component.literal("Only the host can set the kill limit")); return 0; }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) { source.sendFailure(Component.literal("No active game")); return 0; }
        if (!session.isDeathmatch()) { source.sendFailure(Component.literal("This command is only for deathmatch modes")); return 0; }
        if (session.getPhase() != GamePhase.STARTING) { source.sendFailure(Component.literal("Kill limit can only be set during STARTING phase")); return 0; }
        session.setKillLimit(kills);
        source.sendSuccess(() -> Component.literal("Kill limit set to " + kills + ". Now run /game prep <seconds>."), true);
        return 1;
    }

    public static int joinGame(CommandSourceStack source) {
        if (!GameManager.hasGame()) { source.sendFailure(Component.literal("No active game to join")); return 0; }
        if (!(source.getEntity() instanceof ServerPlayer player)) { source.sendFailure(Component.literal("Must be a player")); return 0; }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) { source.sendFailure(Component.literal("No active game to join")); return 0; }
        if (session.getPhase() != GamePhase.STARTING && session.getPhase() != GamePhase.WAITING_PLAYERS) {
            source.sendFailure(Component.literal("You can only join before preparation starts")); return 0;
        }
        if (!player.serverLevel().dimension().equals(session.getLevel().dimension())) {
            source.sendFailure(Component.literal("You must be in the same dimension as the host to join")); return 0;
        }
        if (!session.addJoinedPlayer(player.getUUID())) { source.sendFailure(Component.literal("You are already in the lobby")); return 0; }
        String playerName = player.getName().getString();
        source.getServer().getPlayerList().getPlayers().forEach(p ->
                p.sendSystemMessage(Component.literal("§6[NOTICE] §f" + playerName + " §ehas joined the game.")));

        if (session.getPhase() == GamePhase.WAITING_PLAYERS) {
            int secsLeft = session.getWaitingPlayersTicks() / 20;
            int online = source.getServer().getPlayerList().getPlayers().size();
            int joined = session.getJoinedPlayers().size();
            player.sendSystemMessage(Component.literal(
                    "§7Match starts in §e" + secsLeft + "s §7(" + joined + "/" + online + " joined). Use §f/game forcestart §7to skip."));
        }
        return 1;
    }

    public static int leaveGame(CommandSourceStack source) {
        if (!GameManager.hasGame()) { source.sendFailure(Component.literal("No active game to leave")); return 0; }
        if (!(source.getEntity() instanceof ServerPlayer player)) { source.sendFailure(Component.literal("Must be a player")); return 0; }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) { source.sendFailure(Component.literal("No active game to leave")); return 0; }
        if (session.getPhase() != GamePhase.STARTING && session.getPhase() != GamePhase.WAITING_PLAYERS) {
            source.sendFailure(Component.literal("You can only leave before preparation starts")); return 0;
        }
        if (player.getUUID().equals(session.getHostUuid())) { source.sendFailure(Component.literal("Host cannot leave the lobby. Use /game stop instead.")); return 0; }
        if (!session.removeJoinedPlayer(player.getUUID())) { source.sendFailure(Component.literal("You are not in the lobby")); return 0; }
        source.sendSuccess(() -> Component.literal("You left the game lobby"), false);
        return 1;
    }

    public static int setBorder(CommandSourceStack source, int size) {
        if (!GameManager.hasGame()) { source.sendFailure(Component.literal("No active game")); return 0; }
        if (size <= 10) { source.sendFailure(Component.literal("Border size must be greater than 10")); return 0; }
        if (!isHost(source)) { source.sendFailure(Component.literal("Only the host can set the border")); return 0; }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) { source.sendFailure(Component.literal("No active game")); return 0; }
        if (session.getPhase() != GamePhase.STARTING) { source.sendFailure(Component.literal("Border can only be set during STARTING phase")); return 0; }

        session.setBorderRadius(size);
        BorderManager.applyBorder(session);
        source.sendSuccess(() -> Component.literal("Border radius set to " + size), true);

        if (session.isDeathmatch()) {
            List<net.minecraft.core.BlockPos> beacons = findBeaconsInBorder(session);
            if (beacons.isEmpty()) {
                String blockName = session.getMode() == GameModeType.DEATHMATCH_TEAMS ? "respawn anchors" : "beacons";
                source.sendFailure(Component.literal("No " + blockName + " found inside the border! Place them and try again."));
                return 0;
            }
            int teamCount = session.getTeamCount();
            if (session.getMode() == GameModeType.DEATHMATCH_TEAMS && beacons.size() < teamCount) {
                source.sendFailure(Component.literal("Not enough beacons for " + teamCount + " teams. Found: " + beacons.size() + ". Need at least " + teamCount + "."));
                return 0;
            }
            session.getDeathmatchManager().setAllBeacons(beacons);
            if (session.getMode() == GameModeType.DEATHMATCH_TEAMS) {
                List<net.minecraft.core.BlockPos> shuffled = new java.util.ArrayList<>(beacons);
                java.util.Collections.shuffle(shuffled);
                for (int i = 0; i < teamCount; i++) {
                    String teamName = "Team " + (i + 1);
                    session.getDeathmatchManager().assignTeamBeacon(teamName, shuffled.get(i));
                }
                StringBuilder sb = new StringBuilder("§aBeacon assignments:");
                for (int i = 0; i < teamCount; i++) {
                    String teamName = "Team " + (i + 1);
                    net.minecraft.core.BlockPos b = shuffled.get(i);
                    sb.append(TeamManager.getTeamColor(teamName)).append(teamName)
                            .append("§f -> [").append(b.getX()).append(", ").append(b.getY()).append(", ").append(b.getZ()).append("]");
                }
                final String report = sb.toString();
                source.sendSuccess(() -> Component.literal(report), false);
            } else {
                final int count = beacons.size();
                source.sendSuccess(() -> Component.literal("§aFound " + count + " respawn beacons for solo deathmatch."), false);
            }
            source.sendSuccess(() -> Component.literal("Now run /game matchtime <seconds>, /game winkills <kills>, then /game prep <seconds>"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Now let players join with /game join, then run /game prep <seconds>"), false);
        }
        return 1;
    }

    public static int setPrep(CommandSourceStack source, int seconds) {
        if (!GameManager.hasGame()) { source.sendFailure(Component.literal("No active game")); return 0; }
        if (seconds <= 0) { source.sendFailure(Component.literal("Prep time must be greater than 0")); return 0; }
        if (!(source.getEntity() instanceof ServerPlayer player)) { source.sendFailure(Component.literal("Must be a player")); return 0; }
        if (!isHost(source)) { source.sendFailure(Component.literal("Only the host can start preparation")); return 0; }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) { source.sendFailure(Component.literal("No active game")); return 0; }
        if (session.getPhase() != GamePhase.STARTING) { source.sendFailure(Component.literal("Prep can only be set during STARTING phase")); return 0; }
        if (!session.isMatchTimerSet()) { source.sendFailure(Component.literal("Set match timer with /game matchtime <seconds>")); return 0; }
        if (session.isDeathmatch() && !session.isKillLimitSet()) { source.sendFailure(Component.literal("Set kill limit with /game winkills <kills> (min 10)")); return 0; }

        List<ServerPlayer> players = resolveJoinedPlayers(player.serverLevel().getServer(), session);
        if (players.isEmpty()) { source.sendFailure(Component.literal("No valid players found to start")); return 0; }

        session.setConfiguredPrepSeconds(seconds);
        session.setPrepTimeSeconds(seconds);
        session.setPhase(GamePhase.WAITING_PLAYERS);
        session.setWaitingPlayersTicks(30 * 20);

        broadcast(player.serverLevel().getServer(), "§6[BedGunWars] §eA match is starting! Type §f/game join §eto participate. (30s)");
        source.sendSuccess(() -> Component.literal("Waiting for players — match launches in 30s, or when all online players join."), true);
        source.sendSuccess(() -> Component.literal("Use §f/game forcestart §7to skip the wait."), false);
        return 1;
    }

    public static int forceJoinAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) { source.sendFailure(Component.literal("No active game.")); return 0; }
        if (session.getPhase() != GamePhase.STARTING && session.getPhase() != GamePhase.WAITING_PLAYERS) {
            source.sendFailure(Component.literal("Can only force join during STARTING or WAITING_PLAYERS phase.")); return 0;
        }
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!session.isJoined(player.getUUID())) {
                if (!player.serverLevel().dimension().equals(session.getLevel().dimension())) continue;
                session.addJoinedPlayer(player.getUUID());
                player.sendSystemMessage(Component.literal("§aYou have been force-joined to the game lobby."));
                count++;
            }
        }
        final int c = count;
        source.sendSuccess(() -> Component.literal("§aForce-joined §e" + c + " §aplayers to the lobby."), false);
        return count;
    }

    public static int setMatchTime(CommandSourceStack source, int seconds) {
        if (!GameManager.hasGame()) { source.sendFailure(Component.literal("No active game")); return 0; }
        if (seconds <= 0) { source.sendFailure(Component.literal("Match timer must be greater than 0")); return 0; }
        if (!isHost(source)) { source.sendFailure(Component.literal("Only the host can set the match timer")); return 0; }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) { source.sendFailure(Component.literal("No active game")); return 0; }
        session.setMatchTimeSeconds(seconds);
        source.sendSuccess(() -> Component.literal("Match timer set to " + seconds + " seconds."), true);
        return 1;
    }

    public static int stopGame(CommandSourceStack source) {
        if (!GameManager.hasGame()) { source.sendFailure(Component.literal("No active game")); return 0; }
        if (!isHost(source)) { source.sendFailure(Component.literal("Only the host can stop the game")); return 0; }
        GameSession session = GameManager.getSession();
        if (session == null) { source.sendFailure(Component.literal("No active game")); return 0; }
        GameCleanupManager.restoreAndEnd(source.getServer(), session, "Game stopped. Restoring player state.");
        source.sendSuccess(() -> Component.literal("Game stopped"), true);
        return 1;
    }

    // prep launch - called by tick handler, forceStart, and setPrep
    public static void launchPreparation(MinecraftServer server, GameSession session) {
        ServerLevel level = session.getLevel();
        List<ServerPlayer> players = resolveJoinedPlayers(server, session);
        if (players.isEmpty()) {
            ServerPlayer host = server.getPlayerList().getPlayer(session.getHostUuid());
            if (host != null) players = List.of(host);
            else {
                broadcast(server, "§c[BedGunWars] No players found. Cancelling match.");
                GameCleanupManager.restoreAndEnd(server, session, "No players to start with.");
                return;
            }
        }

        session.resetMatchState();
        session.setMatchStartPlayerCount(players.size());
        for (ServerPlayer target : players) {
            session.savePlayerState(target);
            session.cachePlayerName(target.getUUID(), target.getName().getString());
        }

        assignPlayers(session, players, session.getMode());
        if (session.getMode() == GameModeType.TEAMS) TeamManager.applyScoreboardTeams(server, session);
        if (session.isDeathmatch()) teleportDeathmatchPlayers(players, session);
        else teleportPlayersToBeacon(players, level, session.getBeaconPos());
        giveStarterItems(session, players);

        session.setPhase(GamePhase.PREPARATION);

        for (ServerPlayer p : players) {
            String team = session.getPlayerTeam(p.getUUID());
            if (session.isDeathmatch()) {
                lockMovement(p);
                if (team != null && session.getMode() == GameModeType.DEATHMATCH_TEAMS) {
                    String color = TeamManager.getTeamColor(team);
                    String teammates = buildTeammateList(p, team, session, server);
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                    p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(color + "▶ " + team)));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Teammates: §f" + teammates + " §7| Pick your loadout!")));
                } else {
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                    p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§ePreparation Phase")));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Pick your loadout!")));
                }
            } else if (team != null && session.getMode() == GameModeType.TEAMS) {
                String color = TeamManager.getTeamColor(team);
                String teammates = buildTeammateList(p, team, session, server);
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(color + "▶ " + team)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Teammates: §f" + teammates)));
            } else {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§ePreparation Phase")));
                p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Place your bed!")));
            }
        }

        // Auto-open gun selection menu for all players
        GunSelectionManager gsm = session.getGunSelectionManager();
        for (ServerPlayer p : players) {
            java.util.UUID uuid = p.getUUID();
            java.util.List<net.minecraft.resources.ResourceLocation> allGuns = GunSelectionManager.getAllAvailableGuns();
            java.util.List<net.minecraft.resources.ResourceLocation> currentGuns = gsm.getGunSelections(uuid);
            java.util.List<net.minecraft.resources.ResourceLocation> allAtt = com.frosty.bedgunwars.game.GunHelper.getCompatibleAttachments(currentGuns);
            java.util.List<net.minecraft.resources.ResourceLocation> allThrow = GunSelectionManager.getAllAvailableThrowables();
            java.util.List<net.minecraft.resources.ResourceLocation> currentThrow = gsm.getThrowableSelections(uuid);
            java.util.Map<Integer, java.util.Map<String, String>> gunAttachments = new java.util.HashMap<>();
            gsm.getAllGunAttachments(uuid).forEach((slot, typeMap) -> {
                java.util.Map<String, String> m = new java.util.HashMap<>();
                typeMap.forEach((type, id) -> m.put(type.name(), id.toString()));
                gunAttachments.put(slot, m);
            });
            PacketHandler.sendToClient(p, new com.frosty.bedgunwars.network.OpenGunMenuPacket(
                    allGuns, currentGuns, allAtt, new java.util.ArrayList<>(),
                    allThrow, currentThrow, gunAttachments, false));
            com.frosty.bedgunwars.network.LoadoutSyncPacket.send(p,
                    com.frosty.bedgunwars.game.LoadoutManager.get().getLoadouts(uuid));
        }

        broadcast(server, "Preparation phase started! " + (session.isDeathmatch() ? "Pick your loadout!" : "Place your bed now."));
    }

    public static int forceStart(CommandSourceStack source) {
        if (!GameManager.hasGame()) { source.sendFailure(Component.literal("No active game.")); return 0; }
        if (!isHost(source) && !source.hasPermission(2)) { source.sendFailure(Component.literal("Only the host or an op can force start.")); return 0; }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) { source.sendFailure(Component.literal("No active game.")); return 0; }
        if (session.getPhase() != GamePhase.WAITING_PLAYERS) { source.sendFailure(Component.literal("Can only force start during the waiting phase.")); return 0; }
        broadcast(source.getServer(), "§6[BedGunWars] §eHost force-started the match!");
        launchPreparation(source.getServer(), session);
        return 1;
    }

    static boolean isHost(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        GameSession session = GameManager.getSession();
        return session != null && player.getUUID().equals(session.getHostUuid());
    }

    private static List<ServerPlayer> resolveJoinedPlayers(MinecraftServer server, GameSession session) {
        List<ServerPlayer> players = new ArrayList<>();
        List<UUID> missing = new ArrayList<>();
        for (UUID uuid : session.getJoinedPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) { missing.add(uuid); continue; }
            if (!player.serverLevel().dimension().equals(session.getLevel().dimension())) { missing.add(uuid); continue; }
            players.add(player);
        }
        for (UUID uuid : missing) session.removeJoinedPlayer(uuid);
        players.sort(Comparator.comparing(p -> p.getGameProfile().getName()));
        return players;
    }

    private static void assignPlayers(GameSession session, List<ServerPlayer> players, GameModeType mode) {
        if (mode == GameModeType.SOLO || mode == GameModeType.DEATHMATCH_SOLO) {
            for (ServerPlayer player : players) {
                session.addPlayer(player.getUUID());
                session.setPlayerTeam(player.getUUID(), player.getGameProfile().getName());
                session.getBedUpgradeManager().initTeam(player.getUUID().toString());
            }
            return;
        }
        TeamManager.assignTeams(session, players, session.getTeamCount());
    }

    private static String buildTeammateList(ServerPlayer self, String team, GameSession session, MinecraftServer server) {
        return session.getPlayers().stream()
                .filter(u -> team.equals(session.getPlayerTeam(u)) && !u.equals(self.getUUID()))
                .map(u -> { ServerPlayer tp = server.getPlayerList().getPlayer(u); return tp != null ? tp.getName().getString() : "?"; })
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    public static int setFriendlyFire(CommandSourceStack source, boolean enabled) {
        if (!GameManager.hasGame()) { source.sendFailure(Component.literal("No active game.")); return 0; }
        if (!isHost(source)) { source.sendFailure(Component.literal("Only the host can change friendly fire.")); return 0; }
        GameSession session = GameManager.getSession();
        session.setFriendlyFire(enabled);
        String state = enabled ? "§aenabled" : "§cdisabled";
        source.getServer().getPlayerList().getPlayers().forEach(p ->
                p.sendSystemMessage(Component.literal("§6[NOTICE] §eFriendly fire has been " + state + "§e.")));
        return 1;
    }

    private static int getTeamRGB(String team) {
        return switch (team) {
            case "Team 1" -> 0xFF5555;
            case "Team 2" -> 0x5555FF;
            case "Team 3" -> 0x55FF55;
            case "Team 4" -> 0xFFFF55;
            case "Team 5" -> 0xFF55FF;
            case "Team 6" -> 0xFFAA00;
            default       -> 0xFFFFFF;
        };
    }

    private static void teleportPlayersToBeacon(List<ServerPlayer> players, ServerLevel level, BlockPos beacon) {
        int index = 0;
        for (ServerPlayer player : players) {
            double x = beacon.getX() + 0.5 + (index % 4);
            double y = beacon.getY() + 2;
            double z = beacon.getZ() + 0.5 + (index / 4);
            player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
            index++;
        }
    }

    private static void teleportDeathmatchPlayers(List<ServerPlayer> players, GameSession session) {
        if (session.getMode() == GameModeType.DEATHMATCH_TEAMS) {
            for (ServerPlayer player : players) {
                String team = session.getPlayerTeam(player.getUUID());
                net.minecraft.core.BlockPos beacon = session.getDeathmatchManager().getTeamRespawnBeacon(team);
                if (beacon == null) beacon = session.getBeaconPos();
                player.teleportTo(session.getLevel(), beacon.getX() + 0.5, beacon.getY() + 2, beacon.getZ() + 0.5, player.getYRot(), player.getXRot());
            }
        } else {
            for (ServerPlayer player : players) {
                net.minecraft.core.BlockPos beacon = session.getDeathmatchManager().getRandomBeacon();
                if (beacon == null) beacon = session.getBeaconPos();
                player.teleportTo(session.getLevel(), beacon.getX() + 0.5, beacon.getY() + 2, beacon.getZ() + 0.5, player.getYRot(), player.getXRot());
            }
        }
    }

    private static Item getBedItemForPlayer(GameSession session, UUID uuid) {
        if (session.getMode() == GameModeType.SOLO) return Items.RED_BED;
        String team = session.getPlayerTeam(uuid);
        if (team == null) return null;
        UUID bedOwner = session.getTeamBedOwner(team);
        if (!uuid.equals(bedOwner)) return null;
        return switch (team) {
            case "Team 1" -> Items.RED_BED;
            case "Team 2" -> Items.BLUE_BED;
            case "Team 3" -> Items.LIME_BED;
            case "Team 4" -> Items.YELLOW_BED;
            case "Team 5" -> Items.PURPLE_BED;
            case "Team 6" -> Items.ORANGE_BED;
            default       -> Items.WHITE_BED;
        };
    }

    private static void giveStarterItems(GameSession session, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            boolean isTeams = session.getMode() == GameModeType.TEAMS || session.getMode() == GameModeType.DEATHMATCH_TEAMS;
            String team = session.getPlayerTeam(player.getUUID());

            if (session.isDeathmatch()) {
                // deathmatch - keep player inventory, only apply armor
                player.getInventory().armor.set(3, new ItemStack(Items.NETHERITE_HELMET));
                player.getInventory().armor.set(1, new ItemStack(Items.NETHERITE_LEGGINGS));
                player.getInventory().armor.set(0, new ItemStack(Items.NETHERITE_BOOTS));

                if (isTeams && team != null) {
                    ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
                    chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
                    chest.enchant(Enchantments.UNBREAKING, 3);
                    int rgb = getTeamRGB(team);
                    chest.getOrCreateTagElement("display").putInt("color", rgb);
                    player.getInventory().armor.set(2, chest);
                } else {
                    player.getInventory().armor.set(2, new ItemStack(Items.NETHERITE_CHESTPLATE));
                }
            } else {
                // non-deathmatch - clear and give full starter kit
                player.getInventory().clearContent();

                player.getInventory().armor.set(3, new ItemStack(Items.NETHERITE_HELMET));
                player.getInventory().armor.set(1, new ItemStack(Items.NETHERITE_LEGGINGS));
                player.getInventory().armor.set(0, new ItemStack(Items.NETHERITE_BOOTS));

                if (isTeams && team != null) {
                    ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
                    chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 4);
                    chest.enchant(Enchantments.UNBREAKING, 3);
                    int rgb = getTeamRGB(team);
                    chest.getOrCreateTagElement("display").putInt("color", rgb);
                    player.getInventory().armor.set(2, chest);
                } else {
                    player.getInventory().armor.set(2, new ItemStack(Items.NETHERITE_CHESTPLATE));
                }

                Item bedItem = getBedItemForPlayer(session, player.getUUID());
                if (bedItem != null) player.getInventory().setItem(0, new ItemStack(bedItem, 1));
                player.getInventory().setItem(1, new ItemStack(Items.GOLDEN_APPLE, 32));
                player.getInventory().setItem(2, new ItemStack(Items.WOODEN_PICKAXE, 1));
                player.getInventory().setItem(3, new ItemStack(Items.STONE, 64));
            }

            player.containerMenu.broadcastChanges();
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            player.sendSystemMessage(Component.literal(message));
    }

    private static BlockPos findNearestBeacon(ServerLevel level, BlockPos origin, int radius) {
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.BEACON)) return pos;
                }
        return null;
    }

    public static List<net.minecraft.core.BlockPos> findBeaconsInBorder(GameSession session) {
        List<net.minecraft.core.BlockPos> found = new ArrayList<>();
        net.minecraft.server.level.ServerLevel level = session.getLevel();
        net.minecraft.core.BlockPos center = session.getBeaconPos();
        int radius = session.getBorderRadius();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = minY; y < maxY; y++) {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (session.getMode() == GameModeType.DEATHMATCH_TEAMS) {
                        if (state.is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR)) found.add(pos);
                    } else {
                        if (state.is(net.minecraft.world.level.block.Blocks.BEACON)) found.add(pos);
                    }
                }
            }
        }
        return found;
    }

    public static void lockMovement(ServerPlayer player) {
        lockedPositions.put(player.getUUID(), player.position());
    }

    public static void unlockMovement(ServerPlayer player) {
        lockedPositions.remove(player.getUUID());
    }

    private static final java.util.Map<java.util.UUID, net.minecraft.world.phys.Vec3> lockedPositions = new java.util.HashMap<>();

    public static void clearLockedPositions() { lockedPositions.clear(); }

    // tick locked players during PREPARATION
    public static void tickLockedPlayers(net.minecraft.server.MinecraftServer server) {
        lockedPositions.forEach((uuid, pos) -> {
            net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.teleportTo(p.serverLevel(), pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
        });
    }

    public static String resolveLeaderName(GameSession session, String key) {
        if (session.getMode() == GameModeType.DEATHMATCH_SOLO) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(key);
                net.minecraft.server.level.ServerPlayer p = session.getLevel().getServer().getPlayerList().getPlayer(uuid);
                return p != null ? p.getName().getString() : key.substring(0, 8);
            } catch (IllegalArgumentException e) { return key; }
        }
        return key;
    }
}
