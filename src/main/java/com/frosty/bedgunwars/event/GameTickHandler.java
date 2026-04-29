package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.*;
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
import java.util.Map;

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
        if (event.phase != TickEvent.Phase.END) return;
        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        GamePhase phase = session.getPhase();

        if (phase == GamePhase.PREPARATION) {
            applyPrepEffects(event.getServer(), session);
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
            session.decreaseWinnerDelay();
            if (session.getWinnerDelayTicks() <= 0) {
                GameCleanupManager.restoreAndEnd(
                        event.getServer(), session,
                        session.getWinnerName() + " wins! Game over."
                );
            }
        }
    }

    private void applyPrepEffects(MinecraftServer server, GameSession session) {
        List<UUID> playerUuids = session.getPlayers().stream().toList();
        List<ServerPlayer> onlinePlayers = playerUuids.stream()
                .map(uuid -> server.getPlayerList().getPlayer(uuid))
                .filter(p -> p != null)
                .toList();

        for (ServerPlayer target : onlinePlayers) {
            for (ServerPlayer viewer : onlinePlayers) {
                if (viewer.getUUID().equals(target.getUUID())) continue;
                // Remove target from viewer's client
                viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(target.getUUID())));
            }
            // Keep damage resistance — no invisibility effect needed
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        }
    }

    private void liftPrepEffects(MinecraftServer server, GameSession session) {
        List<UUID> playerUuids = session.getPlayers().stream().toList();
        List<ServerPlayer> onlinePlayers = playerUuids.stream()
                .map(uuid -> server.getPlayerList().getPlayer(uuid))
                .filter(p -> p != null)
                .toList();

        for (ServerPlayer target : onlinePlayers) {
            for (ServerPlayer viewer : onlinePlayers) {
                if (viewer.getUUID().equals(target.getUUID())) continue;
                // Re-add target to viewer's client
                viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(target)));
                viewer.connection.send(new ClientboundAddEntityPacket(target));
                // Sync entity data (skin, held item, etc.)
                var entityData = target.getEntityData().getNonDefaultValues();
                if (entityData != null && !entityData.isEmpty()) {
                    viewer.connection.send(new ClientboundSetEntityDataPacket(target.getId(), entityData));
                }
            }
            target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
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
        Map<UUID, BlockPos> lastPos       = session.getLastKnownPositions();
        Map<UUID, Integer>  noMoveTicks   = session.getNoMoveTicks();
        Map<UUID, Integer>  glowTicks     = session.getGlowTicks();

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

                if (ticks >= session.getNoMoveThreshold()) {
                    // Reveal
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, session.getGlowDuration(), 0, false, false));
                    glowTicks.put(uuid, session.getGlowDuration());
                    noMoveTicks.put(uuid, 0);

                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.sendSystemMessage(Component.literal("§6[NOTICE] §eA player has been revealed due to not moving."));
                    }
                }
            } else {
                noMoveTicks.put(uuid, 0);
            }

            lastPos.put(uuid, currentPos);
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