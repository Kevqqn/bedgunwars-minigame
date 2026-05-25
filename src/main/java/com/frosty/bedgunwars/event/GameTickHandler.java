package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.*;
import com.frosty.bedgunwars.network.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.event.TickEvent;
import com.frosty.bedgunwars.ui.GameScoreboard;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.frosty.bedgunwars.game.GameCleanupManager;
import com.frosty.bedgunwars.game.BedUpgradeMenu;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import java.util.List;

import java.util.UUID;
import com.frosty.bedgunwars.game.TipsManager;

public class GameTickHandler {

    @SuppressWarnings("unused")
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {

        // Process scheduled tasks
        for (int i = taskDelays.size() - 1; i >= 0; i--) {
            taskDelays.set(i, taskDelays.get(i) - 1);
            if (taskDelays.get(i) <= 0) {
                scheduledTasks.get(i).run();
                scheduledTasks.remove(i);
                taskDelays.remove(i);
            }
        }

        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        GamePhase phase = session.getPhase();

        // Host disconnect grace period
        if (session.isHostDisconnected()) {
            session.decreaseHostDisconnectTicks();
            int secsLeft = session.getHostDisconnectTicks() / 20;
            // Warn at 30s and 10s
            if (session.getHostDisconnectTicks() == 30 * 20 || session.getHostDisconnectTicks() == 10 * 20) {
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    p.sendSystemMessage(Component.literal("§c[NOTICE] §fHost has " + secsLeft + "s to reconnect or match cancelled."));
                }
            }
            if (session.getHostDisconnectTicks() <= 0) {
                GameCleanupManager.restoreAndEnd(event.getServer(), session, "Host failed to reconnect. Match cancelled.");
                return;
            }
        }

        if (phase == GamePhase.WAITING_PLAYERS) {
            int ticksLeft = session.getWaitingPlayersTicks();
            int initialTicks = session.getWaitingInitialTicks();
            int secondsLeft = ticksLeft / 20;
            int joined = session.getJoinedPlayers().size();
            int online = event.getServer().getPlayerList().getPlayers().size();

            float progress = initialTicks > 0 ? (float) ticksLeft / initialTicks : 0f;
            BossBarManager.show(event.getServer(),
                    "Waiting for players: " + secondsLeft + "s | " + joined + "/" + online + " joined",
                    progress);

            // Broadcast join prompt at intervals
            if (ticksLeft == 25 * 20 || ticksLeft == 15 * 20 || ticksLeft == 10 * 20 || ticksLeft == 5 * 20) {
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    if (!session.isJoined(p.getUUID())) {
                        p.sendSystemMessage(Component.literal(
                                "§6[BedGunWars] §eMatch starting in " + secondsLeft + "s! Type §f/game join §eto participate."));
                    }
                }
            }

            // Joined players wait time skip
            boolean allJoined = online > 0 && joined >= online;
            if (allJoined && ticksLeft <= session.getWaitingMinTicks()) {
                BossBarManager.remove(event.getServer());
                com.frosty.bedgunwars.command.GameCommand.launchPreparation(event.getServer(), session);
                return;
            }

            session.decreaseWaitingPlayersTicks();

