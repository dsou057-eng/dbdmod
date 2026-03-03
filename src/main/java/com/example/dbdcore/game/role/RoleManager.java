package com.example.dbdcore.game.role;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Отвечает за назначение и хранение ролей игроков в матче.
 * Серверно‑авторитетен, не содержит клиентской логики.
 */
public class RoleManager {

    private final Map<UUID, PlayerRole> roles = new HashMap<>();

    public void clear() {
        roles.clear();
    }

    public void assignRole(ServerPlayer player, PlayerRole role) {
        roles.put(player.getUUID(), role);
    }

    public PlayerRole getRole(ServerPlayer player) {
        return roles.getOrDefault(player.getUUID(), PlayerRole.SPECTATOR);
    }

    public List<UUID> getSurvivors() {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, PlayerRole> e : roles.entrySet()) {
            if (e.getValue() == PlayerRole.SURVIVOR) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    public Optional<UUID> getKiller() {
        return roles.entrySet().stream()
                .filter(e -> e.getValue() == PlayerRole.KILLER)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}

