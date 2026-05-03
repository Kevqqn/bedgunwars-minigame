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
        if (event.phase != TickEvent.Phase.END) return;
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

        if (phase == GamePhase.PREPARATION) {
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
            } else if (ticksLeft == 60 * 20 || ticksLeft == 30 * 20 || ticksLeft == 10 * 20) {
                broadcast(event.getServer(), "Game starts in " + secondsLeft + "s!");
            }


            session.decreasePrepTime();

            if (session.getPrepTimeTicks() <= 0) {
                session.setPhase(GamePhase.ACTIVE);
                liftPrepEffects(event.getServer(), session);
                event.getServer().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, event.getServer());
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    SoundHelper.playNoteClick(p, SoundHelper.noteToPitch(25));
                }
                broadcast(event.getServer(), "Game has started! Destroy enemy beds!");
            }
            GameScoreboard.update(session);
        }

        else if (phase == GamePhase.ACTIVE) {
            session.decreaseMatchTime();
            int ticksLeft = session.getMatchTimeTicks();
            int initialTicks = session.getInitialMatchTicks();
            int secondsLeft = ticksLeft / 20;

            float progress = initialTicks > 0 ? (float) ticksLeft / initialTicks : 0f;
            BossBarManager.show(event.getServer(), "Match: " + formatTime(secondsLeft) + " remaining", progress);

            if (ticksLeft == 60 * 20 || ticksLeft == 30 * 20 || ticksLeft == 10 * 20
                    || ticksLeft == 5 * 20 || ticksLeft == 4 * 20 || ticksLeft == 3 * 20
                    || ticksLeft == 2 * 20 || ticksLeft == 1 * 20) {
                broadcast(event.getServer(), "Match ends in " + secondsLeft + "s!");
            }

            if (ticksLeft <= 0) {
                startEndgame(event.getServer(), session);
            }
            GameScoreboard.update(session);
            tickBedUpgrades(event.getServer(), session);
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
        }

        else if (phase == GamePhase.WINNER_ANNOUNCED) {
            GameScoreboard.update(session);
            session.removeNametagTeams(event.getServer());
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
                // Player moved — reset counter and cancel warning if active
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

            // Healing Station
            int healTier = mgr.getTier(team, BedUpgradeManager.UpgradeType.HEALING_STATION);
            if (healTier > 0) {
                boolean inRange = switch (healTier) {
                    case 1 -> distToBed <= 10 * 10;
                    case 2 -> distToBed <= 30 * 30;
                    case 3 -> distToBed <= 10 * 10;
                    case 4 -> distToBed <= 20 * 20;
                    case 5, 6 -> true; // permanent
                    default -> false;
                };
                int amplifier = (healTier >= 3 && healTier <= 4) ? 1 : // Regen II tiers
                        (healTier >= 5) ? (healTier == 6 ? 0 : 1) : 0;
                if (inRange) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.REGENERATION, 40, amplifier, false, false));
                }
            }

            // Check enemies near bed (for traps)
            for (UUID enemyUuid : session.getPlayers()) {
                if (session.isEliminated(enemyUuid)) continue;
                if (enemyUuid.equals(uuid)) continue;
                // Skip teammates
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
                    // T6 check: only if owner is within 10 blocks
                    if (mfTier == 6 && distToBed > 10 * 10) {
                        // owner is far — T6 always-on requires owner nearby
                    } else {
                        int[] mfDuration = {0, 40, 80, 120, 160, 120, Integer.MAX_VALUE};
                        int[] mfLevel    = {1,  1,  2,   2,   2,   3,  2};
                        enemy.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN,
                                mfDuration[mfTier], mfLevel[mfTier], false, true));
                        // Reset to T1 after trigger (except T6 which is permanent)
                        if (mfTier < 6) mgr.setTier(team, BedUpgradeManager.UpgradeType.MINING_FATIGUE, 1);
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

            // Bed Sense (offensive, alert player near enemy beds)
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
                    if (distToEnemyBed <= 5 * 5) {
                        SoundHelper.playNoteClick(player, SoundHelper.noteToPitch(22));
                        player.sendSystemMessage(Component.literal("§e[NOTICE] §fYou are near someone's bed!"));
                        mgr.setTier(team, BedUpgradeManager.UpgradeType.BED_SENSE, 0);
                        break;
                    }
                }
            }
        }
    }

    private void shrinkBorder(GameSession session, double targetSize, int durationSeconds) {
        WorldBorder border = session.getLevel().getWorldBorder();
        double currentSize = border.getSize();
        double newSize = Math.max(10, currentSize - (targetSize * 2.0));
        border.lerpSizeBetween(currentSize, newSize, 20 * 1000L);
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