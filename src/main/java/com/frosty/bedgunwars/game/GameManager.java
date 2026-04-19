package com.frosty.bedgunwars.game;

public class GameManager {
    private static GameSession currentSession;

    public static boolean hasGame() {
        return currentSession != null && currentSession.isActive();
    }

    public static GameSession getSession() {
        return currentSession;
    }

    public static void start(GameSession session) {
        currentSession = session;
    }

    public static void end() {
        currentSession = null;
    }
}