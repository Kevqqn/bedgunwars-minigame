package com.frosty.bedgunwars.game;

public enum GameModeType {
    SOLO,
    TEAMS,
    DEATHMATCH_SOLO,
    DEATHMATCH_TEAMS;

    public boolean isDeathmatch() {
        return this == DEATHMATCH_SOLO || this == DEATHMATCH_TEAMS;
    }
}