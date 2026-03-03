package com.example.dbdcore.game.movement;

import com.example.dbdcore.config.DbdCoreConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет станами мана (и в будущем других сущностей).
 * Используется для стана за промах и за палету.
 */
public class StunManager {

    public enum StunSource {
        MISS,
        PALLET
    }

    private static final class StunData {
        int ticksRemaining;
        StunSource source;
    }

    private final Map<UUID, StunData> stuns = new HashMap<>();

    public void clear() {
        stuns.clear();
    }

    public void applyStun(ServerPlayer killer, StunSource source) {
        int duration;
        if (source == StunSource.MISS) {
            duration = DbdCoreConfig.COMMON.killerMissStunDurationTicks.get();
        } else {
            duration = DbdCoreConfig.COMMON.killerPalletStunDurationTicks.get();
        }
        if (duration <= 0) {
            return;
        }
        StunData data = stuns.computeIfAbsent(killer.getUUID(), id -> new StunData());
        data.ticksRemaining = duration;
        data.source = source;
    }

    public void tick() {
        stuns.values().removeIf(data -> {
            if (data.ticksRemaining > 0) {
                data.ticksRemaining--;
            }
            return data.ticksRemaining <= 0;
        });
    }

    public boolean isStunned(ServerPlayer killer) {
        StunData data = stuns.get(killer.getUUID());
        return data != null && data.ticksRemaining > 0;
    }
}

