package com.example.dbdcore.tracking;

import com.example.dbdcore.config.DbdCoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Следы (Scratch Marks), видимые только ману.
 * Здесь хранится только серверное состояние; визуализация и сеть делаются отдельным слоем.
 */
public class ScratchMarkManager {

    public static final class ScratchMark {
        public final UUID survivorId;
        public final BlockPos pos;
        public int ageTicks;

        ScratchMark(UUID survivorId, BlockPos pos) {
            this.survivorId = survivorId;
            this.pos = pos;
        }
    }

    private final List<ScratchMark> marks = new ArrayList<>();

    public void clear() {
        marks.clear();
    }

    /**
     * Вызывается, когда раненый или обычный сурв двигается в режиме, в котором должен оставлять следы.
     * Внешний код обязан не вызывать этот метод при скрытном передвижении или в шкафу.
     */
    public void onSurvivorMove(ServerPlayer survivor) {
        marks.add(new ScratchMark(survivor.getUUID(), survivor.blockPosition()));
    }

    public void tick(ServerLevel level) {
        int maxTicks = DbdCoreConfig.COMMON.scratchLifetimeSeconds.get() * 20;
        Iterator<ScratchMark> it = marks.iterator();
        while (it.hasNext()) {
            ScratchMark mark = it.next();
            mark.ageTicks++;
            if (mark.ageTicks >= maxTicks) {
                it.remove();
            }
        }
    }

    public List<ScratchMark> getMarks() {
        return List.copyOf(marks);
    }
}

