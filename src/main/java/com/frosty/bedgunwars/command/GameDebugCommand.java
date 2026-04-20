package com.frosty.bedgunwars.command;

import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.GameCleanupManager;
import com.frosty.bedgunwars.game.WinManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.context.CommandContext;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class GameDebugCommand {
    public static int eliminate(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer target = null;
        try {
            target = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("Error: Invalid player"));
            return 0;
        }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }

        if (session.getHostUuid().equals(target.getUUID())) {
            source.sendFailure(Component.literal("Cannot eliminate the host."));
            return 0;
        }

        session.eliminatePlayer(target.getUUID());
        source.sendSuccess(() -> Component.literal(target.getName() + " has been eliminated."), false);
        return 1;
    }

    public static int forceWin(CommandSourceStack source) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }

        session.setPhase(GamePhase.WINNER_ANNOUNCED);
        session.setWinner("Debug Winner");
        session.setWinnerDelay(60);  // Fixed method for winner delay
        source.sendSuccess(() -> Component.literal("Force-winning the game with 'Debug Winner'."), true);
        return 1;
    }

    public static int setPhase(CommandSourceStack source, String phaseName) {
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }

        GamePhase phase = GamePhase.valueOf(phaseName.toUpperCase());
        session.setPhase(phase);
        source.sendSuccess(() -> Component.literal("Phase set to: " + phaseName), false);
        return 1;
    }

    public static int eliminate(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer target = null;
        try {
            target = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("Error: Invalid player"));
            return 0;
        }
        GameSession session = GameManager.getSession();
        if (session == null || !session.isActive()) {
            source.sendFailure(Component.literal("No active game."));
            return 0;
        }

        if (session.getHostUuid().equals(target.getUUID())) {
            source.sendFailure(Component.literal("Cannot eliminate the host."));
            return 0;
        }

        session.eliminatePlayer(target.getUUID());
        source.sendSuccess(() -> Component.literal(target.getName() + " has been eliminated."), false);
        return 1;
    }
}