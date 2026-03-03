package com.example.dbdcore.server;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.game.health.HealthState;
import com.example.dbdcore.game.match.MatchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Отслеживает движение игроков для спавна следов крови и обработки побега через синюю шерсть.
 */
public class MovementEvents {

    private final Map<UUID, BlockPos> lastBloodSpotPos = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        MatchManager match = GameSystems.match();
        if (match.getGameState() != com.example.dbdcore.game.match.GameState.ACTIVE
                && match.getGameState() != com.example.dbdcore.game.match.GameState.ENDGAME) {
            return;
        }

        MatchManager.PlayerMatchStatus status = match.getPlayerStatus(player);
        if (status != MatchManager.PlayerMatchStatus.IN_MATCH) {
            return;
        }

        // Побег через синюю шерсть.
        BlockPos below = player.blockPosition().below();
        if (player.level().getBlockState(below).is(Blocks.BLUE_WOOL)) {
            match.escapeViaBlueWool((net.minecraft.server.level.ServerLevel) player.level(), player);
            return;
        }

        // Скорость: маны немного быстрее сурвов.
        var role = GameSystems.roles().getRole(player);
        var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            double baseSpeed = (role == com.example.dbdcore.game.role.PlayerRole.KILLER)
                    ? com.example.dbdcore.config.DbdCoreConfig.COMMON.killerBaseMoveSpeed.get()
                    : com.example.dbdcore.config.DbdCoreConfig.COMMON.survivorBaseMoveSpeed.get();
            attr.setBaseValue(baseSpeed);
        }

        // Следы крови: только если ранен и бежит.
        HealthState state = GameSystems.health().getState(player);
        if (state == HealthState.INJURED || state == HealthState.DOWNED) {
            if (player.isSprinting()) {
                spawnBloodIfMoved(player);
            }
        }
    }

    private void spawnBloodIfMoved(ServerPlayer player) {
        UUID id = player.getUUID();
        BlockPos current = player.blockPosition();
        BlockPos last = lastBloodSpotPos.get(id);
        if (last != null) {
            int dx = current.getX() - last.getX();
            int dy = current.getY() - last.getY();
            int dz = current.getZ() - last.getZ();
            if (dx * dx + dy * dy + dz * dz < 1) {
                return; // антиспам: новый спот только при движении хотя бы на 1 блок.
            }
        }
        GameSystems.blood().onInjuredSurvivorMove(player);
        lastBloodSpotPos.put(id, current);
    }
}

