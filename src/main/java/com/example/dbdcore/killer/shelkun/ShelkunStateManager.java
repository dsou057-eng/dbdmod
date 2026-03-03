package com.example.dbdcore.killer.shelkun;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.character.CharacterId;
import com.example.dbdcore.config.DbdCoreConfig;
import com.example.dbdcore.game.health.HealthState;
import com.example.dbdcore.game.match.GameState;
import com.example.dbdcore.game.role.PlayerRole;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Шелкунчик: движение/стояние, слепота/скорость/замедление, невидимость сурвов при стоянии обоих,
 * ваншот «давление» при столкновении в окне после начала движения (КД 3 сек).
 */
public class ShelkunStateManager {

    private static final double TELEPORT_IGNORE_THRESHOLD_SQ = 4.0D; // не менять состояние при скачке >2 блоков

    private static final class State {
        double lastX = Double.NaN;
        double lastZ = Double.NaN;
        boolean wasMoving;
        int pressureWindowTicks;
        int pressureCooldownTicks;
    }

    private final Map<UUID, State> states = new HashMap<>();

    public void clear() {
        states.clear();
    }

    private State state(UUID id) {
        return states.computeIfAbsent(id, k -> new State());
    }

    public static boolean isShelkun(ServerPlayer player) {
        var loadout = GameSystems.loadouts().getLoadout(player);
        return loadout != null && loadout.characterId == CharacterId.SHELKUN;
    }

    public boolean isMoving(ServerPlayer shelkun) {
        State s = state(shelkun.getUUID());
        return s.wasMoving;
    }

    /** Ваншот доступен (в окне и не на перезарядке). */
    public boolean isPressureAvailable(ServerPlayer shelkun) {
        State s = state(shelkun.getUUID());
        return s.pressureWindowTicks > 0 && s.pressureCooldownTicks <= 0;
    }

    public int getPressureCooldownTicks(ServerPlayer shelkun) {
        return state(shelkun.getUUID()).pressureCooldownTicks;
    }

    public void tick(ServerLevel level, ServerPlayer player) {
        if (!isShelkun(player)) {
            return;
        }
        State s = state(player.getUUID());
        double x = player.getX();
        double z = player.getZ();
        double dx = x - s.lastX;
        double dz = z - s.lastZ;
        double distSq = dx * dx + dz * dz;

        double threshold = DbdCoreConfig.COMMON.shelkunMovementThreshold.get();
        boolean isMoving = Math.abs(dx) > threshold || Math.abs(dz) > threshold;

        // Первый тик: только запомнить позицию и наложить эффекты по текущему движению
        if (!Double.isFinite(s.lastX)) {
            s.lastX = x;
            s.lastZ = z;
            s.wasMoving = isMoving;
            if (isMoving) {
                applyMovingEffects(player);
                removeStandingEffects(player);
                removeInvisibilityFromAllSurvivors(level);
                s.pressureWindowTicks = DbdCoreConfig.COMMON.shelkunPressureWindowTicks.get();
            } else {
                applyStandingEffects(player);
                removeMovingEffects(player);
            }
            updateBossbarForShelkun(level, player);
            return;
        }

        // Игнорировать телепорты: при большом скачке синхронизировать позицию и эффекты
        if (distSq > TELEPORT_IGNORE_THRESHOLD_SQ) {
            s.lastX = x;
            s.lastZ = z;
            s.wasMoving = isMoving;
            if (isMoving) {
                applyMovingEffects(player);
                removeStandingEffects(player);
                removeInvisibilityFromAllSurvivors(level);
                s.pressureWindowTicks = DbdCoreConfig.COMMON.shelkunPressureWindowTicks.get();
            } else {
                applyStandingEffects(player);
                removeMovingEffects(player);
            }
            return;
        }

        // Смена состояния: только при переходе standing -> moving или moving -> standing
        if (isMoving != s.wasMoving) {
            if (isMoving) {
                applyMovingEffects(player);
                removeStandingEffects(player);
                removeInvisibilityFromAllSurvivors(level);
                s.pressureWindowTicks = DbdCoreConfig.COMMON.shelkunPressureWindowTicks.get();
                playFootstepSound(level, player);
            } else {
                applyStandingEffects(player);
                removeMovingEffects(player);
            }
            s.wasMoving = isMoving;
        } else {
            if (isMoving) {
                reapplyMovingEffectsOncePerSecond(player);
                if (level.getGameTime() % 5 == 0) {
                    playFootstepSound(level, player);
                }
            } else {
                reapplyStandingEffectsOncePerSecond(player);
                applyInvisibilityToStandingSurvivors(level, player);
            }
        }

        if (s.pressureWindowTicks > 0) {
            s.pressureWindowTicks--;
        }
        if (s.pressureCooldownTicks > 0) {
            s.pressureCooldownTicks--;
        }

        if (s.wasMoving && s.pressureWindowTicks > 0 && s.pressureCooldownTicks <= 0) {
            tryPressureOnContact(level, player, s);
        }

        s.lastX = x;
        s.lastZ = z;

        updateBossbarForShelkun(level, player);
    }

