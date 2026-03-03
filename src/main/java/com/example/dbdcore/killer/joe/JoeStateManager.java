package com.example.dbdcore.killer.joe;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.character.CharacterId;
import com.example.dbdcore.config.DbdCoreConfig;
import com.example.dbdcore.game.role.PlayerRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Состояние Джо: зелье (КД, барьер), Изнанка, фаза Разрушитель миров, блокировка зелья после выхода.
 */
public class JoeStateManager {

    public static final String JOE_POTION_TAG = "dbdcore_joe_potion";
    public static final String JOE_BARRIER_TAG = "dbdcore_joe_barrier";
    public static final String JOE_VOID_SHARD_TAG = "dbdcore_joe_void_shard";
    public static final String JOE_POTION_NAME = "Коктель Джо";
    public static final String JOE_VOID_SHARD_NAME = "Осколок Изнанки";

    private static final class State {
        int potionCooldownTicks;
        boolean potionInWorld;
        boolean inVoid;
        Vec3 voidHolePos;
        int worldBreakerTicksLeft;
        int cannotUsePotionTicks;
    }

    private final Map<UUID, State> states = new HashMap<>();

    public void clear() {
        states.clear();
    }

    private State state(UUID id) {
        return states.computeIfAbsent(id, k -> new State());
    }

    public static boolean isJoe(ServerPlayer player) {
        var loadout = GameSystems.loadouts().getLoadout(player);
        return loadout != null && loadout.characterId == CharacterId.JOE;
    }

    public boolean canUsePotion(ServerPlayer joe) {
        if (!isJoe(joe)) {
            return false;
        }
        State s = state(joe.getUUID());
        return s.potionCooldownTicks <= 0 && s.cannotUsePotionTicks <= 0 && !s.inVoid;
    }

    public int getPotionCooldownTicks(ServerPlayer joe) {
        return state(joe.getUUID()).potionCooldownTicks;
    }

    public boolean isInVoid(ServerPlayer joe) {
        return state(joe.getUUID()).inVoid;
    }

    public boolean isWorldBreakerActive(ServerPlayer joe) {
        return state(joe.getUUID()).worldBreakerTicksLeft > 0;
    }

