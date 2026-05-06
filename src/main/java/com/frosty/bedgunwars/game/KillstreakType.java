package com.frosty.bedgunwars.game;

public enum KillstreakType {
    UAV(3,          "UAV",          "Reveals all enemies on minimap for 15s"),
    PRIVATE_GLOW(5, "Private Glow", "Enemies glow only for you for 15s"),
    AIR_SUPPORT(8,  "Air Support",  "Call in 3 airstrike points"),
    JUGGERNAUT(12,  "Juggernaut",   "Become unstoppable");

    public final int killsRequired;
    public final String displayName;
    public final String description;

    KillstreakType(int k, String d, String desc) {
        killsRequired = k; displayName = d; description = desc;
    }
}