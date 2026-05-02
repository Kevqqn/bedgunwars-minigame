package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.*;
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

                // Award money to the player who broke the bed
                session.addMoney(breaker, 500);
                player.sendSystemMessage(Component.literal("§a+$500 §7(Bed Destroyed)"));

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
    @SubscribeEvent
    public void onPlayerInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GameManager.hasGame()) return;
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) return;
        if (session.getPhase() != GamePhase.PREPARATION && session.getPhase() != GamePhase.ACTIVE) return;

        BlockPos pos = event.getPos();
        net.minecraft.world.level.block.state.BlockState state = event.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return;

        UUID uuid = player.getUUID();
        if (!session.getPlayers().contains(uuid)) return;

        // Check if this is the player's own bed or their team's bed
        String team = BedUpgradeMenu.getTeamKey(player, session);
        UUID bedOwner = session.getMode() == GameModeType.TEAMS
                ? session.getTeamBedOwner(session.getPlayerTeam(uuid))
                : uuid;
        if (bedOwner == null) return;
        BlockPos bedPos = session.getPlayerBed(bedOwner);
        if (bedPos == null) return;

        // Check both foot and head of bed
        net.minecraft.core.Direction facing = state.getValue(BedBlock.FACING);
        BlockPos headPos = pos.relative(facing);
        if (!pos.equals(bedPos) && !headPos.equals(bedPos) && !pos.relative(facing.getOpposite()).equals(bedPos)) return;

        event.setCanceled(true);
        event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        BedUpgradeMenu.open(player, session);
    }
    @SubscribeEvent
    public void onCompassUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof net.minecraft.world.item.CompassItem)) return;
        if (!held.hasCustomHoverName()) return;
        if (!held.getHoverName().getString().contains("Bed Teleport")) return;
        if (!GameManager.hasGame()) return;
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) return;

        UUID uuid = player.getUUID();
        UUID bedOwner = session.getMode() == GameModeType.TEAMS
                ? session.getTeamBedOwner(session.getPlayerTeam(uuid))
                : uuid;
        if (bedOwner == null) return;
        net.minecraft.core.BlockPos bedPos = session.getPlayerBed(bedOwner);
        if (bedPos == null) {
            player.sendSystemMessage(Component.literal("§cYour bed has not been placed yet!"));
            return;
        }

        player.teleportTo(player.serverLevel(),
                bedPos.getX() + 0.5, bedPos.getY() + 1, bedPos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("§aTeleported to your bed!"));

        // Consume compass and reset upgrade to T0
        held.shrink(1);
        String team = BedUpgradeMenu.getTeamKey(player, session);
        session.getBedUpgradeManager().setTier(team, BedUpgradeManager.UpgradeType.TP_TO_BED, 0);
        event.setCanceled(true);
    }
    @SubscribeEvent
    public void onSleepInBed(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) {
        if (!GameManager.hasGame()) return;
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) return;
        if (session.getPhase() != GamePhase.PREPARATION && session.getPhase() != GamePhase.ACTIVE) return;
        event.setResult(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
    }
}