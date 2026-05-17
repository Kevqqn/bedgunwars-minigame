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

public class MvpCharacterEntity extends Entity implements GeoEntity {

    private static final RawAnimation MVP_ANIM =
            RawAnimation.begin().thenPlay("animation.mvp.cinematic");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Skin state set from MvpPacket on client
    public boolean isSlim = false;

    public void setSlim(boolean slim) {
        this.isSlim = slim;
    }
    // animTick drives animation playback; set client-side from startTick
    public long startTick = 0;

    public MvpCharacterEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(false);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "mvp_controller", 0, state ->
                state.setAndContinue(MVP_ANIM)
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}