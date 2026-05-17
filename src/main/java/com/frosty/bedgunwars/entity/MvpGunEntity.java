package com.frosty.bedgunwars.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class MvpGunEntity extends Entity implements GeoEntity {

    private static final RawAnimation MVP_ANIM =
            RawAnimation.begin().thenPlay("animation.mvp.cinematic");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MvpGunEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "mvp_gun_controller", 0, state ->
                state.setAndContinue(MVP_ANIM)
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private int animTick = 0;

    @Override
    public void tick() {
        super.tick();
        animTick++;
        if (animTick % 5 == 0) {
            com.frosty.bedgunwars.BedGunWars.LOGGER.info("[MvpGun] animTick={}", animTick);
        }
    }

    public int getAnimTick() { return animTick; }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}