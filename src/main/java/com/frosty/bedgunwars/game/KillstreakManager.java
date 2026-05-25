package com.frosty.bedgunwars.game;

import com.frosty.bedgunwars.BedGunWars;
import com.frosty.bedgunwars.network.KillstreakEffectPacket;
import com.frosty.bedgunwars.network.KillstreakStatePacket;
import com.frosty.bedgunwars.network.PacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.*;

public class KillstreakManager {

    private final Map<UUID, Integer> streakKills = new HashMap<>();
    private final Map<UUID, Map<KillstreakType, Integer>> earned = new HashMap<>();
    private final Map<UUID, Integer> uavTimer  = new HashMap<>();
    private final Map<UUID, Integer> glowTimer = new HashMap<>();
    private final java.util.Set<UUID> activeJuggernauts = new java.util.HashSet<>();
    private final Map<UUID, ItemStack> airSupportDisplaced = new HashMap<>();
    private final Map<UUID, Integer> juggernautBulletsGiven = new HashMap<>();

    private static final UUID JUGG_MODIFIER = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    // Kill / Death

    public void onKill(UUID killer, MinecraftServer server, GameSession session) {
        int streak = streakKills.merge(killer, 1, Integer::sum);
        for (KillstreakType type : KillstreakType.values()) {
            if (streak == type.killsRequired) award(killer, type, server);
        }
        pushState(killer, server, session);
    }

    public void onDeath(UUID player, MinecraftServer server, GameSession session) {
        streakKills.put(player, 0);
        ServerPlayer sp = server.getPlayerList().getPlayer(player);
        if (sp != null) removeJuggernaut(sp);
        pushState(player, server, session);
    }

    private final java.util.Set<UUID> tippedPlayers = new java.util.HashSet<>();

    public void award(UUID uuid, KillstreakType type, MinecraftServer server) {
        earned.computeIfAbsent(uuid, k -> new EnumMap<>(KillstreakType.class))
                .merge(type, 1, Integer::sum);
        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
        if (sp != null) {
            sp.sendSystemMessage(Component.literal(
                    "§6[Killstreak] §e" + type.displayName + " §7ready! Press §eV §7to activate."));
            SoundHelper.playNoteClick(sp, SoundHelper.noteToPitch(22));
            if (tippedPlayers.add(uuid)) TipsManager.sendTip(sp, "10");
        }
    }

    // Activation

    public void activate(UUID uuid, KillstreakType type, MinecraftServer server, GameSession session) {
        Map<KillstreakType, Integer> q = earned.get(uuid);
        if (q == null || q.getOrDefault(type, 0) <= 0) return;
        if (q.merge(type, -1, Integer::sum) <= 0) q.remove(type);

        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
        if (sp == null) return;

        switch (type) {
            case UAV          -> activateUAV(uuid, sp, server);
            case PRIVATE_GLOW -> activateGlow(uuid, sp, server, session);
            case AIR_SUPPORT  -> activateAirSupport(uuid, sp, server);
            case JUGGERNAUT   -> activateJuggernaut(sp);
        }
        pushState(uuid, server, session);
    }

