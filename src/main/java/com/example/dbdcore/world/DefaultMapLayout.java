package com.example.dbdcore.world;

import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.List;

/**
 * Базовая раскладка генераторов и ворот для этапа 2.5.
 * Все координаты заданы явно, но собраны в списки,
 * чтобы не хардкодить логику под одного игрока.
 */
public final class DefaultMapLayout {

    private static final List<BlockPos> GENERATOR_POSITIONS = Arrays.asList(
            new BlockPos(58, -44, -110),
            new BlockPos(66, -42, -58),
            new BlockPos(66, -50, -59),
            new BlockPos(25, -58, -111),
            new BlockPos(58, -58, -112),
            new BlockPos(59, -58, -70),
            new BlockPos(59, -58, -28),
            new BlockPos(38, -50, -27),
            new BlockPos(66, -50, -29),
            new BlockPos(66, -50, -59)
    );

    private static final List<BlockPos> EXIT_GATE_POSITIONS = Arrays.asList(
            new BlockPos(58, -59, 9),     // Gate A
            new BlockPos(-2, -60, 0),     // Gate B
            new BlockPos(-3, -59, -85)    // Gate C
    );

    private DefaultMapLayout() {
    }

    /** Все возможные позиции генераторов (10 штук). */
    public static List<BlockPos> generatorPositions() {
        return GENERATOR_POSITIONS;
    }

    /** Все позиции ворот (3 штуки). */
    public static List<BlockPos> exitGatePositions() {
        return EXIT_GATE_POSITIONS;
    }
}

