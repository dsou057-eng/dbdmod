package com.example.dbdcore.world;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.config.DbdCoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * Управление всеми генераторами на карте.
 * Не отвечает за отрисовку, только за прогресс и состояние.
 */
public class GeneratorManager {

    public static final class GeneratorInstance {
        public final UUID id = UUID.randomUUID();
        public final BlockPos pos;
        public double progressPercent; // 0..100
        public boolean blocked;
        public int blockTicksRemaining;

        // Текущие игроки, взаимодействующие с генератором
        public final Set<UUID> interactingSurvivors = new HashSet<>();

        // Текущая визуальная стадия (0,1,2,3) по прогрессу
        public int visualStage = 0;

        public boolean completed() {
            return progressPercent >= 100.0D;
        }

        public GeneratorInstance(BlockPos pos) {
            this.pos = pos;
        }
    }

    private final Map<UUID, GeneratorInstance> generators = new HashMap<>();

    public Collection<GeneratorInstance> all() {
        return generators.values();
    }

    public void clear() {
        generators.clear();
    }

    /**
     * Сбрасывает визуальные индикаторы генов (блоки над ними) в воздух.
     * Вызывается при полном сбросе матча.
     */
    public void resetVisuals(ServerLevel level) {
        for (GeneratorInstance gen : generators.values()) {
            BlockPos indicatorPos = gen.pos.above();
            level.setBlock(indicatorPos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public void spawnGenerators(List<BlockPos> candidatePositions, Random random) {
        clear();
        int toSpawn = DbdCoreConfig.COMMON.generatorsToSpawn.get();
        List<BlockPos> shuffled = new ArrayList<>(candidatePositions);
        Collections.shuffle(shuffled, random);
        for (int i = 0; i < Math.min(toSpawn, shuffled.size()); i++) {
            BlockPos pos = shuffled.get(i);
            GeneratorInstance instance = new GeneratorInstance(pos);
            generators.put(instance.id, instance);
        }
    }

    public void tick(ServerLevel level) {
        int blockDuration = DbdCoreConfig.COMMON.generatorBlockDurationTicks.get();

        for (GeneratorInstance gen : generators.values()) {
            if (gen.blocked) {
                if (gen.blockTicksRemaining > 0) {
                    gen.blockTicksRemaining--;
                } else {
                    gen.blocked = false;
                }
                continue;
            }

            int interacting = gen.interactingSurvivors.size();
            if (interacting > 0 && !gen.completed()) {
                int repairSeconds;
                if (interacting == 1) {
                    repairSeconds = DbdCoreConfig.COMMON.generatorRepairTime1.get();
                } else if (interacting == 2) {
                    repairSeconds = DbdCoreConfig.COMMON.generatorRepairTime2.get();
                } else if (interacting == 3) {
                    repairSeconds = DbdCoreConfig.COMMON.generatorRepairTime3.get();
                } else {
                    repairSeconds = DbdCoreConfig.COMMON.generatorRepairTime4.get();
                }
                double progressPerTick = 100.0D / (repairSeconds * 20.0D);
                gen.progressPercent = Math.min(100.0D, gen.progressPercent + progressPerTick);
            }

            if (gen.progressPercent < 0.0D) {
                gen.progressPercent = 0.0D;
            }

            updateVisualStageAndFeedback(level, gen);
        }
    }

    public void startInteraction(ServerPlayer survivor, BlockPos generatorPos) {
        generators.values().stream()
                .filter(g -> g.pos.equals(generatorPos))
                .findFirst()
                .ifPresent(g -> g.interactingSurvivors.add(survivor.getUUID()));
    }

    public void stopInteraction(ServerPlayer survivor, BlockPos generatorPos) {
        generators.values().stream()
                .filter(g -> g.pos.equals(generatorPos))
                .findFirst()
                .ifPresent(g -> g.interactingSurvivors.remove(survivor.getUUID()));
    }

    public int countCompletedGenerators() {
        int count = 0;
        for (GeneratorInstance gen : generators.values()) {
            if (gen.completed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Сбивает прогресс генератора (атака маньяка).
     * Конкретный коэффициент регресса берётся из конфига.
     */
    public void applyRegressionOnce(BlockPos generatorPos) {
        double regressionPerSecond = DbdCoreConfig.COMMON.generatorRegressionPerSecond.get();
        generators.values().stream()
                .filter(g -> g.pos.equals(generatorPos))
                .findFirst()
                .ifPresent(g -> g.progressPercent = Math.max(0.0D, g.progressPercent - regressionPerSecond));
    }

    public void blockGenerator(BlockPos generatorPos) {
        int duration = DbdCoreConfig.COMMON.generatorBlockDurationTicks.get();
        generators.values().stream()
                .filter(g -> g.pos.equals(generatorPos))
                .findFirst()
                .ifPresent(g -> {
                    g.blocked = true;
                    g.blockTicksRemaining = duration;
                });
    }

    private void updateVisualStageAndFeedback(ServerLevel level, GeneratorInstance gen) {
        int newStage;
        if (gen.progressPercent >= 100.0D) {
            newStage = 3;
        } else if (gen.progressPercent >= 66.0D) {
            newStage = 2;
        } else if (gen.progressPercent >= 33.0D) {
            newStage = 1;
        } else {
            newStage = 0;
        }
        if (newStage == gen.visualStage) {
            return;
        }
        int oldStage = gen.visualStage;
        gen.visualStage = newStage;

        // Визуальная подсветка: меняем блок над генератором на разные варианты свечения.
        BlockPos indicatorPos = gen.pos.above();
        BlockState state;
        if (newStage == 0) {
            state = Blocks.REDSTONE_LAMP.defaultBlockState();
        } else if (newStage == 1) {
            state = Blocks.SEA_LANTERN.defaultBlockState();
        } else if (newStage == 2) {
            state = Blocks.SHROOMLIGHT.defaultBlockState();
        } else {
            state = Blocks.BEACON.defaultBlockState();
        }
        level.setBlock(indicatorPos, state, 3);

        // Звук починки для игроков, которые чинят этот ген.
        float volume;
        if (newStage == 1) {
            volume = 0.5F;
        } else if (newStage == 2) {
            volume = 0.8F;
        } else if (newStage == 3) {
            volume = 1.0F;
        } else {
            volume = 0.3F;
        }

        for (UUID id : gen.interactingSurvivors) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                if (newStage == 3) {
                    level.playSound(null, gen.pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundSource.BLOCKS, volume, 1.0F);
                } else {
                    level.playSound(null, gen.pos, SoundEvents.BLOCK_NOTE_BLOCK_HARP, SoundSource.BLOCKS, volume, 1.0F);
                }
            }
        }

        // Видимость сурва для манов на 2 секунды после полной починки.
        if (newStage == 3 && oldStage < 3) {
            int auraDuration = 40; // 2 секунды
            for (UUID id : gen.interactingSurvivors) {
                GameSystems.auras().revealSurvivor(id, auraDuration);
            }
        }
    }
}