    private void activateUAV(UUID uuid, ServerPlayer sp, MinecraftServer server) {
        int cur = uavTimer.getOrDefault(uuid, 0);
        uavTimer.put(uuid, cur + (cur > 0 ? 80 : 300));
        PacketHandler.sendToClientByUUID(uuid, server,
                new KillstreakEffectPacket(KillstreakEffectPacket.Effect.UAV_START));
        sp.sendSystemMessage(Component.literal("§6[UAV] §eActive! Enemies revealed on minimap."));

        // Sound A activating player only
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                com.frosty.bedgunwars.BedGunWars.UAV_SELF.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

        // Sound B all other players in session
        for (UUID otherUuid : server.getPlayerList().getPlayers().stream()
                .map(net.minecraft.server.level.ServerPlayer::getUUID)
                .toList()) {
            if (otherUuid.equals(uuid)) continue;
            net.minecraft.server.level.ServerPlayer other =
                    server.getPlayerList().getPlayer(otherUuid);
            if (other == null) continue;
            other.level().playSound(null, other.getX(), other.getY(), other.getZ(),
                    com.frosty.bedgunwars.BedGunWars.UAV_ENEMY.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void activateGlow(UUID uuid, ServerPlayer sp, MinecraftServer server, GameSession session) {
        int cur = glowTimer.getOrDefault(uuid, 0);
        glowTimer.put(uuid, cur + (cur > 0 ? 80 : 300));
        for (UUID enemyUuid : session.getPlayers()) {
            if (enemyUuid.equals(uuid) || session.isEliminated(enemyUuid)) continue;
            String myTeam = session.getPlayerTeam(uuid);
            String theirTeam = session.getPlayerTeam(enemyUuid);
            if (myTeam != null && myTeam.equals(theirTeam)) continue;
            ServerPlayer enemy = server.getPlayerList().getPlayer(enemyUuid);
            if (enemy != null) sendFakeGlow(sp, enemy, true);
        }
        sp.sendSystemMessage(Component.literal("§6[Private Glow] §eActive! Enemies highlighted."));
    }

    private void activateAirSupport(UUID uuid, ServerPlayer sp, MinecraftServer server) {
        ItemStack gun = GunHelper.buildGun(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("daffas_arsenal", "samula3"));
        if (!gun.isEmpty()) {
            // Save what's in slot 0, force samula3 there
            ItemStack displaced = sp.getInventory().getItem(0);
            airSupportDisplaced.put(sp.getUUID(), displaced.copy());
            sp.getInventory().setItem(0, gun);
            sp.getInventory().selected = 0; // force hotbar selection to slot 0
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        }
        PacketHandler.sendToClientByUUID(uuid, server,
                new KillstreakEffectPacket(KillstreakEffectPacket.Effect.AIR_SUPPORT_OPEN));
        sp.sendSystemMessage(Component.literal("§6[Air Support] §eSelect 3 target points on the minimap."));
    }

    private void activateJuggernaut(ServerPlayer sp) {
        ItemStack minigun = GunHelper.buildGun(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tacz", "minigun"));
        if (!minigun.isEmpty()) {
            // Set ammo directly on the stack before adding
            minigun.getOrCreateTag().putInt("AmmoCount", 500);
            sp.getInventory().add(minigun);
            // Also try reload after 2 ticks as backup
            sp.getServer().tell(new net.minecraft.server.TickTask(
                    sp.getServer().getTickCount() + 2, () ->
                    GunHelper.reloadAllGuns(sp, null)));
        }
        // Resistance I (amplifier 0) for 2 minutes (2400 ticks)
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2400, 0, false, false, false));
        var attr = sp.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null && attr.getModifier(JUGG_MODIFIER) == null) {
            attr.addPermanentModifier(new AttributeModifier(
                    JUGG_MODIFIER, "juggernaut_health", 200, AttributeModifier.Operation.ADDITION));
            sp.setHealth(sp.getMaxHealth());
        }
        activeJuggernauts.add(sp.getUUID());

        // Give 350 .308 Winchester bullets, tracked for cleanup on death
        net.minecraft.nbt.CompoundTag ammoTag = new net.minecraft.nbt.CompoundTag();
        ammoTag.putString("AmmoId", "tacz:308");
        ItemStack ammoStack = new ItemStack(net.minecraft.world.item.Items.AIR); // placeholder, replaced below
        net.minecraft.world.item.Item ammoItem = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(net.minecraft.resources.ResourceLocation.tryParse("tacz:ammo"));
        if (ammoItem != null) {
            int toGive = 350;
            int stackSize = 48;
            juggernautBulletsGiven.put(sp.getUUID(), toGive);
            while (toGive > 0) {
                int count = Math.min(toGive, stackSize);
                ItemStack bullets = new ItemStack(ammoItem, count);
                bullets.setTag(ammoTag.copy());
                sp.getInventory().add(bullets);
                toGive -= count;
            }
        }
        sp.sendSystemMessage(Component.literal("§6[Juggernaut] §eActivated."));
        String juggName = sp.getName().getString();
        for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
            if (!p.getUUID().equals(sp.getUUID())) {
                p.sendSystemMessage(Component.literal("§6[Juggernaut] §f" + juggName + " §ehas acquired Juggernaut!"));
            }
        }
    }

