package com.example.dbdcore.killer.jd;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.game.role.PlayerRole;
import com.example.dbdcore.game.movement.StunManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Орбитальный бросок лопаты JD: траектория, попадания в блоки и сурвов.
 * Без отдельного Entity, всё считается на сервере с частицами.
 */
public class JdShovelProjectileManager {

    private static final class Projectile {
        UUID killerId;
        Vec3 pos;
        Vec3 vel;
        int lifeTicks;
    }

    private final List<Projectile> projectiles = new ArrayList<>();

    public void clear() {
        projectiles.clear();
    }

    public void spawn(ServerPlayer killer) {
        Projectile p = new Projectile();
        p.killerId = killer.getUUID();
        Vec3 eye = killer.getEyePosition();
        Vec3 look = killer.getLookAngle().normalize();

        // Базовая скорость.
        double baseSpeed = 0.7D;
        double pitch = Math.toRadians(killer.getXRot());
        // Чем выше смотрим, тем дальше бросок.
        double pitchFactor = Math.max(0.0D, Math.min(1.0D, (-pitch) / (Math.PI / 2)));
        double speed = baseSpeed + pitchFactor * 0.7D; // примерно 8–20 блоков.

        // Добавляем небольшую вертикальную составляющую.
        Vec3 vel = new Vec3(look.x * speed, look.y * speed + 0.3D, look.z * speed);

        p.pos = eye.add(look.scale(0.5D));
        p.vel = vel;
        p.lifeTicks = 40; // максимум ~2 секунды полёта.
        projectiles.add(p);
    }

    public void tick(ServerLevel level) {
        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            ServerPlayer killer = level.getServer().getPlayerList().getPlayer(p.killerId);
            if (killer == null) {
                it.remove();
                continue;
            }

            p.lifeTicks--;
            if (p.lifeTicks <= 0) {
                // Промах по времени — считаем промахом.
                GameSystems.stuns().applyStun(killer, StunManager.StunSource.MISS);
                it.remove();
                continue;
            }

            // Один шаг движения с гравитацией.
            p.pos = p.pos.add(p.vel);
            p.vel = p.vel.add(0.0D, -0.05D, 0.0D);

            // Частицы траектории (примерная дуга).
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    p.pos.x, p.pos.y, p.pos.z,
                    1, 0.01D, 0.01D, 0.01D, 0.0D);

            BlockPos bp = new BlockPos((int) Math.floor(p.pos.x), (int) Math.floor(p.pos.y), (int) Math.floor(p.pos.z));
            if (!level.getBlockState(bp).isAir() && !level.getBlockState(bp).is(Blocks.WATER)) {
                // Попадание в блок: лопата "застревает".
                GameSystems.stuns().applyStun(killer, StunManager.StunSource.MISS);
                it.remove();
                continue;
            }

            // Проверка попадания в сурва.
            for (ServerPlayer target : level.players()) {
                if (target.getUUID().equals(p.killerId)) {
                    continue;
                }
                if (GameSystems.roles().getRole(target) != PlayerRole.SURVIVOR) {
                    continue;
                }
                double distSq = target.position().distanceToSqr(p.pos);
                if (distSq <= 0.8D * 0.8D) {
                    // Попадание в сурва: задаём состояние ранен и помечаем лопату в спине.
                    GameSystems.health().setState(target, com.example.dbdcore.game.health.HealthState.INJURED);
                    GameSystems.jdShovel().markShovelInSurvivor(target);
                    // Частицы крови.
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                            target.getX(), target.getY() + 1.0D, target.getZ(),
                            8, 0.2D, 0.2D, 0.2D, 0.1D);
                    it.remove();
                    break;
                }
            }
        }
    }
}

