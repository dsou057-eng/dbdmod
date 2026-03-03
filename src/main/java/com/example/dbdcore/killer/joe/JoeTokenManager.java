package com.example.dbdcore.killer.joe;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Жетоны «Разрушителя миров» на сурвах. Максимум 2 — при достижении триггерится фаза у Джо.
 */
public class JoeTokenManager {

    public static final int MAX_TOKENS = 2;

    private final Map<UUID, Integer> tokens = new HashMap<>();

    public void clear() {
        tokens.clear();
    }

    public int getTokens(ServerPlayer survivor) {
        return tokens.getOrDefault(survivor.getUUID(), 0);
    }

    public void setTokens(ServerPlayer survivor, int count) {
        tokens.put(survivor.getUUID(), Math.min(MAX_TOKENS, Math.max(0, count)));
    }

    /**
     * Добавляет 1 жетон. Возвращает true, если сурв достиг максимума (триггер фазы Разрушитель миров).
     */
    public boolean addToken(ServerPlayer survivor) {
        int current = getTokens(survivor);
        if (current >= MAX_TOKENS) {
            return false;
        }
        setTokens(survivor, current + 1);
        return getTokens(survivor) >= MAX_TOKENS;
    }

    public boolean hasMaxTokens(ServerPlayer survivor) {
        return getTokens(survivor) >= MAX_TOKENS;
    }

    /**
     * Есть ли хотя бы один сурв с максимумом жетонов (для входа Джо в фазу).
     */
    public boolean anySurvivorHasMaxTokens(Iterable<ServerPlayer> survivors) {
        for (ServerPlayer p : survivors) {
            if (hasMaxTokens(p)) {
                return true;
            }
        }
        return false;
    }
}
