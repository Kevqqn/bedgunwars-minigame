package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameModeType;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class BedEventHandler {

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!GameManager.hasGame()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            return;
        }

        if (session.getPhase() != GamePhase.PREPARATION && session.getPhase() != GamePhase.ACTIVE) {
            return;
        }

        BlockState state = event.getPlacedBlock();
        if (!(state.getBlock() instanceof BedBlock)) {
            return;
        }

        UUID uuid = player.getUUID();

        if (!session.getPlayers().contains(uuid)) {
            return;
        }

        if (session.hasPlacedBed(uuid)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("You can only place one bed."));
            return;
        }

        BlockPos footPos = event.getPos();
        if (!isInsideBorder(session, footPos)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("You must place your bed inside the border."));
            return;
        }

        Direction facing = state.getValue(BedBlock.FACING);
        BedPart part = state.getValue(BedBlock.PART);

        BlockPos headPos;
        BlockPos actualFootPos;

        if (part == BedPart.FOOT) {
            actualFootPos = footPos;
            headPos = footPos.relative(facing);
        } else {
            headPos = footPos;
            actualFootPos = footPos.relative(facing.getOpposite());
        }

        if (!isInsideBorder(session, headPos)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("The full bed must be inside the border."));
            return;
        }

        session.setPlayerBed(uuid, actualFootPos, headPos);
        player.sendSystemMessage(Component.literal("Bed placed successfully."));
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!GameManager.hasGame()) {
            return;
        }

        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            return;
        }

        BlockPos pos = event.getPos();
        UUID owner = session.getBedOwner(pos);

        if (owner == null) {
            return;
        }

        UUID breaker = player.getUUID();

        if (session.getPhase() == GamePhase.PREPARATION) {
            if (!owner.equals(breaker)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("You cannot break other players' beds during preparation."));
                return;
            }

            session.removePlayerBed(owner);
            player.sendSystemMessage(Component.literal("Your bed was removed. You may place it again."));
            return;
        }

        if (session.getPhase() == GamePhase.ACTIVE) {
            if (owner.equals(breaker)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("You cannot break your own bed after preparation."));
                return;
            }

            if (session.getMode() == GameModeType.TEAMS) {
                String ownerTeam = session.getPlayerTeam(owner);
                String breakerTeam = session.getPlayerTeam(breaker);

                if (ownerTeam != null && ownerTeam.equals(breakerTeam)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("You cannot break your teammate's bed."));
                    return;
                }
            }

            if (!session.isBedBroken(owner)) {
                session.breakBed(owner);
                player.sendSystemMessage(Component.literal("A tracked bed was broken."));
            }
        }
    }

    private boolean isInsideBorder(GameSession session, BlockPos pos) {
        int radius = session.getBorderRadius();
        BlockPos center = session.getBeaconPos();

        int dx = Math.abs(pos.getX() - center.getX());
        int dz = Math.abs(pos.getZ() - center.getZ());

        return dx <= radius && dz <= radius;
    }
}