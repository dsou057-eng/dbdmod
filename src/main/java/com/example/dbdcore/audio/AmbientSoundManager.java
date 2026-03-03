package com.example.dbdcore.audio;

import com.example.dbdcore.game.match.GameState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Random;

/**
 * Редкие атмосферные звуки в зависимости от стадии матча.
 * Не спамит и не перекрывает ключевые звуки.
 */
public class AmbientSoundManager {

    private int ambientTicks = 0;
    private final Random random = new Random();

    public void clear() {
        ambientTicks = 0;
    }

    public void tick(ServerLevel level, GameState state) {
        if (ambientTicks > 0) {
            ambientTicks--;
            return;
        }

        // Периодичность: 30–90 секунд.
        ambientTicks = 20 * (30 + random.nextInt(60));

        ServerPlayer any = level.getRandomPlayer();
        if (any == null) {
            return;
        }

        switch (state) {
            case ACTIVE -> level.playSound(null, any.blockPosition(), SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 0.6F, 1.0F);
            case ENDGAME -> level.playSound(null, any.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.AMBIENT, 0.8F, 0.8F);
            default -> {
            }
        }
    }
}

