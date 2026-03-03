package com.example.dbdcore.killer.joe;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.config.DbdCoreConfig;
import com.example.dbdcore.game.role.PlayerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Падение 8 зелий в выбранную область. Попадание: жетон + замедление сурву; промах — замедление Джо.
 */
public class JoePotionManager {

    private static final class PendingImpact {
        final UUID joeId;
        final double x, y, z;
        int ticksRemaining;
        final double radius;
        final long throwId;

        PendingImpact(UUID joeId, double x, double y, double z, int ticksRemaining, double radius, long throwId) {
            this.joeId = joeId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ticksRemaining = ticksRemaining;
            this.radius = radius;
            this.throwId = throwId;
        }
    }

    private long nextThrowId;

    private final List<PendingImpact> impacts = new ArrayList<>();

    public void clear() {
        impacts.clear();
    }

    /**
     * Запускает 8 падений зелий в точку (блок под прицелом). Небольшой разброс по площади.
     */
    public void scheduleImpacts(ServerLevel level, ServerPlayer joe, BlockPos targetBlock) {
        long throwId = ++nextThrowId;
        UUID joeId = joe.getUUID();
        double radius = DbdCoreConfig.COMMON.joePotionRadius.get();
        int delayTicks = 25;
        for (int i = 0; i < 8; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 2;
            double oz = (level.random.nextDouble() - 0.5) * 2;
            PendingImpact p = new PendingImpact(
                    joeId,
                    targetBlock.getX() + 0.5 + ox,
                    targetBlock.getY() + 1.0,
                    targetBlock.getZ() + 0.5 + oz,
                    delayTicks + i * 3,
                    radius,
                    throwId
            );
            impacts.add(p);
        }
    }

    public void tick(ServerLevel level) {
        Map<Long, Boolean> throwAnyHit = new HashMap<>();
        Iterator<PendingImpact> it = impacts.iterator();
        while (it.hasNext()) {
            PendingImpact p = it.next();
            p.ticksRemaining--;
            if (p.ticksRemaining > 0) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.5, p.z, 2, 0.2, 0.2, 0.2, 0.01);
                continue;
            }
            it.remove();
            ServerPlayer joe = level.getServer().getPlayerList().getPlayer(p.joeId);
            boolean hit = joe != null && applyImpact(level, p, joe);
            throwAnyHit.merge(p.throwId, hit, Boolean::logicalOr);
            long tid = p.throwId;
            boolean isLastOfThrow = impacts.stream().noneMatch(x -> x.throwId == tid);
            if (isLastOfThrow && joe != null) {
                GameSystems.joeState().onPotionLanded(joe, throwAnyHit.getOrDefault(tid, false));
            }
        }
    }

    private boolean applyImpact(ServerLevel level, PendingImpact p, ServerPlayer joe) {
        double radius = p.radius;
        double radiusSq = radius * radius;
        boolean anyHit = false;
        int slowTicks = DbdCoreConfig.COMMON.joeTokenSlowDurationTicks.get();
        boolean worldBreaker = GameSystems.joeState().isWorldBreakerActive(joe);

        for (ServerPlayer target : level.players()) {
            if (target.getUUID().equals(p.joeId)) {
                continue;
            }
            if (GameSystems.roles().getRole(target) != PlayerRole.SURVIVOR) {
                continue;
            }
            double dx = target.getX() - p.x;
            double dy = target.getY() - p.y;
            double dz = target.getZ() - p.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) {
                continue;
            }
            anyHit = true;
            if (worldBreaker) {
                GameSystems.health().applyHit(target);
            } else {
                boolean reachedMax = GameSystems.joeTokens().addToken(target);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 0, false, false, true));
                if (reachedMax) {
                    GameSystems.joeState().triggerWorldBreaker(joe);
                }
            }
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + 1, target.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
        }

        level.playSound(null, p.x, p.y, p.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        return anyHit;
    }

    /**
     * Выход из Изнанки: АОЕ в позиции Джо — всем сурвам в радиусе 2 жетона + замедление.
     */
    public void applyExitAoe(ServerLevel level, ServerPlayer joe) {
        double x = joe.getX();
        double y = joe.getY();
        double z = joe.getZ();
        double radius = DbdCoreConfig.COMMON.joeAoeExitRadius.get();
        double radiusSq = radius * radius;
        int slowTicks = DbdCoreConfig.COMMON.joeTokenSlowDurationTicks.get() * 2;

        for (ServerPlayer target : level.players()) {
            if (target.getUUID().equals(joe.getUUID())) {
                continue;
            }
            if (GameSystems.roles().getRole(target) != PlayerRole.SURVIVOR) {
                continue;
            }
            double dx = target.getX() - x;
            double dy = target.getY() - y;
            double dz = target.getZ() - z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) {
                continue;
            }
            GameSystems.joeTokens().setTokens(target, 2);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 1, false, false, true));
        }
        GameSystems.joeState().triggerWorldBreaker(joe);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 0.8F);
        for (int i = 0; i < 30; i++) {
            level.sendParticles(ParticleTypes.PORTAL, x, y + 1, z, 1, 0.5, 0.5, 0.5, 0.1);
        }
    }
}
