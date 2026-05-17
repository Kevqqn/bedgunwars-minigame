package com.frosty.bedgunwars.game;

import com.frosty.bedgunwars.command.GameCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class GameSetupUI {

    // setup lock
    private static UUID setupOwner = null;
    private static PendingGameConfig pendingConfig = null;
    private static String awaitingInput = null;
    private static int awaitingInputToken = 0;

    private static final int CUSTOM_INPUT_TIMEOUT_TICKS = 30 * 20;

    public static boolean hasActiveSetup() { return setupOwner != null; }
    public static UUID getSetupOwner() { return setupOwner; }
    public static boolean isOwner(UUID uuid) { return uuid.equals(setupOwner); }

    public static void open(ServerPlayer player) {
        setupOwner = player.getUUID();
        pendingConfig = new PendingGameConfig();
        awaitingInput = null;
        sendPanel(player);
    }

    public static void close() {
        setupOwner = null;
        pendingConfig = null;
        awaitingInput = null;
        awaitingInputToken++;
    }

    // panel rendering
    public static void sendPanel(ServerPlayer player) {
        if (pendingConfig == null) return;
        PendingGameConfig c = pendingConfig;
        boolean isTeamMode = c.mode.equals("TEAMS") || c.mode.equals("DMTEAMS");
        boolean isDM = c.mode.equals("DMSOLO") || c.mode.equals("DMTEAMS");

        player.sendSystemMessage(divider());
        player.sendSystemMessage(Component.literal("§6>> BedGunWars Setup"));
        player.sendSystemMessage(divider());

        player.sendSystemMessage(row("Mode    ",
                btn("Solo",     "SOLO",    c.mode.equals("SOLO")),
                btn("Teams",    "TEAMS",   c.mode.equals("TEAMS")),
                btn("DM Solo",  "DMSOLO",  c.mode.equals("DMSOLO")),
                btn("DM Teams", "DMTEAMS", c.mode.equals("DMTEAMS"))
        ));

        if (isTeamMode) {
            player.sendSystemMessage(row("Teams   ",
                    btn("2", "teams_2", c.teamCount == 2),
                    btn("3", "teams_3", c.teamCount == 3),
                    btn("4", "teams_4", c.teamCount == 4),
                    btn("5", "teams_5", c.teamCount == 5),
                    btn("6", "teams_6", c.teamCount == 6)
            ));
        }

        player.sendSystemMessage(rowWithCustom("Border  ",
                c.border + "m", new int[]{50, 75, 100, 150}, c.border, "border",
                v -> "/bgwsetup border " + v));

        player.sendSystemMessage(rowWithCustom("Prep    ",
                formatSecs(c.prepSeconds), new int[]{60, 120, 180}, c.prepSeconds, "prep",
                v -> "/bgwsetup prep " + v));

        player.sendSystemMessage(rowWithCustom("Match   ",
                formatMins(c.matchSeconds), new int[]{300, 600, 900, 1200}, c.matchSeconds, "match",
                v -> "/bgwsetup match " + v));

        if (isDM) {
            player.sendSystemMessage(rowWithCustom("Kills   ",
                    c.killLimit + " kills", new int[]{20, 30, 50, 75}, c.killLimit, "kills",
                    v -> "/bgwsetup kills " + v));
        }

        player.sendSystemMessage(divider());

        // summary line
        String summary = "§7Mode: §f" + friendlyMode(c.mode)
                + (isTeamMode ? " §7(" + c.teamCount + " teams)" : "")
                + " §7| Border: §f" + c.border + "m"
                + " §7| Prep: §f" + formatSecs(c.prepSeconds)
                + " §7| Match: §f" + formatMins(c.matchSeconds)
                + (isDM ? " §7| Kills: §f" + c.killLimit : "");
        player.sendSystemMessage(Component.literal(summary));

        if (isDM) {
            player.sendSystemMessage(Component.literal(
                    "§8(i) Beacons detected when border is set. DM Teams uses respawn anchors."));
        }

        MutableComponent startBtn = clickable(
                isDM ? "§a[ Start Deathmatch ]" : "§a[ Start Game ]",
                "/bgwsetup confirm", "§aLaunch with current settings");
        MutableComponent cancelBtn = clickable(
                "§c[ Cancel ]", "/bgwsetup cancel", "§cClose setup without starting");
        player.sendSystemMessage(startBtn.append("  ").append(cancelBtn));
        player.sendSystemMessage(divider());
    }

    // command handler
    public static int handleSetupCommand(CommandSourceStack source, String sub) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        if (!isOwner(player.getUUID())) {
            source.sendFailure(Component.literal("You don't have an active setup session."));
            return 0;
        }
        if (pendingConfig == null) return 0;

        PendingGameConfig c = pendingConfig;

        if (sub.equals("confirm")) { launchFromSetup(source, player); return 1; }
        if (sub.equals("cancel"))  { close(); player.sendSystemMessage(Component.literal("§7[Setup] Cancelled.")); return 1; }

        // custom input prompts
        if (sub.equals("custom_border")) { promptCustom(player, "border"); return 1; }
        if (sub.equals("custom_prep"))   { promptCustom(player, "prep");   return 1; }
        if (sub.equals("custom_match"))  { promptCustom(player, "match");  return 1; }
        if (sub.equals("custom_kills"))  { promptCustom(player, "kills");  return 1; }

        // direct set from custom input
        if (sub.startsWith("border ")) { tryParseAndSet(player, "border", sub.substring(7)); return 1; }
        if (sub.startsWith("prep "))   { tryParseAndSet(player, "prep",   sub.substring(5)); return 1; }
        if (sub.startsWith("match "))  { tryParseAndSet(player, "match",  sub.substring(6)); return 1; }
        if (sub.startsWith("kills "))  { tryParseAndSet(player, "kills",  sub.substring(6)); return 1; }

        // preset buttons
        switch (sub) {
            case "SOLO"    -> { c.mode = "SOLO";    c.teamCount = 1; }
            case "TEAMS"   -> { c.mode = "TEAMS";   if (c.teamCount < 2) c.teamCount = 2; }
            case "DMSOLO"  -> { c.mode = "DMSOLO";  c.teamCount = 1; }
            case "DMTEAMS" -> { c.mode = "DMTEAMS"; if (c.teamCount < 2) c.teamCount = 2; }
            case "teams_2" -> c.teamCount = 2;
            case "teams_3" -> c.teamCount = 3;
            case "teams_4" -> c.teamCount = 4;
            case "teams_5" -> c.teamCount = 5;
            case "teams_6" -> c.teamCount = 6;
            case "border_50"  -> c.border = 50;
            case "border_75"  -> c.border = 75;
            case "border_100" -> c.border = 100;
            case "border_150" -> c.border = 150;
            case "prep_60"    -> c.prepSeconds = 60;
            case "prep_120"   -> c.prepSeconds = 120;
            case "prep_180"   -> c.prepSeconds = 180;
            case "match_300"  -> c.matchSeconds = 300;
            case "match_600"  -> c.matchSeconds = 600;
            case "match_900"  -> c.matchSeconds = 900;
            case "match_1200" -> c.matchSeconds = 1200;
            case "kills_20"   -> c.killLimit = 20;
            case "kills_30"   -> c.killLimit = 30;
            case "kills_50"   -> c.killLimit = 50;
            case "kills_75"   -> c.killLimit = 75;
        }

        // rerender panel
        sendPanel(player);
        return 1;
    }

    // custom input
    private static void promptCustom(ServerPlayer player, String param) {
        awaitingInput = param;
        int token = ++awaitingInputToken;
        String label = switch (param) {
            case "border" -> "border size (min 11)";
            case "prep"   -> "prep time in seconds (min 1)";
            case "match"  -> "match time in seconds (min 1)";
            case "kills"  -> "kill limit (min 10)";
            default -> param;
        };
        player.sendSystemMessage(Component.literal(
                "§e[Setup] §fType your " + label + " in chat, or type §7cancel§f:"));

        // timeout
        com.frosty.bedgunwars.event.GameTickHandler.scheduleTask(CUSTOM_INPUT_TIMEOUT_TICKS, () -> {
            if (awaitingInputToken != token) return;
            awaitingInput = null;
            ServerPlayer p = player.getServer().getPlayerList().getPlayer(player.getUUID());
            if (p != null) p.sendSystemMessage(Component.literal(
                    "§c[Setup] §fInput timed out. Type §7/game setup §fto start again."));
            close();
        });
    }

    // chat interception - returns true to suppress normal chat
    public static boolean onChatMessage(ServerPlayer player, String message) {
        if (!isOwner(player.getUUID())) return false;
        if (awaitingInput == null) return false;

        String param = awaitingInput;
        awaitingInput = null;
        awaitingInputToken++;

        if (message.equalsIgnoreCase("cancel")) {
            player.sendSystemMessage(Component.literal("§7[Setup] Input cancelled."));
            sendPanel(player);
            return true;
        }

        tryParseAndSet(player, param, message.trim());
        return true;
    }

    private static void tryParseAndSet(ServerPlayer player, String param, String raw) {
        if (pendingConfig == null) return;
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("§c[Setup] §fInvalid number: \"" + raw + "\". Try again."));
            sendPanel(player);
            return;
        }

        PendingGameConfig c = pendingConfig;
        switch (param) {
            case "border" -> {
                if (value <= 10) player.sendSystemMessage(Component.literal("§c[Setup] §fBorder must be greater than 10."));
                else c.border = value;
            }
            case "prep" -> {
                if (value < 1) player.sendSystemMessage(Component.literal("§c[Setup] §fPrep time must be at least 1 second."));
                else c.prepSeconds = value;
            }
            case "match" -> {
                if (value < 1) player.sendSystemMessage(Component.literal("§c[Setup] §fMatch time must be at least 1 second."));
                else c.matchSeconds = value;
            }
            case "kills" -> {
                if (value < 10) player.sendSystemMessage(Component.literal("§c[Setup] §fKill limit must be at least 10."));
                else c.killLimit = value;
            }
        }
        sendPanel(player);
    }

    // game launch
    private static void launchFromSetup(CommandSourceStack source, ServerPlayer player) {
        if (GameManager.hasGame()) {
            player.sendSystemMessage(Component.literal("§c[Setup] §fA game is already running."));
            return;
        }
        if (pendingConfig == null) return;

        PendingGameConfig c = pendingConfig;
        GameModeType mode = resolveMode(c.mode);
        boolean isDM = mode.isDeathmatch();
        ServerLevel level = player.serverLevel();

        BlockPos beacon = null;
        if (!isDM) {
            beacon = findNearestBeacon(level, player.blockPosition(), 10);
            if (beacon == null) {
                player.sendSystemMessage(Component.literal("§c[Setup] §fNo beacon within 10 blocks. Stand near a beacon."));
                sendPanel(player);
                return;
            }
        }

        BlockPos sessionPos = beacon != null ? beacon : player.blockPosition();
        GameSession session = new GameSession(level, sessionPos, mode, player.getUUID());
        session.setPhase(GamePhase.STARTING);
        session.setTeamCount(c.teamCount);
        session.setPrepTimeSeconds(c.prepSeconds);
        session.setConfiguredPrepSeconds(c.prepSeconds);
        session.setMatchTimeSeconds(c.matchSeconds);
        if (isDM) session.setKillLimit(c.killLimit);
        session.addJoinedPlayer(player.getUUID());
        session.setBorderRadius(c.border);
        BorderManager.applyBorder(session);

        // beacon validation for deathmatch
        if (isDM) {
            java.util.List<BlockPos> beacons = GameCommand.findBeaconsInBorder(session);
            if (beacons.isEmpty()) {
                String blockName = mode == GameModeType.DEATHMATCH_TEAMS ? "respawn anchors" : "beacons";
                player.sendSystemMessage(Component.literal(
                        "§c[Setup] §fNo " + blockName + " found inside border radius " + c.border + ". Place them and try again."));
                sendPanel(player);
                return;
            }
            if (mode == GameModeType.DEATHMATCH_TEAMS && beacons.size() < c.teamCount) {
                player.sendSystemMessage(Component.literal(
                        "§c[Setup] §fNeed " + c.teamCount + " respawn anchors, found " + beacons.size() + "."));
                sendPanel(player);
                return;
            }
            session.getDeathmatchManager().setAllBeacons(beacons);
            if (mode == GameModeType.DEATHMATCH_TEAMS) {
                java.util.List<BlockPos> shuffled = new java.util.ArrayList<>(beacons);
                java.util.Collections.shuffle(shuffled);
                for (int i = 0; i < c.teamCount; i++) {
                    session.getDeathmatchManager().assignTeamBeacon("Team " + (i + 1), shuffled.get(i));
                }
            }
        }
        ExcludedGunsConfig.load();
        GameManager.start(session);
        close();

        String modeLabel = isDM ? "deathmatch" : "match";
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(
                    "§6[BedGunWars] §eA " + modeLabel + " is starting! Type §f/game join §eto participate. (30s)"));
        }

        session.setPhase(GamePhase.WAITING_PLAYERS);
    }

    // component builders
    private static MutableComponent divider() {
        return Component.literal("§8--------------------------------");
    }

    private static MutableComponent btn(String label, String action, boolean selected) {
        String color = selected ? "§a" : "§7";
        return clickable(color + "[" + label + "]", "/bgwsetup " + action,
                selected ? "§aSelected" : "§7Click to select");
    }

    private static MutableComponent clickable(String text, String command, String hover) {
        return Component.literal(text).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static MutableComponent row(String label, MutableComponent... buttons) {
        MutableComponent line = Component.literal("§7" + label + " ");
        for (int i = 0; i < buttons.length; i++) {
            line = line.append(buttons[i]);
            if (i < buttons.length - 1) line = line.append(Component.literal("§8 "));
        }
        return line;
    }

    private static MutableComponent rowWithCustom(String label, String currentDisplay,
                                                   int[] presets, int currentValue, String param,
                                                   java.util.function.IntFunction<String> cmdFor) {
        MutableComponent line = Component.literal("§7" + label + " ");
        boolean matchesPreset = false;
        for (int preset : presets) {
            boolean sel = currentValue == preset;
            if (sel) matchesPreset = true;
            String color = sel ? "§a" : "§7";
            String display = param.equals("prep")  ? formatSecs(preset)
                           : param.equals("match") ? formatMins(preset)
                           : String.valueOf(preset);
            line = line.append(clickable(color + "[" + display + "]", cmdFor.apply(preset),
                    sel ? "§aSelected" : "§7Set to " + display));
            line = line.append(Component.literal("§8 "));
        }
        String customLabel = matchesPreset ? "Custom" : currentDisplay;
        line = line.append(clickable("§e[" + customLabel + "]",
                "/bgwsetup custom_" + param, "§eEnter a custom value"));
        return line;
    }

    // formatting
    private static String formatSecs(int s) { return s + "s"; }

    private static String formatMins(int s) {
        int m = s / 60;
        int rem = s % 60;
        return rem == 0 ? m + "m" : m + "m " + rem + "s";
    }

    private static String friendlyMode(String mode) {
        return switch (mode) {
            case "SOLO"    -> "Solo";
            case "TEAMS"   -> "Teams";
            case "DMSOLO"  -> "Deathmatch Solo";
            case "DMTEAMS" -> "Deathmatch Teams";
            default -> mode;
        };
    }

    private static GameModeType resolveMode(String mode) {
        return switch (mode) {
            case "TEAMS"   -> GameModeType.TEAMS;
            case "DMSOLO"  -> GameModeType.DEATHMATCH_SOLO;
            case "DMTEAMS" -> GameModeType.DEATHMATCH_TEAMS;
            default        -> GameModeType.SOLO;
        };
    }

    private static BlockPos findNearestBeacon(ServerLevel level, BlockPos origin, int radius) {
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BEACON)) return pos;
                }
        return null;
    }

    public static class PendingGameConfig {
        public String mode         = "SOLO";
        public int    teamCount    = 1;
        public int    border       = 75;
        public int    prepSeconds  = 180;
        public int    matchSeconds = 600;
        public int    killLimit    = 30;
    }
}