    private void removeJuggernaut(ServerPlayer sp) {
        activeJuggernauts.remove(sp.getUUID());
        sp.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        var attr = sp.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.removePermanentModifier(JUGG_MODIFIER);
            if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
        }
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                var id = iGun.getGunId(s);
                if (id != null && id.getPath().equals("minigun")) {
                    sp.getInventory().setItem(i, ItemStack.EMPTY);
                    break;
                }
            }
        }

        // Remove up to the tracked number of .308 bullets given on juggernaut activation
        int toRemove = juggernautBulletsGiven.getOrDefault(sp.getUUID(), 0);
        juggernautBulletsGiven.remove(sp.getUUID());
        if (toRemove > 0) {
            for (int i = 0; i < sp.getInventory().getContainerSize() && toRemove > 0; i++) {
                ItemStack s = sp.getInventory().getItem(i);
                if (s.isEmpty()) continue;
                net.minecraft.resources.ResourceLocation itemId =
                        net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
                if (itemId == null || !itemId.toString().equals("tacz:ammo")) continue;
                net.minecraft.nbt.CompoundTag tag = s.getTag();
                if (tag == null || !tag.getString("AmmoId").equals("tacz:308")) continue;
                int remove = Math.min(s.getCount(), toRemove);
                s.shrink(remove);
                toRemove -= remove;
                if (s.isEmpty()) sp.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    // Fake glow packet

    private void sendFakeGlow(ServerPlayer receiver, ServerPlayer target, boolean glow) {
        try {
            var accessor = net.minecraft.network.syncher.EntityDataSerializers.BYTE.createAccessor(0);
            byte flags = target.getEntityData().get(accessor);
            byte updated = glow ? (byte)(flags | 0x40) : (byte)(flags & ~0x40);
            var pkt = new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                    target.getId(),
                    List.of(new net.minecraft.network.syncher.SynchedEntityData.DataValue<>(
                            accessor.getId(),
                            net.minecraft.network.syncher.EntityDataSerializers.BYTE,
                            updated)));
            receiver.connection.send(pkt);
        } catch (Exception ignored) {}
    }

    // Tick

    public void tick(MinecraftServer server, GameSession session) {
        tickExplosions();
        uavTimer.entrySet().removeIf(e -> {
            UUID uuid = e.getKey();
            int ticks = e.getValue() - 1;
            if (ticks <= 0) {
                PacketHandler.sendToClientByUUID(uuid, server,
                        new KillstreakEffectPacket(KillstreakEffectPacket.Effect.UAV_END));
                pushState(uuid, server, session);
                return true;
            }
            e.setValue(ticks);
            pushState(uuid, server, session);
            return false;
        });

        glowTimer.entrySet().removeIf(e -> {
            UUID uuid = e.getKey();
            int ticks = e.getValue() - 1;
            if (ticks <= 0) {
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp != null) {
                    for (UUID enemyUuid : session.getPlayers()) {
                        ServerPlayer enemy = server.getPlayerList().getPlayer(enemyUuid);
                        if (enemy != null && !enemyUuid.equals(uuid)) sendFakeGlow(sp, enemy, false);
                    }
                }
                pushState(uuid, server, session);
                return true;
            }
            e.setValue(ticks);
            if (ticks % 20 == 0) pushState(uuid, server, session);
            return false;
        });
    }

    // Pending explosions (tick-counter based)

    private static class PendingExplosion {
        final ServerLevel level;
        final double px, pz;
        int ticksRemaining;
        final UUID callerUuid;
        final String callerTeam;
        PendingExplosion(ServerLevel lvl, double px, double pz, int delay, UUID callerUuid, String callerTeam) {
            this.level = lvl; this.px = px; this.pz = pz; this.ticksRemaining = delay;
            this.callerUuid = callerUuid; this.callerTeam = callerTeam;
        }
    }
    private final List<PendingExplosion> pendingExplosions = new ArrayList<>();

    // Air Support explosions



    public void fireAirSupport(UUID uuid, List<double[]> points, MinecraftServer server, GameSession session) {
        if (points.isEmpty()) {
            // Cancelled refund and restore
            earned.computeIfAbsent(uuid, k -> new java.util.EnumMap<>(KillstreakType.class))
                    .merge(KillstreakType.AIR_SUPPORT, 1, Integer::sum);
            ServerPlayer cancelled = server.getPlayerList().getPlayer(uuid);
            ItemStack displaced = airSupportDisplaced.remove(uuid);
            if (cancelled != null && displaced != null && !displaced.isEmpty()) {
                cancelled.getInventory().setItem(0, displaced);
            }
            pushState(uuid, server, session);
            return;
        }

        // Confirmed remove samula3, restore displaced, play sound
        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
        if (sp != null) {
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                ItemStack s = sp.getInventory().getItem(i);
                if (s.isEmpty()) continue;
                if (s.getItem() instanceof com.tacz.guns.api.item.IGun iGun) {
                    var id = iGun.getGunId(s);
                    if (id != null && id.toString().equals("daffas_arsenal:samula3")) {
                        ItemStack restore = airSupportDisplaced.remove(uuid);
                        sp.getInventory().setItem(i, restore != null ? restore : ItemStack.EMPTY);
                        break;
                    }
                }
            }
            sp.level().playSound(null,
                    sp.blockPosition().getX(), sp.blockPosition().getY(), sp.blockPosition().getZ(),
                    com.frosty.bedgunwars.BedGunWars.AIRSTRIKE_SOUND.get(),
                    net.minecraft.sounds.SoundSource.MASTER, 2.0f, 1.0f);
        }

        ServerLevel level = session.getLevel();
        int tickDelay = 60; // initial delay
        int pointGap = 9;   // gap between points
        int waveGap = 40;   // gap between waves

        // I fucking hate math, why the fuck the jet is flying backwards
        // random jet flying angle VVV
        double angle = level.getRandom().nextDouble() * 2 * Math.PI;
        double dx = Math.sin(angle);
        double dz = Math.cos(angle);
        float speed = com.frosty.bedgunwars.entity.JetEntity.SPEED;

        // Perpendicular axis for lateral offset
        double perpX = dz;
        double perpZ = -dx;

        // Lateral offsets so jets don't fly in the same line
        double[] lateralOffsets = {-12.0, 0.0, 12.0};

        // Average ground height for fly altitude
        double avgGroundY = 0;
        for (double[] point : points) {
            avgGroundY += level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    (int) point[0], (int) point[1]);
        }
        avgGroundY = avgGroundY / points.size() + 80;

        for (int wave = 0; wave < 2; wave++) {
            for (int p = 0; p < points.size(); p++) {
                final double px = points.get(p)[0], pz = points.get(p)[1];
                final double lateral = lateralOffsets[p % lateralOffsets.length];
                final double jetSpawnX = px - dx * 200 + perpX * lateral;
                final double jetSpawnY = avgGroundY;
                final double jetSpawnZ = pz - dz * 200 + perpZ * lateral;
                final net.minecraft.world.phys.Vec3 vel =
                        new net.minecraft.world.phys.Vec3(dx * speed, 0, dz * speed);
                final int jetDelay = Math.max(1, tickDelay - 40); // spawn jet delay

                spawnJetDelayed(level, jetSpawnX, jetSpawnY, jetSpawnZ, vel, jetDelay);
                pendingExplosions.add(new PendingExplosion(level, px, pz, tickDelay, uuid,
                        session.getPlayerTeam(uuid)));
                tickDelay += pointGap;
            }
            tickDelay += waveGap;
        }
        // Sync decremented killstreak state to client
        pushState(uuid, server, session);
    }

    private void spawnJetDelayed(ServerLevel level, double x, double y, double z,
                                 net.minecraft.world.phys.Vec3 vel, int delay) {
        float speed = com.frosty.bedgunwars.entity.JetEntity.SPEED;
        PacketHandler.sendToAllClients(level.getServer(),
                new com.frosty.bedgunwars.network.SpawnJetPacket(
                        x, y, z, vel.x / speed, vel.z / speed, speed, delay));
    }

    private void spawnJet(ServerLevel level, double x, double y, double z,
                          net.minecraft.world.phys.Vec3 vel) {
        spawnJetDelayed(level, x, y, z, vel, 1);
    }    private void tickExplosions() {
        pendingExplosions.removeIf(e -> {
            e.ticksRemaining--;
            if (e.ticksRemaining > 0) return false;
            final double fpx = e.px + (Math.random() * 4 - 2);
            final double fpz = e.pz + (Math.random() * 4 - 2);
            final double fpy = e.level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    (int) fpx, (int) fpz);
            e.level.explode(null, fpx, fpy, fpz, 4.0f, false,
                    net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(
                    fpx - 7, fpy - 4, fpz - 7, fpx + 7, fpy + 4, fpz + 7);
            GameSession session = GameManager.getSession();
            for (net.minecraft.world.entity.LivingEntity ent :
                    e.level.getEntitiesOfClass(
                            net.minecraft.world.entity.LivingEntity.class, aabb)) {
                // skip teammates (but not the caller themselves)
                if (session != null && ent instanceof ServerPlayer target
                        && e.callerTeam != null
                        && !target.getUUID().equals(e.callerUuid)) {
                    String targetTeam = session.getPlayerTeam(target.getUUID());
                    if (e.callerTeam.equals(targetTeam)) continue;
                }
                double dist = Math.sqrt(
                        Math.pow(ent.getX() - fpx, 2) +
                                Math.pow(ent.getY() - fpy, 2) +
                                Math.pow(ent.getZ() - fpz, 2));
                if (dist <= 7.0) {
                    float dmg = (float)(60.0 * (1.0 - dist / 7.0));
                    if (dmg > 0) ent.hurt(e.level.damageSources().generic(), dmg);
                }
            }
            return true;
        });
    }

    // State push

    public void pushState(UUID uuid, MinecraftServer server, GameSession session) {
        Map<KillstreakType, Integer> q = earned.getOrDefault(uuid, Collections.emptyMap());
        PacketHandler.sendToClientByUUID(uuid, server, new KillstreakStatePacket(
                streakKills.getOrDefault(uuid, 0),
                q.getOrDefault(KillstreakType.UAV, 0),
                q.getOrDefault(KillstreakType.PRIVATE_GLOW, 0),
                q.getOrDefault(KillstreakType.AIR_SUPPORT, 0),
                q.getOrDefault(KillstreakType.JUGGERNAUT, 0),
                uavTimer.getOrDefault(uuid, 0),
                glowTimer.getOrDefault(uuid, 0)));
    }

    public void pushAll(MinecraftServer server, GameSession session) {
        for (UUID uuid : session.getPlayers()) pushState(uuid, server, session);
    }

    // Cleanup

    public void reset(MinecraftServer server) {
        for (UUID uuid : new HashSet<>(activeJuggernauts)) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) removeJuggernaut(sp);
        }
        streakKills.clear(); earned.clear(); uavTimer.clear(); glowTimer.clear();
        activeJuggernauts.clear(); pendingExplosions.clear();
        airSupportDisplaced.clear(); juggernautBulletsGiven.clear();
    }

    public boolean isUAVActive(UUID uuid)  { return uavTimer.getOrDefault(uuid, 0) > 0; }
    public int getStreak(UUID uuid)        { return streakKills.getOrDefault(uuid, 0); }
    public Map<KillstreakType, Integer> getEarned(UUID uuid) {
        return earned.getOrDefault(uuid, Collections.emptyMap());
    }
}