//package com.frosty.bedgunwars.event;
//
//import com.frosty.bedgunwars.game.GameManager;
//import com.frosty.bedgunwars.game.GameModeType;
//import com.frosty.bedgunwars.game.GameSession;
//import net.minecraft.core.BlockPos;
//import net.minecraft.server.level.ServerPlayer;
//import net.minecraft.world.level.GameType;
//import net.minecraftforge.event.entity.player.PlayerEvent;
//import net.minecraftforge.eventbus.api.SubscribeEvent;
//
//import java.util.UUID;
//
//public class PlayerRespawnHandler {
//
//    @SubscribeEvent
//    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
//        if (!(event.getEntity() instanceof ServerPlayer player)) return;
//        if (!GameManager.hasGame()) return;
//
//        GameSession session = GameManager.getSession();
//        if (session == null || !session.isActive()) return;
//
//        UUID uuid = player.getUUID();
//        if (!session.getPlayers().contains(uuid)) return;
//
//        if (session.isEliminated(uuid)) {
//            player.setGameMode(GameType.SPECTATOR);
//            return;
//        }
//
//        if (session.getPendingRespawnPlayers().contains(uuid)) {
//            BlockPos bedPos = getRespawnPos(session, uuid);
//            if (bedPos != null) {
//                player.teleportTo(player.serverLevel(),
//                        bedPos.getX() + 0.5, bedPos.getY(), bedPos.getZ() + 0.5,
//                        player.getYRot(), player.getXRot());
//            }
//            session.clearPendingRespawn(uuid);
//            player.setGameMode(GameType.SURVIVAL);
//        }
//    }
//
//    private BlockPos getRespawnPos(GameSession session, UUID uuid) {
//        BlockPos bedPos;
//        if (session.getMode() == GameModeType.TEAMS) {
//            String team = session.getPlayerTeam(uuid);
//            UUID bedOwner = session.getTeamBedOwner(team);
//            bedPos = bedOwner != null ? session.getPlayerBed(bedOwner) : null;
//        } else {
//            bedPos = session.getPlayerBed(uuid);
//        }
//        if (bedPos == null) return null;
//
//        for (int radius = 1; radius <= 5; radius++) {
//            for (int dx = -radius; dx <= radius; dx++) {
//                for (int dz = -radius; dz <= radius; dz++) {
//                    for (int dy = 0; dy <= 3; dy++) {
//                        BlockPos candidate = bedPos.offset(dx, dy, dz);
//                        if (isSafeSpawn(session, candidate)) return candidate;
//                    }
//                }
//            }
//        }
//        return bedPos.above();
//    }
//
//    private boolean isSafeSpawn(GameSession session, BlockPos pos) {
//        var level = session.getLevel();
//        return level.getBlockState(pos).isAir()
//                && level.getBlockState(pos.above()).isAir()
//                && !level.getBlockState(pos.below()).isAir();
//    }
//}