package com.example.dbdcore.ui;

import com.example.dbdcore.game.match.GameState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.boss.ServerBossEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Управляет bossbar'ом матча: текстом, цветом и прогрессом в зависимости от стадии.
 */
public class MatchBossBarManager {

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("Подготовка"),
            BossBarColor.BLUE,
            BossBarOverlay.PROGRESS
    );

    private final Set<ServerPlayer> attachedPlayers = new HashSet<>();

    public MatchBossBarManager() {
        bossEvent.setVisible(true);
        bossEvent.setProgress(1.0F);
    }

    public void clear() {
        for (ServerPlayer player : attachedPlayers) {
            bossEvent.removePlayer(player);
        }
        attachedPlayers.clear();
    }

    /**
     * Синхронизирует список игроков в bossbar с игроками в мире.
     */
    public void syncPlayers(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!attachedPlayers.contains(player)) {
                bossEvent.addPlayer(player);
                attachedPlayers.add(player);
            }
        }
        attachedPlayers.removeIf(p -> {
            if (!level.players().contains(p)) {
                bossEvent.removePlayer(p);
                return true;
            }
            return false;
        });
    }

    /**
     * Обновляет подпись и цвет bossbar в зависимости от стадии матча.
     */
    public void updateForState(GameState state) {
        switch (state) {
            case LOBBY -> {
                bossEvent.setName(Component.literal("Ожидание матча"));
                bossEvent.setColor(BossBarColor.WHITE);
                bossEvent.setProgress(0.0F);
            }
            case PREPARATION -> {
                bossEvent.setName(Component.literal("Подготовка"));
                bossEvent.setColor(BossBarColor.BLUE);
                bossEvent.setProgress(1.0F);
            }
            case ACTIVE -> {
                bossEvent.setName(Component.literal("Матч начался"));
                bossEvent.setColor(BossBarColor.GREEN);
            }
            case ENDGAME -> {
                bossEvent.setName(Component.literal("Эндгейм"));
                bossEvent.setColor(BossBarColor.RED);
            }
            case FINISHED -> {
                bossEvent.setName(Component.literal("Матч завершён"));
                bossEvent.setColor(BossBarColor.PURPLE);
                bossEvent.setProgress(0.0F);
            }
        }
    }

    /**
     * Устанавливает прогресс bossbar, например для таймера коллапса.
     */
    public void setProgress(double fraction) {
        float clamped = (float) Math.max(0.0D, Math.min(1.0D, fraction));
        bossEvent.setProgress(clamped);
    }

    /**
     * Для мана Шелкунчик: отобразить состояние (Движется/Стоит и ваншот КД).
     */
    public void setShelkunState(String line) {
        bossEvent.setName(Component.literal("Шелкунчик: " + line));
    }

    /**
     * Для мана Казак: заряд блевоты, трапы, КД ТП.
     */
    public void setKazakState(String line) {
        bossEvent.setName(Component.literal("Казак: " + line));
    }
}