    private void tryPressureOnContact(ServerLevel level, ServerPlayer shelkun, State s) {
        AABB shelkunBox = shelkun.getBoundingBox();
        for (ServerPlayer target : level.players()) {
            if (target.getUUID().equals(shelkun.getUUID())) {
                continue;
            }
            if (GameSystems.roles().getRole(target) != PlayerRole.SURVIVOR) {
                continue;
            }
            if (GameSystems.health().getState(target) == HealthState.DEAD || GameSystems.health().getState(target) == HealthState.DOWNED) {
                continue;
            }
            if (!shelkunBox.intersects(target.getBoundingBox())) {
                continue;
            }
            GameSystems.health().setState(target, HealthState.DEAD);
            s.pressureCooldownTicks = DbdCoreConfig.COMMON.shelkunPressureCooldownTicks.get();
            level.playSound(null, shelkun.getX(), shelkun.getY(), shelkun.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5F, 0.6F);
            break;
        }
    }

    private void applyMovingEffects(ServerPlayer shelkun) {
        shelkun.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 9999, 1, false, false, true));
        shelkun.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 9999, 1, false, false, true));
        shelkun.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    private void removeMovingEffects(ServerPlayer shelkun) {
        shelkun.removeEffect(MobEffects.BLINDNESS);
        shelkun.removeEffect(MobEffects.MOVEMENT_SPEED);
    }

    private void applyStandingEffects(ServerPlayer shelkun) {
        shelkun.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 9999, 2, false, false, true));
        shelkun.removeEffect(MobEffects.MOVEMENT_SPEED);
        shelkun.removeEffect(MobEffects.BLINDNESS);
    }

    private void removeStandingEffects(ServerPlayer shelkun) {
        shelkun.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    private void reapplyMovingEffectsOncePerSecond(ServerPlayer shelkun) {
        if (shelkun.tickCount % 20 != 0) {
            return;
        }
        shelkun.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 1, false, false, true));
        shelkun.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, true));
    }

    private void reapplyStandingEffectsOncePerSecond(ServerPlayer shelkun) {
        if (shelkun.tickCount % 20 != 0) {
            return;
        }
        shelkun.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false, true));
    }

    private void removeInvisibilityFromAllSurvivors(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (GameSystems.roles().getRole(p) == PlayerRole.SURVIVOR) {
                p.removeEffect(MobEffects.INVISIBILITY);
            }
        }
    }

    private static boolean isSurvivorStanding(ServerPlayer survivor) {
        double dx = survivor.getX() - survivor.xo;
        double dz = survivor.getZ() - survivor.zo;
        double th = 0.01D;
        return Math.abs(dx) <= th && Math.abs(dz) <= th;
    }

    private void applyInvisibilityToStandingSurvivors(ServerLevel level, ServerPlayer shelkun) {
        for (ServerPlayer p : level.players()) {
            if (GameSystems.roles().getRole(p) != PlayerRole.SURVIVOR) {
                continue;
            }
            if (!isSurvivorStanding(p)) {
                p.removeEffect(MobEffects.INVISIBILITY);
                continue;
            }
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, true));
            if (level.getGameTime() % 8 == 0) {
                level.sendParticles(ParticleTypes.INSTANT_EFFECT, p.getX(), p.getY() + 1, p.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    private void playFootstepSound(ServerLevel level, ServerPlayer shelkun) {
        level.playSound(null, shelkun.getBlockX(), shelkun.getBlockY(), shelkun.getBlockZ(), SoundEvents.GRASS_STEP, SoundSource.HOSTILE, 0.4F, 0.8F);
    }

    private void updateBossbarForShelkun(ServerLevel level, ServerPlayer shelkun) {
        if (GameSystems.match().getGameState() != GameState.ACTIVE && GameSystems.match().getGameState() != GameState.ENDGAME) {
            return;
        }
        State s = state(shelkun.getUUID());
        String moveText = s.wasMoving ? "Движется" : "Стоит";
        String pressureText = s.pressureCooldownTicks > 0
                ? ("КД " + ((s.pressureCooldownTicks + 19) / 20) + " сек")
                : (s.pressureWindowTicks > 0 ? "Ваншот готов" : "—");
        GameSystems.bossbar().setShelkunState(moveText + " | " + pressureText);
    }
}
