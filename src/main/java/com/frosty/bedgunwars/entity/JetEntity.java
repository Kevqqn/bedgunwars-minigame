package com.frosty.bedgunwars.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class JetEntity extends Entity {

    private Vec3 velocity = Vec3.ZERO;
    private int despawnTimer = 0;
    public static final int DESPAWN_TICKS = 400;
    public static float SPEED = 6.0f;

    public JetEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setVelocity(Vec3 vel) {
        this.velocity = vel;
        this.setDeltaMovement(vel);
        double dx = vel.x, dz = vel.z;
        this.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        this.yRotO = this.getYRot();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        this.setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        if (++despawnTimer >= DESPAWN_TICKS) this.discard();
    }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}