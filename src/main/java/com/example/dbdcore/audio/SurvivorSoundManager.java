package com.example.dbdcore.audio;

import com.example.dbdcore.config.DbdCoreConfig;
import com.example.dbdcore.game.health.HealthState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Отвечает за серверную часть звуков сурва (дыхание, стоны, шаги).
 * Самих звуков и ресурсов не определяет, только генерирует события/сигналы.
 */
public class SurvivorSoundManager {

    public enum SoundType {
        FOOTSTEP,
        INJURED_GROAN,
        BREATH
    }

    public static final class EmittedSound {
        public final UUID sourceId;
        public final SoundType type;
        public final double x;
        public final double y;
        public final double z;
        public final double hearRadius;

        public EmittedSound(UUID sourceId, SoundType type, double x, double y, double z, double hearRadius) {
            this.sourceId = sourceId;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hearRadius = hearRadius;
        }
    }

    private EmittedSound lastSound; // Базовый пример, в реальности можно делать очередь.

    // Кулдаун стонов для каждого сурва, чтобы не спамить звуки.
    private final Map<UUID, Integer> groanCooldownTicks = new HashMap<>();

    public void clear() {
        lastSound = null;
        groanCooldownTicks.clear();
    }

    public void onSurvivorMove(ServerPlayer survivor, boolean isSprinting) {
        if (!isSprinting) {
            return;
        }
        double radius = DbdCoreConfig.COMMON.survivorFootstepHearRadius.get();
        emit(survivor, SoundType.FOOTSTEP, radius);
    }

    public void onTick(ServerLevel level) {
        int cooldownReset = 40; // 2 секунды при 20 тиках в секунду.

        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            int current = groanCooldownTicks.getOrDefault(id, 0);
            if (current > 0) {
                groanCooldownTicks.put(id, current - 1);
                continue;
            }
            // Стоны активны всегда, если сурв ранен (INJURED или DOWNED), независимо от бега.
            // Проверка состояния здоровья выполняется внешним кодом,
            // который вызывает этот метод только для полноценных участников матча.
            // Здесь мы просто периодически издаём звук вокруг игрока.
            double radius = DbdCoreConfig.COMMON.survivorInjuredGroanHearRadius.get();
            emit(player, SoundType.INJURED_GROAN, radius);
            groanCooldownTicks.put(id, cooldownReset);
        }
    }

    public void onHealthChanged(ServerPlayer survivor, HealthState newState) {
        // Этот метод оставлен на случай мгновенного триггера звука при получении урона;
        // постоянные стоны обрабатываются в onTick.
        if (newState == HealthState.INJURED || newState == HealthState.DOWNED) {
            double radius = DbdCoreConfig.COMMON.survivorInjuredGroanHearRadius.get();
            emit(survivor, SoundType.INJURED_GROAN, radius);
        }
    }

    private void emit(ServerPlayer survivor, SoundType type, double radius) {
        lastSound = new EmittedSound(
                survivor.getUUID(),
                type,
                survivor.getX(),
                survivor.getY(),
                survivor.getZ(),
                radius
        );
        // Фактическая отправка клиенту реализуется через отдельный сетевой слой.
    }

    public EmittedSound getLastSound() {
        return lastSound;
    }
}

