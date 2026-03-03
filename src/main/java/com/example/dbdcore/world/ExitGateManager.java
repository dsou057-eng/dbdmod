package com.example.dbdcore.world;

import com.example.dbdcore.config.DbdCoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * Управление выходными воротами.
 */
public class ExitGateManager {

    public enum ExitGateState {
        LOCKED,
        POWERED,   // после починки нужного числа генераторов
        OPENING,
        OPEN
    }

    public static final class ExitGateInstance {
        public final UUID id = UUID.randomUUID();
        public final BlockPos pos;
        public ExitGateState state = ExitGateState.LOCKED;
        public double progressSeconds; // 0..openTime
        public final Set<UUID> interactingSurvivors = new HashSet<>();

        public ExitGateInstance(BlockPos pos) {
            this.pos = pos;
        }
    }

    private final Map<UUID, ExitGateInstance> gates = new HashMap<>();

    public void clear() {
        gates.clear();
    }

    public Collection<ExitGateInstance> all() {
        return gates.values();
    }

    public void spawnGates(List<BlockPos> positions) {
        clear();
        for (BlockPos pos : positions) {
            ExitGateInstance instance = new ExitGateInstance(pos);
            gates.put(instance.id, instance);
        }
    }

    public void powerAllGates() {
        for (ExitGateInstance gate : gates.values()) {
            if (gate.state == ExitGateState.LOCKED) {
                gate.state = ExitGateState.POWERED;
            }
        }
    }

    public void tick(ServerLevel level) {
        int openTime = DbdCoreConfig.COMMON.exitGateOpenTimeSeconds.get();
        for (ExitGateInstance gate : gates.values()) {
            if (gate.state == ExitGateState.POWERED || gate.state == ExitGateState.OPENING) {
                if (!gate.interactingSurvivors.isEmpty()) {
                    gate.state = ExitGateState.OPENING;
                    double delta = 1.0D / 20.0D; // 1/20 секунды
                    gate.progressSeconds = Math.min(openTime, gate.progressSeconds + delta);
                    if (gate.progressSeconds >= openTime && gate.state != ExitGateState.OPEN) {
                        gate.state = ExitGateState.OPEN;
                        openGateBlocks(level, gate);
                    }
                }
            }
        }
    }

    public void startInteraction(ServerPlayer survivor, BlockPos gatePos) {
        gates.values().stream()
                .filter(g -> g.pos.equals(gatePos))
                .findFirst()
                .ifPresent(g -> g.interactingSurvivors.add(survivor.getUUID()));
    }

    public void stopInteraction(ServerPlayer survivor, BlockPos gatePos) {
        gates.values().stream()
                .filter(g -> g.pos.equals(gatePos))
                .findFirst()
                .ifPresent(g -> g.interactingSurvivors.remove(survivor.getUUID()));
    }

    /**
     * Физически открывает конкретные ворота, повторяя поведение указанных /fill команд.
     */
    private void openGateBlocks(ServerLevel level, ExitGateInstance gate) {
        BlockPos pos = gate.pos;
        // Gate A: 58 -59 9  -> /fill 59 -59 10 59 -58 10 air
        if (pos.getX() == 58 && pos.getY() == -59 && pos.getZ() == 9) {
            fillBox(level, 59, -59, 10, 59, -58, 10);
        }
        // Gate B: -2 -60 0 -> /fill -3 -60 1 -3 -59 1 air
        else if (pos.getX() == -2 && pos.getY() == -60 && pos.getZ() == 0) {
            fillBox(level, -3, -60, 1, -3, -59, 1);
        }
        // Gate C: -3 -59 -85 -> /fill -4 -59 -86 -4 -58 -86 air
        else if (pos.getX() == -3 && pos.getY() == -59 && pos.getZ() == -85) {
            fillBox(level, -4, -59, -86, -4, -58, -86);
        }
    }

    private void fillBox(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * Восстанавливает физические блоки закрытых ворот (железные прутья)
     * для всех трёх выходов. Вызывается при сбросе матча/старте нового.
     */
    public void restoreClosedBlocks(ServerLevel level) {
        // Gate A: /fill 59 -59 10 59 -58 10 iron_bars
        restoreBox(level, 59, -59, 10, 59, -58, 10);
        // Gate B: /fill -3 -60 1 -3 -59 1 iron_bars
        restoreBox(level, -3, -60, 1, -3, -59, 1);
        // Gate C: /fill -4 -59 -86 -4 -58 -86 iron_bars
        restoreBox(level, -4, -59, -86, -4, -58, -86);
    }

    private void restoreBox(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.IRON_BARS.defaultBlockState(), 3);
                }
            }
        }
    }
}

