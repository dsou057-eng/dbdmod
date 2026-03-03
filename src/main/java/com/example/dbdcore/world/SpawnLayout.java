package com.example.dbdcore.world;

import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.List;

/**
 * Базовые точки спавна сурвов и мана для текущей карты.
 * На этапе 3 координаты заданы явно, но собраны в списки и используются
 * из одного места, чтобы не дублировать хардкод.
 */
public final class SpawnLayout {

    // 5 точек спавна сурвов
    private static final List<BlockPos> SURVIVOR_SPAWNS = Arrays.asList(
            new BlockPos(33, -59, -72),
            new BlockPos(-1, -52, -94),
            new BlockPos(57, -51, -52),
            new BlockPos(67, -59, 4),
            new BlockPos(76, -50, -67)
    );

    // 5 точек спавна мана
    private static final List<BlockPos> KILLER_SPAWNS = Arrays.asList(
            new BlockPos(23, -59, -35),
            new BlockPos(55, -59, -50),
            new BlockPos(52, -59, -135),
            new BlockPos(16, -59, -99),
            new BlockPos(26, -49, -116)
    );

    private SpawnLayout() {
    }

    public static List<BlockPos> survivorSpawns() {
        return SURVIVOR_SPAWNS;
    }

    public static List<BlockPos> killerSpawns() {
        return KILLER_SPAWNS;
    }
}

