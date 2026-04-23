package com.frosty.bedgunwars.game;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;

public class SoundHelper {

    public static void playNoteClick(ServerPlayer player, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
                SoundEvents.NOTE_BLOCK_HARP,
                SoundSource.MASTER,
                player.getX(), player.getY(), player.getZ(),
                1.0f, pitch, 0L
        ));
    }

    public static void playLevelUp(ServerPlayer player) {
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EXPERIENCE_ORB_PICKUP),
                SoundSource.MASTER,
                player.getX(), player.getY(), player.getZ(),
                1.0f, 1.0f, 0L
        ));
    }

    public static float noteToPitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }
    public static void playWitherDeath(ServerPlayer player) {
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WITHER_DEATH),
                SoundSource.MASTER,
                player.getX(), player.getY(), player.getZ(),
                1.0f, 1.0f, 0L
        ));
    }
}