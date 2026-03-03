package com.example.dbdcore.game.health;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Кастомная система HP для сурвов.
 * M1 мана: 3 удара до дауна (0→1→2→3), 4-й удар = смерть.
 * Состояние: hitCount 0,1 = HEALTHY; 2 = INJURED; 3 = DOWNED; 4 = DEAD.
 */
public class HealthManager {

    private final Map<UUID, Integer> hitCount = new HashMap<>();

    private static final int STAGES_TO_DOWN = 3;
    private static final int STAGES_TO_DEAD = 4;

    public void clear() {
        hitCount.clear();
    }

    public HealthState getState(ServerPlayer player) {
        int h = hitCount.getOrDefault(player.getUUID(), 0);
        if (h <= 1) return HealthState.HEALTHY;
        if (h == 2) return HealthState.INJURED;
        if (h == 3) return HealthState.DOWNED;
        return HealthState.DEAD;
    }

    public void setState(ServerPlayer player, HealthState state) {
        switch (state) {
            case HEALTHY -> hitCount.put(player.getUUID(), 0);
            case INJURED -> hitCount.put(player.getUUID(), 2);
            case DOWNED -> hitCount.put(player.getUUID(), 3);
            case DEAD -> hitCount.put(player.getUUID(), STAGES_TO_DEAD);
        }
    }

    /** Один удар M1 или способности: +1 стадия. Сносит за 3 удара (0→1→2→3 = down). */
    public void applyHit(ServerPlayer survivor) {
        int current = hitCount.getOrDefault(survivor.getUUID(), 0);
        if (current < STAGES_TO_DEAD) {
            hitCount.put(survivor.getUUID(), current + 1);
        }
    }

    /** То же, что applyHit — один удар M1. */
    public void applyM1Hit(ServerPlayer survivor) {
        applyHit(survivor);
    }
}