            if (session.getWaitingPlayersTicks() <= 0) {
                BossBarManager.remove(event.getServer());
                com.frosty.bedgunwars.command.GameCommand.launchPreparation(event.getServer(), session);
            }
            return;
        }

        else if (phase == GamePhase.PREPARATION) {
            com.frosty.bedgunwars.command.GameCommand.tickLockedPlayers(event.getServer());
            if (!session.isMinimapStartSent()) {
                session.setMinimapStartSent(true);
                com.frosty.bedgunwars.network.MinimapStartPacket pkt = new com.frosty.bedgunwars.network.MinimapStartPacket(
                        session.getBeaconPos().getX(),
                        session.getBeaconPos().getZ(),
                        session.getBorderRadius()
                );
                for (java.util.UUID uuid : session.getPlayers()) {
                    net.minecraft.server.level.ServerPlayer p = event.getServer().getPlayerList().getPlayer(uuid);
                    if (p != null) PacketHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), pkt);
                }
                TabStatsManager.push(event.getServer(), session);
            }
            applyPrepEffects(event.getServer(), session);
            session.hideAllNametags(event.getServer());
            int ticksLeft = session.getPrepTimeTicks();
            int initialTicks = session.getInitialPrepTicks();
            int secondsLeft = ticksLeft / 20;

            float progress = initialTicks > 0 ? (float) ticksLeft / initialTicks : 0f;
            BossBarManager.show(event.getServer(), "Preparation: " + secondsLeft + "s remaining", progress);

            if (ticksLeft == 5 * 20 || ticksLeft == 4 * 20 || ticksLeft == 3 * 20
                    || ticksLeft == 2 * 20 || ticksLeft == 1 * 20) {
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    SoundHelper.playNoteClick(p, SoundHelper.noteToPitch(20));
                }
                broadcast(event.getServer(), "Game starts in " + secondsLeft + "s!");
            } else if (ticksLeft == 60 * 20 || ticksLeft == 30 * 20) {
                broadcast(event.getServer(), "Game starts in " + secondsLeft + "s!");
            } else if (ticksLeft == 10 * 20) {
                broadcast(event.getServer(), "Game starts in " + secondsLeft + "s!");
                String tipId = session.isDeathmatch() ? "6" : "5";
                for (UUID uuid : session.getPlayers()) {
                    ServerPlayer p = event.getServer().getPlayerList().getPlayer(uuid);
                    if (p == null) continue;
                    if (session.isDeathmatch()) {
                        TipsManager.sendTip(p, tipId, "<winkills>", String.valueOf(session.getKillLimit()));
                    } else {
                        TipsManager.sendTip(p, tipId);
                    }
                }
            }


            session.decreasePrepTime();

            if (session.getPrepTimeTicks() <= 0) {
                session.setPhase(GamePhase.ACTIVE);
                session.getMapRestoreManager().init(session.getLevel());
                liftPrepEffects(event.getServer(), session);
                TabStatsManager.push(event.getServer(), session);
                event.getServer().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, event.getServer());
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    SoundHelper.playNoteClick(p, SoundHelper.noteToPitch(25));
                    // Movement release on DM mode
                    if (session.isDeathmatch()) {
                        com.frosty.bedgunwars.command.GameCommand.unlockMovement(p);
                    }
                }
                if (session.isDeathmatch()) {
                    broadcast(event.getServer(), "Deathmatch has started! First to " + session.getKillLimit() + " kills wins!");
                } else {
                    broadcast(event.getServer(), "Game has started! Destroy enemy beds!");
                    for (UUID uuid : session.getPlayers()) {
                        ServerPlayer tp = event.getServer().getPlayerList().getPlayer(uuid);
                        if (tp != null) TipsManager.sendTip(tp, "7");
                    }
                }
            }
            GameScoreboard.update(session);
            tickVoidCheck(event.getServer(), session);
        }

        else if (phase == GamePhase.ACTIVE) {
            session.decreaseMatchTime();
            int ticksLeft = session.getMatchTimeTicks();
            int initialTicks = session.getInitialMatchTicks();
            int secondsLeft = ticksLeft / 20;

            float progress = initialTicks > 0 ? (float) ticksLeft / initialTicks : 0f;
            if (session.isDeathmatch()) {
                String leader = session.getDeathmatchManager().getLeader();
                String leaderName = leader != null ? com.frosty.bedgunwars.command.GameCommand.resolveLeaderName(session, leader) : "None";
                int leaderKills = leader != null ? session.getDeathmatchManager().getKills(leader) : 0;
                BossBarManager.show(event.getServer(),
                        "DM │ " + formatTime(secondsLeft) + " │ Leader: " + leaderName + " (" + leaderKills + "/" + session.getKillLimit() + " kills)",
                        progress);
            } else {
                BossBarManager.show(event.getServer(), "Match: " + formatTime(secondsLeft) + " remaining", progress);
            }

            if (ticksLeft == 5 * 60 * 20) {
                broadcast(event.getServer(), "§e[NOTICE] §f5 minutes remaining!");
            } else if (ticksLeft == 2 * 60 * 20) {
                broadcast(event.getServer(), "§e[NOTICE] §f2 minutes remaining!");
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    SoundHelper.playNoteClick(p, SoundHelper.noteToPitch(20));
                }
            } else if (ticksLeft == 60 * 20) {
                broadcast(event.getServer(), "§c[NOTICE] §f1 minute remaining!");
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    SoundHelper.playNoteClick(p, SoundHelper.noteToPitch(22));
                }
            } else if (ticksLeft == 30 * 20 || ticksLeft == 10 * 20
                    || ticksLeft == 5 * 20 || ticksLeft == 4 * 20 || ticksLeft == 3 * 20
                    || ticksLeft == 2 * 20 || ticksLeft == 1 * 20) {
                broadcast(event.getServer(), "Match ends in " + secondsLeft + "s!");
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    SoundHelper.playNoteClick(p, SoundHelper.noteToPitch(20));
                }
            }

            if (ticksLeft % 20 == 0) {
                BedUpgradeMenu.tickReplenishDisplay(event.getServer(), session);
            }

            if (ticksLeft % 40 == 0) {
                TabStatsManager.push(event.getServer(), session);
            }

            int elapsed = session.getInitialMatchTicks() - ticksLeft;
            if (elapsed == 20 * 20) {
                for (UUID uuid : session.getPlayers()) {
                    ServerPlayer tp = event.getServer().getPlayerList().getPlayer(uuid);
                    if (tp != null) TipsManager.sendTip(tp, "8");
                }
            } else if (elapsed == 60 * 20) {
                for (UUID uuid : session.getPlayers()) {
                    ServerPlayer tp = event.getServer().getPlayerList().getPlayer(uuid);
                    if (tp != null) TipsManager.sendTip(tp, "9");
                }
            }

            if (ticksLeft <= 0) {
                if (session.isDeathmatch()) {
                    WinManager.checkDeathmatchTimerWinner(session);
                } else {
                    startEndgame(event.getServer(), session);
                }
            }
            GameScoreboard.update(session);
            if (!session.isDeathmatch()) {
                tickBedUpgrades(event.getServer(), session);
            }
            session.getKillstreakManager().tick(event.getServer(), session);
            // "What?! How is he still immune!!" - A frustrated player because the spawn immunity is not balanced literally on playtest
            if (session.isDeathmatch()) {
                for (java.util.UUID uuid : session.getPlayers()) {
                    if (!session.isSpawnImmune(uuid)) continue;
                    net.minecraft.server.level.ServerPlayer p = event.getServer().getPlayerList().getPlayer(uuid);
                    if (p == null) continue;
                    net.minecraft.core.BlockPos cur = p.blockPosition();
                    net.minecraft.core.BlockPos last = session.getLastKnownPositions().get(uuid);
                    if (last != null && !cur.equals(last)) {
                        session.clearSpawnImmune(uuid);
                        p.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
                        p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6[NOTICE] §fSpawn immunity dropped!"));
                    }
                    session.getLastKnownPositions().put(uuid, cur);
                }
            }
        }

        else if (phase == GamePhase.ENDING) {
            int shrinkTicks = session.getEndgameBorderShrinkTicks();
            int interval = session.getEndgameBorderShrinkInterval();
            int secondsUntilShrink = shrinkTicks / 20;

            BossBarManager.show(event.getServer(), "ENDGAME — Border shrinks in " + formatTime(secondsUntilShrink), 1.0f);

            if (shrinkTicks == 5 * 20 || shrinkTicks == 4 * 20 || shrinkTicks == 3 * 20
                    || shrinkTicks == 2 * 20 || shrinkTicks == 1 * 20) {
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    SoundHelper.playNoteClick(p, SoundHelper.noteToPitch(20));
                }
                broadcast(event.getServer(), "Border shrinks in " + secondsUntilShrink + "s!");
            }

            session.decreaseEndgameShrinkTicks();

            if (session.getEndgameBorderShrinkTicks() <= 0) {
                shrinkBorder(session, 30, Math.max(1, interval / 20));
                session.setEndgameBorderShrinkTicks(interval);
                broadcast(event.getServer(), "§cThe border is shrinking!");
            }

            tickAntiSittingDuck(event.getServer(), session);
            WinManager.checkWinner(session);
            GameScoreboard.update(session);
            session.getKillstreakManager().tick(event.getServer(), session);
            // Border sync to all players every 1s
            if (shrinkTicks % 20 == 0) {
                net.minecraft.world.level.border.WorldBorder border =
                        session.getLevel().getWorldBorder();
                net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket pkt =
                        new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(border);
                for (net.minecraft.server.level.ServerPlayer p : session.getLevel().players()) {
                    p.connection.send(pkt);
                }
            }
        }

        else if (phase == GamePhase.WINNER_ANNOUNCED) {
            GameScoreboard.update(session);
            session.removeNametagTeams(event.getServer());

            // Fade transition for cutscene
            if (session.getWinnerDelayTicks() == 11) {
                PacketHandler.sendToAllClients(event.getServer(), new com.frosty.bedgunwars.network.MvpPreFadePacket());
            }

            if (session.getWinnerDelayTicks() == 1 && !com.frosty.bedgunwars.game.MvpCutsceneManager.isRunning()) {
                com.frosty.bedgunwars.game.MvpCutsceneManager.start(event.getServer(), session, session.getWinnerUUID());
            }

            com.frosty.bedgunwars.game.MvpCutsceneManager.tick(event.getServer(), session);
            session.decreaseWinnerDelay();

            if (session.getWinnerDelayTicks() <= 0) {
                GameCleanupManager.restoreAndEnd(
                        event.getServer(), session,
                        session.getWinnerName() + " wins! Game ended."
                );
            }
        }
        else if (phase == GamePhase.SCOREBOARD_VIEW) {
            com.frosty.bedgunwars.BedGunWars.LOGGER.info("[Scoreboard] tick={}, winnerDelay={}",
                    com.frosty.bedgunwars.game.EndScoreboardManager.getTick(),
                    session.getWinnerDelayTicks());
            com.frosty.bedgunwars.game.EndScoreboardManager.tick(event.getServer(), session);
            session.decreaseWinnerDelay();
            if (session.getWinnerDelayTicks() <= 0) {
                GameCleanupManager.restoreAndEnd(
                        event.getServer(), session,
                        session.getWinnerName() + " wins! Game over."
                );
            }
        }
    }

    private static final List<Runnable> scheduledTasks = new ArrayList<>();
    private static final List<Integer> taskDelays = new ArrayList<>();

    public static void scheduleTask(int delayTicks, Runnable task) {
        scheduledTasks.add(task);
        taskDelays.add(delayTicks);
    }

    private void applyPrepEffects(MinecraftServer server, GameSession session) {
        List<UUID> playerUuids = session.getPlayers().stream().toList();
        List<ServerPlayer> onlinePlayers = playerUuids.stream()
                .map(uuid -> server.getPlayerList().getPlayer(uuid))
                .filter(p -> p != null)
                .toList();

        for (ServerPlayer target : onlinePlayers) {
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
        }
    }

    private void liftPrepEffects(MinecraftServer server, GameSession session) {
        List<UUID> playerUuids = session.getPlayers().stream().toList();
        List<ServerPlayer> onlinePlayers = playerUuids.stream()
                .map(uuid -> server.getPlayerList().getPlayer(uuid))
                .filter(p -> p != null)
                .toList();

        for (ServerPlayer target : onlinePlayers) {
            target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            target.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    private void startEndgame(MinecraftServer server, GameSession session) {
        session.setPhase(GamePhase.ENDING);
        session.setEndgameBorderShrinkTicks(session.getEndgameBorderShrinkInterval());

        destroyAllBeds(session);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            SoundHelper.playEnderDragonSound(p);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Endgame")));
            p.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("No respawn, last man standing wins!")));
        }

        broadcast(server, "Endgame! All beds destroyed. Last player standing wins!");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            TipsManager.sendTip(p, "13");
        }
    }

    private void destroyAllBeds(GameSession session) {
        for (UUID uuid : session.getPlayers()) {
            if (session.isEliminated(uuid)) continue;
            BlockPos footPos = session.getPlayerBed(uuid);
            if (footPos == null) continue;
            BlockState footState = session.getLevel().getBlockState(footPos);
            if (!(footState.getBlock() instanceof BedBlock)) continue;
            Direction facing = footState.getValue(BedBlock.FACING);
            BlockPos headPos = footPos.relative(facing);
            session.getLevel().setBlock(footPos, Blocks.AIR.defaultBlockState(), 18);
            BlockState headState = session.getLevel().getBlockState(headPos);
            if (headState.getBlock() instanceof BedBlock) {
                session.getLevel().setBlock(headPos, Blocks.AIR.defaultBlockState(), 18);
            }
            session.breakBed(uuid);
        }
    }

    private void tickAntiSittingDuck(MinecraftServer server, GameSession session) {
        Map<UUID, BlockPos> lastPos     = session.getLastKnownPositions();
        Map<UUID, Integer>  noMoveTicks = session.getNoMoveTicks();
        Map<UUID, Integer>  glowTicks   = session.getGlowTicks();
        Set<UUID>           warned      = session.getWarnedPlayers();

        for (UUID uuid : session.getPlayers()) {
            if (session.isEliminated(uuid)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            BlockPos currentPos = player.blockPosition();
            BlockPos last = lastPos.getOrDefault(uuid, currentPos);

            // Tick down existing glow
            if (glowTicks.containsKey(uuid)) {
                int remaining = glowTicks.get(uuid) - 1;
                if (remaining <= 0) {
                    glowTicks.remove(uuid);
                    player.removeEffect(MobEffects.GLOWING);
                } else {
                    glowTicks.put(uuid, remaining);
                }
                lastPos.put(uuid, currentPos);
                noMoveTicks.put(uuid, 0);
                continue;
            }

            if (currentPos.equals(last)) {
                int ticks = noMoveTicks.getOrDefault(uuid, 0) + 1;
                noMoveTicks.put(uuid, ticks);

                // Warning at NO_MOVE_WARNING threshold
                if (ticks == session.getNoMoveWarningThreshold()) {
                    warned.add(uuid);
                    int secsUntilReveal = (session.getNoMoveThreshold() - session.getNoMoveWarningThreshold()) / 20;
                    player.sendSystemMessage(Component.literal(
                            "§c[WARNING] §fYou haven't moved! You will be revealed in §e"
                                    + secsUntilReveal + " seconds §fif you don't move!"));
                    SoundHelper.playNoteClick(player, SoundHelper.noteToPitch(20));
                }

                // Reveal at NO_MOVE_THRESHOLD
                if (ticks >= session.getNoMoveThreshold()) {
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                            session.getGlowDuration(), 0, false, false));
                    glowTicks.put(uuid, session.getGlowDuration());
                    noMoveTicks.put(uuid, 0);
                    warned.remove(uuid);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.sendSystemMessage(Component.literal(
                                "§6[NOTICE] §eA player has been revealed due to not moving."));
                    }
                }
            } else {
                // Cancel and reset if player moved
                if (warned.contains(uuid)) {
                    warned.remove(uuid);
                    player.sendSystemMessage(Component.literal(
                            "§a[NOTICE] §fWarning cancelled — keep moving!"));
                }
                noMoveTicks.put(uuid, 0);
            }

            lastPos.put(uuid, currentPos);
        }
    }

    private void tickVoidCheck(MinecraftServer server, GameSession session) {
        for (UUID uuid : session.getPlayers()) {
            if (session.isEliminated(uuid)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            if (player.getY() < -64) {
                BlockPos beacon = session.getBeaconPos();
                player.teleportTo(player.serverLevel(),
                        beacon.getX() + 0.5, beacon.getY() + 2, beacon.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.literal("§c[NOTICE] §fHow did you fell? Teleporting back"));

            }
        }
    }

    private void tickBedUpgrades(MinecraftServer server, GameSession session) {
        BedUpgradeManager mgr = session.getBedUpgradeManager();

        for (UUID uuid : session.getPlayers()) {
            if (session.isEliminated(uuid)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            String team = BedUpgradeMenu.getTeamKey(player, session);
            UUID bedOwner = session.getMode() == GameModeType.TEAMS
                    ? session.getTeamBedOwner(session.getPlayerTeam(uuid))
                    : uuid;
            if (bedOwner == null) continue;
            BlockPos bedPos = session.getPlayerBed(bedOwner);
            if (bedPos == null) continue;

            double distToBed = player.blockPosition().distSqr(bedPos);

            int healTier = mgr.getTier(team, BedUpgradeManager.UpgradeType.HEALING_STATION);
            if (healTier > 0) {
                boolean inRange = switch (healTier) {
                    case 1 -> distToBed <= 20 * 20;
                    case 2 -> distToBed <= 40 * 40;
                    case 3 -> distToBed <= 60 * 60;
                    case 4 -> distToBed <= 40 * 40;
                    case 5 -> distToBed <= 80 * 80;
                    case 6 -> true;
                    default -> false;
                };
                int amplifier = healTier >= 4 ? 1 : 0;
                if (inRange) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.REGENERATION, 40, amplifier, false, false));
                }
            }

            for (UUID enemyUuid : session.getPlayers()) {
                if (session.isEliminated(enemyUuid)) continue;
                if (enemyUuid.equals(uuid)) continue;
                // Skip if teammate
                if (session.getMode() == GameModeType.TEAMS) {
                    String myTeam = session.getPlayerTeam(uuid);
                    String enemyTeam = session.getPlayerTeam(enemyUuid);
                    if (myTeam != null && myTeam.equals(enemyTeam)) continue;
                }
                ServerPlayer enemy = server.getPlayerList().getPlayer(enemyUuid);
                if (enemy == null) continue;
                double enemyDistToBed = enemy.blockPosition().distSqr(bedPos);

                // Mining Fatigue Trap
                int mfTier = mgr.getTier(team, BedUpgradeManager.UpgradeType.MINING_FATIGUE);
                if (mfTier > 0 && enemyDistToBed <= 3 * 3) {
                    if (mfTier == 6 && distToBed > 10 * 10) {
                    } else {
                        int[] mfDuration = {0, 40, 80, 120, 160, 120, Integer.MAX_VALUE};
                        int[] mfLevel    = {1,  1,  2,   2,   2,   3,  2};
                        enemy.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN,
                                mfDuration[mfTier], mfLevel[mfTier], false, true));
                        if (mfTier < 6) {
                            mgr.setTier(team, BedUpgradeManager.UpgradeType.MINING_FATIGUE, 1);
                            // notify bed owner
                            ServerPlayer owner = server.getPlayerList().getPlayer(bedOwner);
                            if (owner != null) {
                                owner.sendSystemMessage(Component.literal(
                                        "§6[BED TRAP] §fMining Fatigue triggered! Trap has been reset to Tier 1."));
                                SoundHelper.playNoteClick(owner, SoundHelper.noteToPitch(18));
                            }
                        }
                    }
                }

                // Slowness Trap
                int slowTier = mgr.getTier(team, BedUpgradeManager.UpgradeType.SLOWNESS);
                if (slowTier > 0 && enemyDistToBed <= 5 * 5) {
                    enemy.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true));
                }

                // Alarm
                int alarmTier = mgr.getTier(team, BedUpgradeManager.UpgradeType.ALARM);
                if (alarmTier > 0 && enemyDistToBed <= 10 * 10) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(bedOwner);
                    if (owner != null) {
                        for (int n = 0; n < 5; n++) {
                            SoundHelper.playNoteClick(owner, SoundHelper.noteToPitch(22));
                        }
                        owner.sendSystemMessage(Component.literal("§c[NOTICE] §fA player is near your bed!"));
                    }
                    mgr.setTier(team, BedUpgradeManager.UpgradeType.ALARM, 0);
                }
            }

            // Bed sense
            int bedSenseTier = mgr.getTier(team, BedUpgradeManager.UpgradeType.BED_SENSE);
            if (bedSenseTier > 0) {
                for (UUID enemyUuid : session.getPlayers()) {
                    if (session.isEliminated(enemyUuid)) continue;
                    if (session.getMode() == GameModeType.TEAMS) {
                        String myTeam = session.getPlayerTeam(uuid);
                        String enemyTeam = session.getPlayerTeam(enemyUuid);
                        if (myTeam != null && myTeam.equals(enemyTeam)) continue;
                    }
                    BlockPos enemyBed = session.getPlayerBed(enemyUuid);
                    if (enemyBed == null) continue;
                    double distToEnemyBed = player.blockPosition().distSqr(enemyBed);
                    if (distToEnemyBed <= 9 * 9 && !session.hasBedSenseActive(uuid)) {
                        // Bed sense trigger
                        session.setBedSenseTimer(uuid, 8 * 20);
                        player.sendSystemMessage(Component.literal("§e[BED SENSE] §fEnemy bed detected nearby!"));
                        mgr.setTier(team, BedUpgradeManager.UpgradeType.BED_SENSE, 0);
                        break;
                    }
                }
            }

            // tick active bed sense, now with proximity beeping and actionbar!
            if (session.hasBedSenseActive(uuid)) {
                // find closest enemy bed
                BlockPos closestBed = null;
                double closestDist = Double.MAX_VALUE;
                for (UUID enemyUuid : session.getPlayers()) {
                    if (session.isEliminated(enemyUuid)) continue;
                    if (session.getMode() == GameModeType.TEAMS) {
                        String myTeam = session.getPlayerTeam(uuid);
                        String enemyTeam = session.getPlayerTeam(enemyUuid);
                        if (myTeam != null && myTeam.equals(enemyTeam)) continue;
                    }
                    BlockPos bed = session.getPlayerBed(enemyUuid);
                    if (bed == null) continue;
                    double dist = player.blockPosition().distSqr(bed);
                    if (dist < closestDist) { closestDist = dist; closestBed = bed; }
                }

                if (closestBed != null) {
                    int timerTicks = session.getBedSenseTimer(uuid);
                    int secsLeft = timerTicks / 20;
                    int blockDist = (int) Math.sqrt(closestDist);

                    // actionbar distance readout
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                            Component.literal("§e[BED SENSE] §fEnemy bed ~" + blockDist + " blocks away §7(" + secsLeft + "s)")));

                    // prox beep bed sense
                    // beep every N ticks based on distance: 1 block = every 2t, 9 blocks = every 18t
                    int beepInterval = Math.max(2, blockDist * 2);
                    if (timerTicks % beepInterval == 0) {
                        // Bed sense prox beep pitch
                        int pitch = Math.max(10, 24 - blockDist);
                        player.serverLevel().playSound(player, closestBed,
                                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.get(),
                                net.minecraft.sounds.SoundSource.PLAYERS,
                                0.6f, SoundHelper.noteToPitch(pitch));
                    }
                }

                session.tickBedSenseTimer(uuid);

                if (!session.hasBedSenseActive(uuid)) {
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                            Component.literal("")));
                }
            }
        }
    }

    private void shrinkBorder(GameSession session, double targetSize, int durationSeconds) {
        net.minecraft.server.level.ServerLevel level = session.getLevel();
        net.minecraft.world.level.border.WorldBorder border = level.getWorldBorder();
        double currentSize = border.getSize();
        double newSize = Math.max(10, currentSize - (targetSize * 2.0));
        border.lerpSizeBetween(currentSize, newSize, durationSeconds * 1000L);
        net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket packet =
                new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(border);
        for (net.minecraft.server.level.ServerPlayer p : level.players()) {
            p.connection.send(packet);
        }
    }

    private String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return m + ":" + String.format("%02d", s);
    }

    private void broadcast(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}