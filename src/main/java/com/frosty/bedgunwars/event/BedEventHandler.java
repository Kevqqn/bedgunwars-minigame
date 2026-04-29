package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameModeType;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.event.level.BlockEvent;
import com.frosty.bedgunwars.game.SoundHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class BedEventHandler {

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!GameManager.hasGame()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) return;
        if (session.getPhase() != GamePhase.PREPARATION && session.getPhase() != GamePhase.ACTIVE) return;

        BlockState placed = event.getPlacedBlock();

        // Guard: ignore air blocks and anything that isn't a BedBlock
        // revents empty hand right-clicks from triggering this handler
        if (placed == null || placed.isAir() || !(placed.getBlock() instanceof BedBlock)) return;

        // verify the player is actually holding a bed item
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof BedItem)) return;

        UUID uuid = player.getUUID();
        if (!session.getPlayers().contains(uuid)) return;

        if (session.getMode() == GameModeType.TEAMS) {
            String team = session.getPlayerTeam(uuid);
            UUID designatedOwner = session.getTeamBedOwner(team);
            if (!uuid.equals(designatedOwner)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cOnly the designated bed owner on your team can place the bed."));
                return;
            }
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

        Direction facing = placed.getValue(BedBlock.FACING);
        BedPart part = placed.getValue(BedBlock.PART);

        BlockPos actualFootPos;
        BlockPos headPos;

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
        if (!GameManager.hasGame()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) return;

        BlockPos pos = event.getPos();
        UUID owner = session.getBedOwner(pos);
        if (owner == null) return;

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

                MinecraftServer server = session.getLevel().getServer();
                String breakerName = player.getName().getString();

                ServerPlayer bedOwnerPlayer = server.getPlayerList().getPlayer(owner);
                if (bedOwnerPlayer != null) {
                    sendNotice(bedOwnerPlayer, "Your bed has been broken! Survive at all cost.");
                }

                BlockPos bedPos = session.getPlayerBed(owner) != null ? session.getPlayerBed(owner) : pos;
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, session.getLevel());
                bolt.setPos(bedPos.getX() + 0.5, bedPos.getY(), bedPos.getZ() + 0.5);
                bolt.setVisualOnly(true);
                session.getLevel().addFreshEntity(bolt);

                server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 20, () -> {
                    String ownerName = server.getPlayerList().getPlayer(owner) != null
                            ? server.getPlayerList().getPlayer(owner).getName().getString()
                            : "A player";
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        sendNotice(p, ownerName + "'s bed has been destroyed by " + breakerName + "!");
                    }
                }));
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
}