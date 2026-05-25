package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.*;
import com.frosty.bedgunwars.game.TipsManager;
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
                int newBalance = session.getMoney(killer.getUUID());
                killer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                        Component.literal("§a+$150 §7| §eBalance: §f$" + newBalance)));
                session.getKillstreakManager().onKill(killer.getUUID(), killer.getServer(), session);

                // Death feed packet sends gun HUD texture + names to all clients
                net.minecraft.world.item.ItemStack held = killer.getMainHandItem();
                if (held.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                    net.minecraft.resources.ResourceLocation gunId = iGun.getGunId(held);
                    if (gunId != null) {
                        com.frosty.bedgunwars.network.DeathFeedPacket pkt =
                                new com.frosty.bedgunwars.network.DeathFeedPacket(
                                        killer.getName().getString(),
                                        player.getName().getString(),
                                        gunId.getNamespace(),
                                        gunId.getPath());
                        com.frosty.bedgunwars.network.PacketHandler.sendToAllClients(
                                killer.getServer(), pkt);
                    }
                }

                // Deathmatch kill tracking
                if (session.isDeathmatch()) {
                    String killKey = session.getMode() == GameModeType.DEATHMATCH_TEAMS
                            ? session.getPlayerTeam(killer.getUUID())
                            : killer.getUUID().toString();
                    if (killKey != null) {
                        session.getDeathmatchManager().addKill(killKey);
                        int kills = session.getDeathmatchManager().getKills(killKey);
                        int limit = session.getKillLimit();
                        killer.sendSystemMessage(Component.literal("§e[DM] §fKills: " + kills + "/" + limit));
                        // Check win condition
                        WinManager.checkDeathmatchWinner(session);
                    }
                }
            }
        }

        // Track death
        session.addDeath(uuid);
        session.getKillstreakManager().onDeath(uuid, player.getServer(), session);
        if (session.getMode() == GameModeType.SOLO || session.getMode() == GameModeType.TEAMS) {
            TipsManager.sendTip(player, "12");
        }

        // Deathmatch death never eliminate, always respawn
        if (session.isDeathmatch() && session.getPhase() == GamePhase.ACTIVE) {
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth());
            net.minecraft.core.BlockPos killerPos = null;
            if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                killerPos = killer.blockPosition();
            }
            scheduleDeathmatchRespawn(player, session, killerPos);
            return;
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
            // gives 5-second spawn immunity drops immediately on dealing damage
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false, false));
            session.markSpawnImmune(uuid);
            applyLoadoutToPlayer(p, uuid, session);
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

    private String getKillerWeapon(ServerPlayer killer) {
        net.minecraft.world.item.ItemStack held = killer.getMainHandItem();
        if (held.isEmpty()) return "Unknown";
        if (held.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
            net.minecraft.resources.ResourceLocation id = iGun.getGunId(held);
            if (id != null) return GunHelper.getGunDisplayName(id);
        }
        return held.getHoverName().getString();
    }

    private boolean isSafeSpawn(GameSession session, net.minecraft.core.BlockPos pos) {
        var level = session.getLevel();
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && !level.getBlockState(pos.below()).isAir();
    }
    private void scheduleDeathmatchRespawn(ServerPlayer player, GameSession session, net.minecraft.core.BlockPos killerPos) {
        UUID uuid = player.getUUID();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        sendNotice(player, "Respawning in §e3§f...");

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

            net.minecraft.core.BlockPos spawnBeacon;
            if (session.getMode() == GameModeType.DEATHMATCH_TEAMS) {
                String team = session.getPlayerTeam(uuid);
                spawnBeacon = session.getDeathmatchManager().getTeamRespawnBeacon(team);
            } else {
                spawnBeacon = getSafeDeathmatchBeacon(session, killerPos);
            }

            net.minecraft.core.BlockPos dest = spawnBeacon != null
                    ? (findSafeSpawnNearBeacon(session, spawnBeacon) != null
                       ? findSafeSpawnNearBeacon(session, spawnBeacon)
                       : spawnBeacon.above(2))
                    : p.blockPosition();

            // Set SURVIVAL first so teleport takes effect on client
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setHealth(p.getMaxHealth());

            // Teleport preserving look direction without resetting rotation
            p.connection.teleport(
                    dest.getX() + 0.5, dest.getY() + 0.1, dest.getZ() + 0.5,
                    p.getYRot(), p.getXRot());

            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false, false));
            session.markSpawnImmune(uuid);
            applyLoadoutToPlayer(p, uuid, session);
            sendNotice(p, "Respawned! §7(Immune for §e5s§7 — drops on attack)");
        });
    }

    private net.minecraft.core.BlockPos findSafeSpawnNearBeacon(GameSession session, net.minecraft.core.BlockPos beacon) {
        for (int radius = 1; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        net.minecraft.core.BlockPos candidate = beacon.offset(dx, dy, dz);
                        if (isSafeSpawn(session, candidate)) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private net.minecraft.core.BlockPos getSafeDeathmatchBeacon(GameSession session, net.minecraft.core.BlockPos killerPos) {
        java.util.List<net.minecraft.core.BlockPos> beacons = session.getDeathmatchManager().getAllBeacons();
        if (beacons.isEmpty()) return null;
        if (killerPos == null || beacons.size() == 1) return beacons.get(new java.util.Random().nextInt(beacons.size()));

        // filter out beacons within 20 blocks of the killer
        int minDistSq = 20 * 20;
        java.util.List<net.minecraft.core.BlockPos> safeBeacons = beacons.stream()
                .filter(b -> b.distSqr(killerPos) >= minDistSq)
                .collect(java.util.stream.Collectors.toList());

        // if all beacons are too close, fall back to the furthest one
        if (safeBeacons.isEmpty()) {
            return beacons.stream()
                    .max(java.util.Comparator.comparingDouble(b -> b.distSqr(killerPos)))
                    .orElse(beacons.get(0));
        }

        return safeBeacons.get(new java.util.Random().nextInt(safeBeacons.size()));
    }

    private void applyLoadoutToPlayer(ServerPlayer p, UUID uuid, GameSession session) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack s = p.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof com.tacz.guns.api.item.IGun)
                p.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
        java.util.List<net.minecraft.resources.ResourceLocation> guns =
                session.getGunSelectionManager().getGunSelections(uuid);
        for (int i = 0; i < guns.size(); i++) {
            net.minecraft.resources.ResourceLocation id = guns.get(i);
            net.minecraft.world.item.ItemStack stack = GunHelper.buildGun(id);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                var atts = session.getGunSelectionManager().getGunAttachments(uuid, i);
                for (var e : atts.entrySet()) {
                    try {
                        net.minecraft.world.item.ItemStack att =
                                com.tacz.guns.api.item.builder.AttachmentItemBuilder
                                        .create().setId(e.getValue()).build();
                        if (!att.isEmpty() && iGun.allowAttachment(stack, att))
                            iGun.installAttachment(stack, att);
                    } catch (Exception ignored) {}
                }
                com.tacz.guns.api.TimelessAPI.getCommonGunIndex(id).ifPresent(idx -> {
                    int max = idx.getGunData().getAmmoAmount();
                    if (max > 0) iGun.setCurrentAmmoCount(stack, max);
                });
            }
            p.getInventory().add(stack);
        }
        java.util.List<net.minecraft.resources.ResourceLocation> allGuns =
                com.frosty.bedgunwars.game.GunSelectionManager.getAllAvailableGuns();
        GunHelper.removeAllGunAmmo(p, allGuns);
        GunHelper.giveAmmoReserves(p, guns, false);
    }
}