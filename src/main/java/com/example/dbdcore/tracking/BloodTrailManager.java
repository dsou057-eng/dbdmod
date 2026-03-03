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
 * Следы крови, появляющиеся только у раненых сурвов.
 * Видимы только ману.
 */
public class BloodTrailManager {

    public static final class BloodSpot {
        public final UUID survivorId;
        public final BlockPos pos;
        public int ageTicks;

        BloodSpot(UUID survivorId, BlockPos pos) {
            this.survivorId = survivorId;
            this.pos = pos;
        }
    }

    private final List<BloodSpot> spots = new ArrayList<>();

    public void clear() {
        spots.clear();
    }

    /**
     * Вызывается, когда раненый сурв двигается.
     */
    public void onInjuredSurvivorMove(ServerPlayer survivor) {
        spots.add(new BloodSpot(survivor.getUUID(), survivor.blockPosition()));
    }

    public void tick(ServerLevel level) {
        int maxTicks = DbdCoreConfig.COMMON.bloodLifetimeSeconds.get() * 20;
        Iterator<BloodSpot> it = spots.iterator();
        while (it.hasNext()) {
            BloodSpot spot = it.next();
            spot.ageTicks++;
            if (spot.ageTicks >= maxTicks) {
                it.remove();
            }
        }
    }

    public List<BloodSpot> getSpots() {
        return List.copyOf(spots);
    }
}

