package com.frosty.bedgunwars.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import com.frosty.bedgunwars.game.GameManager;
import com.frosty.bedgunwars.game.GameSession;
import com.frosty.bedgunwars.game.GamePhase;
import com.frosty.bedgunwars.game.WinManager;

public class GameDebugCommand {

    public static int eliminate(CommandSourceStack source) {
        if (!GameManager.hasGame()) return 0;

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        GameSession session = GameManager.getSession();

        if (!player.getUUID().equals(session.getHostUuid())) return 0;

        if (session.isEliminated(player.getUUID())) return 0;

        session.handlePlayerDisconnect(player.getUUID(), true);

        WinManager.checkWinner(session);

        source.sendSuccess(() -> Component.literal("eliminated"), false);
        return 1;
    }

    public static int forceWin(CommandSourceStack source) {
        if (!GameManager.hasGame()) return 0;

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        GameSession session = GameManager.getSession();

        if (!player.getUUID().equals(session.getHostUuid())) return 0;

        session.setPhase(GamePhase.WINNER_ANNOUNCED);

        source.sendSuccess(() -> Component.literal("forced"), false);
        return 1;
    }

    public static int setPhase(CommandSourceStack source, String name) {
        if (!GameManager.hasGame()) return 0;

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        GameSession session = GameManager.getSession();

        if (!player.getUUID().equals(session.getHostUuid())) return 0;

        GamePhase phase;

        try {
            phase = GamePhase.valueOf(name.toUpperCase());
        } catch (Exception e) {
            source.sendFailure(Component.literal("invalid"));
            return 0;
        }

        session.setPhase(phase);

        source.sendSuccess(() -> Component.literal("phase " + phase.name()), false);
        return 1;
    }
}