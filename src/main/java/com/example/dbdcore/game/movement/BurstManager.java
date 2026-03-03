package com.example.dbdcore.game.movement;

import com.example.dbdcore.config.DbdCoreConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет ускорением сурва после получения урона (DBD Burst).
 * Эффект может срабатывать много раз, но не стакается.
 */
public class BurstManager {

    private static final class BurstData {
        int ticksRemaining;
        int cooldownTicksRemaining;
    }

    private final Map<UUID, BurstData> bursts = new HashMap<>();

    public void clear() {
        bursts.clear();
    }

    /**
     * Вызывается, когда сурв получает урон.
     */
    public void trigger(ServerPlayer survivor) {
        int duration = DbdCoreConfig.COMMON.dbdBurstDurationTicks.get();
        int cooldown = DbdCoreConfig.COMMON.dbdBurstCooldownTicks.get();
        BurstData data = bursts.computeIfAbsent(survivor.getUUID(), id -> new BurstData());
        if (data.cooldownTicksRemaining > 0) {
            return;
        }
        data.ticksRemaining = duration;
        data.cooldownTicksRemaining = cooldown;
    }

    public void tick() {
        bursts.values().removeIf(data -> {
            if (data.ticksRemaining > 0) {
                data.ticksRemaining--;
            }
            if (data.cooldownTicksRemaining > 0) {
                data.cooldownTicksRemaining--;
            }
            return data.ticksRemaining <= 0 && data.cooldownTicksRemaining <= 0;
        });
    }

    /**
     * Возвращает множитель скорости для сурва.
     */
    public double getSpeedMultiplier(ServerPlayer survivor) {
        BurstData data = bursts.get(survivor.getUUID());
        if (data == null || data.ticksRemaining <= 0) {
            return 1.0D;
        }
        return DbdCoreConfig.COMMON.dbdBurstSpeedMultiplier.get();
    }
}

