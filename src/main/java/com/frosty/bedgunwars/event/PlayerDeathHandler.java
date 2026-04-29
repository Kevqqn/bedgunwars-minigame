package com.frosty.bedgunwars.event;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.WinManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import com.frosty.bedgunwars.game.SoundHelper;
import com.frosty.bedgunwars.game.TeamManager;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import com.frosty.bedgunwars.game.GameModeType;
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
            }
        }

        if (session.getPhase() == GamePhase.ENDING) {
            session.eliminatePlayer(uuid);
            sendNotice(player, "You are eliminated!");
            WinManager.checkWinner(session);
            return;
        }

        if (session.getMode() == GameModeType.SOLO) {
            boolean hasBed = session.hasPlacedBed(uuid);
            boolean bedBroken = session.isBedBroken(uuid);
            if (hasBed && !bedBroken) {
                session.markPendingRespawn(uuid);
                sendNotice(player, "Bed is safe, respawning...");
            } else {
                session.eliminatePlayer(uuid);
                sendNotice(player, "You are eliminated!");
                WinManager.checkWinner(session);
            }
        } else {
            String team = session.getPlayerTeam(uuid);
            boolean teamBedBroken = TeamManager.isTeamBedBroken(session, team);
            if (!teamBedBroken) {
                session.markPendingRespawn(uuid);
                sendNotice(player, "Your team's bed is safe, respawning...");
            } else {
                session.eliminatePlayer(uuid);
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
}