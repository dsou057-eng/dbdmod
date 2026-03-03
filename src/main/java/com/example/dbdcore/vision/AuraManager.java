package com.example.dbdcore.vision;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Базовая система временной видимости сурвов для манов.
 * На этом этапе хранит только таймеры подсветки; реальный визуал и сеть будут добавлены позже.
 */
public class AuraManager {

    public static final class AuraEntry {
        public final UUID targetId;
        public int ticksRemaining;

        public AuraEntry(UUID targetId, int ticksRemaining) {
            this.targetId = targetId;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private final List<AuraEntry> activeAuras = new ArrayList<>();

    public void clear() {
        activeAuras.clear();
    }

    /**
     * Включить подсветку сурва на заданное количество тиков.
     */
    public void revealSurvivor(UUID survivorId, int durationTicks) {
        activeAuras.add(new AuraEntry(survivorId, durationTicks));
    }

    public void tick(ServerLevel level) {
        Iterator<AuraEntry> it = activeAuras.iterator();
        while (it.hasNext()) {
            AuraEntry entry = it.next();
            entry.ticksRemaining--;
            if (entry.ticksRemaining <= 0) {
                it.remove();
                continue;
            }
            // В дальнейшем здесь можно отправлять пакеты мана для визуализации ауры.
        }
    }

    public boolean isRevealed(ServerPlayer player) {
        UUID id = player.getUUID();
        for (AuraEntry entry : activeAuras) {
            if (entry.targetId.equals(id)) {
                return true;
            }
        }
        return false;
    }
}

