package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.Component;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.WinManager;
import com.frosty.bedgunwars.ui.GameScoreboard;

import java.util.UUID;

@Mod.EventBusSubscriber
public class PlayerReconnectHandler {
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GameManager.hasGame()) return;

        GameSession session = GameManager.getSession();
        UUID uuid = player.getUUID();
        GamePhase phase = session.getPhase();

        // Host reconnecting
        if (uuid.equals(session.getHostUuid()) && session.isHostDisconnected()) {
            session.clearHostDisconnectTimer();
            player.sendSystemMessage(Component.literal("§aYou reconnected as host. Game continues."));
            GameScoreboard.reinitPlayer(uuid);
            GameScoreboard.update(session);
            return;
        }

        if (!session.getPlayers().contains(uuid)) return;
        session.markOnline(uuid);

        if (phase == GamePhase.PREPARATION) {
            session.getDisconnectedDuringPrep().remove(uuid);

            player.setGameMode(GameType.SURVIVAL);
            giveStarterItems(player, session);

            GameScoreboard.reinitPlayer(uuid);
            GameScoreboard.update(session);

            player.sendSystemMessage(Component.literal("§aYou reconnected. Game is in preparation phase."));
            return;
        }

        if (session.isEliminated(uuid)) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("You were eliminated."));
            GameScoreboard.update(session);
            return;
        }
        if (phase == GamePhase.ACTIVE) {
            if (session.isBedBroken(uuid)) {
                session.eliminatePlayer(uuid);
                player.setGameMode(GameType.SPECTATOR);
                player.sendSystemMessage(Component.literal("Your bed was destroyed while you were gone. You are eliminated."));
                WinManager.checkWinner(session);
                return;
            }
            player.setGameMode(GameType.SURVIVAL);
            player.sendSystemMessage(Component.literal("You reconnected and are still in the match."));
            GameScoreboard.update(session);
            GameScoreboard.reinitPlayer(player.getUUID());
        }
        if (phase == GamePhase.ENDING) {
            if (session.isEliminated(uuid)) {
                player.setGameMode(GameType.SPECTATOR);
            } else {
                player.setGameMode(GameType.SURVIVAL);
            }
            player.sendSystemMessage(Component.literal("§cYou reconnected during Endgame."));
            GameScoreboard.reinitPlayer(uuid);
            GameScoreboard.update(session);
        }
    }
    private static void giveStarterItems(ServerPlayer player, GameSession session) {
        UUID uuid = player.getUUID();

        // Only give bed if player hasn't placed one yet
        if (!session.hasPlacedBed(uuid)) {
            player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_BED, 1));
        }

        // Always restore these if missing
        if (player.getInventory().getItem(1).isEmpty())
            player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 1));
        if (player.getInventory().getItem(2).isEmpty())
            player.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE, 1));
        if (player.getInventory().getItem(3).isEmpty())
            player.getInventory().setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64));

        // Always restore armor if missing
        if (player.getInventory().armor.get(3).isEmpty())
            player.getInventory().armor.set(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));
        if (player.getInventory().armor.get(2).isEmpty())
            player.getInventory().armor.set(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));
        if (player.getInventory().armor.get(1).isEmpty())
            player.getInventory().armor.set(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_LEGGINGS));
        if (player.getInventory().armor.get(0).isEmpty())
            player.getInventory().armor.set(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_BOOTS));

        player.containerMenu.broadcastChanges();
    }
}

