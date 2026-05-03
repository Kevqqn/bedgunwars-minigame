package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import com.frosty.bedgunwars.event.GameTickHandler;
import net.minecraft.world.level.GameType;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class PlayerDeathHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) return;
        if (session.getPhase() != GamePhase.ACTIVE && session.getPhase() != GamePhase.ENDING) return;

        UUID uuid = player.getUUID();
        if (!session.getPlayers().contains(uuid)) return;
        if (session.isEliminated(uuid)) return;

        // Credit kill to attacker
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            if (!killer.getUUID().equals(uuid)) {
                session.addKill(killer.getUUID());
                session.addMoney(killer.getUUID(), 150);
                killer.sendSystemMessage(Component.literal("§a+$150 §7(Kill)"));
            }
        }

        if (session.getPhase() == GamePhase.ENDING) {
            // Cancel death, eliminate, set spectator
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            session.eliminatePlayer(uuid);
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            sendNotice(player, "You are eliminated!");
            WinManager.checkWinner(session);
            return;
        }

        if (session.getMode() == GameModeType.SOLO) {
            boolean hasBed = session.hasPlacedBed(uuid);
            boolean bedBroken = session.isBedBroken(uuid);
            if (hasBed && !bedBroken) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                scheduleRespawn(player, session);
            } else {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                session.eliminatePlayer(uuid);
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                sendNotice(player, "You are eliminated!");
                WinManager.checkWinner(session);
            }
        } else {
            String team = session.getPlayerTeam(uuid);
            boolean teamBedBroken = TeamManager.isTeamBedBroken(session, team);
            if (!teamBedBroken) {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                scheduleRespawn(player, session);
            } else {
                event.setCanceled(true);
                player.setHealth(player.getMaxHealth());
                session.eliminatePlayer(uuid);
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                sendNotice(player, "You are eliminated!");
                WinManager.checkWinner(session);
            }
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!GameManager.hasGame()) return;
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) return;
        if (session.getPhase() == GamePhase.PREPARATION) { event.setCanceled(true); return; }

        // Drop spawn immunity from the attacker the moment they deal damage
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (session.isSpawnImmune(attacker.getUUID())) {
                session.clearSpawnImmune(attacker.getUUID());
                attacker.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
                sendNotice(attacker, "Spawn immunity dropped!");
            }
        }
        if (session.isFriendlyFire()) return;
        if (session.getMode() == GameModeType.SOLO) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        String victimTeam   = session.getPlayerTeam(victim.getUUID());
        String attackerTeam = session.getPlayerTeam(attacker.getUUID());
        if (victimTeam != null && victimTeam.equals(attackerTeam)) event.setCanceled(true);
    }

    private void sendNotice(ServerPlayer player, String message) {
        net.minecraft.network.chat.MutableComponent prefix =
                Component.literal("[")
                        .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GOLD))
                        .append(Component.literal("NOTICE")
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)))
                        .append(Component.literal("] ")
                                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GOLD)));
        player.sendSystemMessage(prefix.append(Component.literal(message)));
        SoundHelper.playNoteClick(player, SoundHelper.noteToPitch(20));
    }

    private void scheduleRespawn(ServerPlayer player, GameSession session) {
        UUID uuid = player.getUUID();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Put into spectator immediately
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);

        sendNotice(player, "Bed is safe! Respawning in §e3§f...");
        GameTickHandler.scheduleTask(20, () -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null || !session.isActive()) return;
            sendNotice(p, "Respawning in §e2§f...");
        });
        GameTickHandler.scheduleTask(40, () -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null || !session.isActive()) return;
            sendNotice(p, "Respawning in §e1§f...");
        });
        GameTickHandler.scheduleTask(60, () -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null || !session.isActive()) return;

            // Find respawn position near bed
            net.minecraft.core.BlockPos bedPos = getRespawnPos(session, uuid);
            if (bedPos != null) {
                p.teleportTo(session.getLevel(),
                        bedPos.getX() + 0.5, bedPos.getY() + 0.1, bedPos.getZ() + 0.5,
                        p.getYRot(), p.getXRot());
            }
            p.setHealth(p.getMaxHealth());
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            // gives 5-second spawn immunity — drops immediately on dealing damage
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false, false));
            session.markSpawnImmune(uuid);
            GunHelper.reloadAllGuns(p, session.getGunSelectionManager());
            sendNotice(p, "Respawned! §7(Immune for §e5s§7 — drops on attack)");
        });
    }

    private net.minecraft.core.BlockPos getRespawnPos(GameSession session, UUID uuid) {
        net.minecraft.core.BlockPos bedPos;
        if (session.getMode() == GameModeType.TEAMS) {
            String team = session.getPlayerTeam(uuid);
            UUID bedOwner = session.getTeamBedOwner(team);
            bedPos = bedOwner != null ? session.getPlayerBed(bedOwner) : null;
        } else {
            bedPos = session.getPlayerBed(uuid);
        }
        if (bedPos == null) return null;
        for (int radius = 1; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        net.minecraft.core.BlockPos candidate = bedPos.offset(dx, dy, dz);
                        if (isSafeSpawn(session, candidate)) return candidate;
                    }
                }
            }
        }
        return bedPos.above();
    }

    private boolean isSafeSpawn(GameSession session, net.minecraft.core.BlockPos pos) {
        var level = session.getLevel();
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir();
    }
}