    /**
     * Направление взгляда — блок в пределах maxDistance (сквозь стены). Макс. дистанция из конфига.
     */
    public BlockHitResult getTargetBlock(ServerPlayer joe, ServerLevel level) {
        int maxDist = DbdCoreConfig.COMMON.joePotionMaxDistance.get();
        HitResult hit = joe.pick(maxDist, 0, false); // includeEntities = false, только блоки
        if (hit.getType() == HitResult.Type.BLOCK) {
            return (BlockHitResult) hit;
        }
        // Блок под ногами или вдаль по направлению взгляда
        Vec3 eye = joe.getEyePosition(0);
        Vec3 look = joe.getViewVector(0);
        Vec3 end = eye.add(look.scale(maxDist));
        return level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, joe));
    }

    public void onPotionThrown(ServerPlayer joe) {
        State s = state(joe.getUUID());
        s.potionCooldownTicks = DbdCoreConfig.COMMON.joePotionCooldownTicks.get();
        s.potionInWorld = true;
        ensureBarrierInHand(joe, s);
    }

    public void onPotionLanded(ServerPlayer joe, boolean anyHit) {
        State s = state(joe.getUUID());
        s.potionInWorld = false;
        int stunTicks = anyHit
                ? DbdCoreConfig.COMMON.joeHitStunTicks.get()
                : DbdCoreConfig.COMMON.joeMissStunTicks.get();
        if (stunTicks > 0) {
            joe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stunTicks, 1, false, false, true));
        }
        ensurePotionOrBarrierInHand(joe);
    }

    public void enterVoid(ServerPlayer joe, ServerLevel level) {
        State s = state(joe.getUUID());
        if (s.inVoid) {
            return;
        }
        s.inVoid = true;
        s.voidHolePos = joe.position();
        joe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 9999, 1, false, false, true));
        joe.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 9999, 0, false, false, true));
    }

    public void exitVoid(ServerPlayer joe, ServerLevel level) {
        State s = state(joe.getUUID());
        if (!s.inVoid) {
            return;
        }
        s.inVoid = false;
        s.voidHolePos = null;
        joe.removeEffect(MobEffects.MOVEMENT_SPEED);
        joe.removeEffect(MobEffects.NIGHT_VISION);
        s.cannotUsePotionTicks = DbdCoreConfig.COMMON.joeCannotUsePotionAfterExitTicks.get();
        GameSystems.joePotions().applyExitAoe(level, joe);
        // Нерф: Джо «встаёт на месте» на 5 сек — сурв может отбежать (радиус AOE тот же).
        int rootTicks = DbdCoreConfig.COMMON.joeExitRootDurationTicks.get();
        if (rootTicks > 0) {
            joe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, rootTicks, 255, false, false, true));
        }
    }

    public void triggerWorldBreaker(ServerPlayer joe) {
        State s = state(joe.getUUID());
        s.worldBreakerTicksLeft = DbdCoreConfig.COMMON.joeWorldBreakerDurationTicks.get();
        if (joe.level() instanceof ServerLevel level) {
            level.playSound(null, joe.getX(), joe.getY(), joe.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
    }

    public void tick(ServerLevel level, ServerPlayer joe) {
        if (!isJoe(joe)) {
            return;
        }
        State s = state(joe.getUUID());
        if (s.potionCooldownTicks > 0) {
            s.potionCooldownTicks--;
        }
        if (s.cannotUsePotionTicks > 0) {
            s.cannotUsePotionTicks--;
        }
        if (s.worldBreakerTicksLeft > 0) {
            s.worldBreakerTicksLeft--;
            if (level.getGameTime() % 5 == 0) {
                Vec3 pos = joe.getEyePosition(0);
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 4, 0.2, 0.2, 0.2, 0.02);
                level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 2, 0.15, 0.15, 0.15, 0.01);
            }
        }
        ensurePotionOrBarrierInHand(joe);
        ensureVoidShardInOffhand(joe);
        tickVoidTerror(level, joe);
    }

    private void tickVoidTerror(ServerLevel level, ServerPlayer joe) {
        State s = state(joe.getUUID());
        if (!s.inVoid) {
            return;
        }
        double radius = DbdCoreConfig.COMMON.joeVoidTerrorRadius.get();
        double radiusSq = radius * radius;
        for (ServerPlayer p : level.players()) {
            if (GameSystems.roles().getRole(p) != PlayerRole.SURVIVOR) {
                continue;
            }
            double dx = p.getX() - joe.getX();
            double dy = p.getY() - joe.getY();
            double dz = p.getZ() - joe.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSq && level.getGameTime() % 40 == 0) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, 0.5F, 0.8F);
            }
        }
    }

    private void ensurePotionOrBarrierInHand(ServerPlayer joe) {
        State s = state(joe.getUUID());
        if (s.potionCooldownTicks > 0) {
            ensureBarrierInHand(joe, s);
        } else {
            ensurePotionInHand(joe);
        }
    }

    private void ensurePotionInHand(ServerPlayer joe) {
        ItemStack main = joe.getMainHandItem();
        if (isJoePotion(main)) {
            return;
        }
        removeDuplicates(joe, true);
        ItemStack potion = new ItemStack(Items.SPLASH_POTION);
        potion.setHoverName(net.minecraft.network.chat.Component.literal(JOE_POTION_NAME));
        potion.getOrCreateTag().putBoolean(JOE_POTION_TAG, true);
        joe.setItemInHand(InteractionHand.MAIN_HAND, potion);
    }

    private void ensureBarrierInHand(ServerPlayer joe, State s) {
        ItemStack main = joe.getMainHandItem();
        if (!isJoeBarrier(main)) {
            removeDuplicates(joe, true);
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.getOrCreateTag().putBoolean(JOE_BARRIER_TAG, true);
            joe.setItemInHand(InteractionHand.MAIN_HAND, barrier);
        }
        main = joe.getMainHandItem();
        if (isJoeBarrier(main)) {
            int sec = Math.max(0, s.potionCooldownTicks / 20);
            main.setHoverName(net.minecraft.network.chat.Component.literal("КД: " + sec));
        }
    }

    private void removeDuplicates(ServerPlayer joe, boolean keepMain) {
        for (int i = 0; i < joe.getInventory().getContainerSize(); i++) {
            ItemStack stack = joe.getInventory().getItem(i);
            if (isJoePotion(stack) || isJoeBarrier(stack)) {
                if (keepMain && i == joe.getInventory().selected) {
                    continue;
                }
                joe.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    public static boolean isJoePotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.hasTag() && stack.getTag().getBoolean(JOE_POTION_TAG);
    }

    public static boolean isJoeBarrier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() == Items.BARRIER && stack.hasTag() && stack.getTag().getBoolean(JOE_BARRIER_TAG);
    }

    public static boolean isJoeVoidShard(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.hasTag() && stack.getTag().getBoolean(JOE_VOID_SHARD_TAG);
    }

    public static void ensureVoidShardInOffhand(ServerPlayer joe) {
        if (!isJoe(joe)) {
            return;
        }
        ItemStack off = joe.getOffhandItem();
        if (isJoeVoidShard(off)) {
            return;
        }
        ItemStack shard = new ItemStack(Items.NETHER_STAR);
        shard.setHoverName(net.minecraft.network.chat.Component.literal(JOE_VOID_SHARD_NAME));
        shard.getOrCreateTag().putBoolean(JOE_VOID_SHARD_TAG, true);
        joe.setItemInHand(InteractionHand.OFF_HAND, shard);
    }

    /** Вызвать при ПКМ барьером, когда КД зелья истёк — вернуть зелье в руку. */
    public void onBarrierReturn(ServerPlayer joe) {
        ensurePotionInHand(joe);
    }
}
