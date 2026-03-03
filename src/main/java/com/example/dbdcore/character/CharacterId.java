package com.example.dbdcore.character;

import com.example.dbdcore.game.role.PlayerRole;

/**
 * Идентификаторы базовых персонажей DBD-режима.
 * Здесь задаются только ID и базовая роль, без конкретных перков/умений.
 */
public enum CharacterId {

    // Survivors
    SURV_GENERIC("SURV", PlayerRole.SURVIVOR),
    DK("DK", PlayerRole.SURVIVOR),

    // Killers
    JD("JD", PlayerRole.KILLER),
    JOE("JOE", PlayerRole.KILLER),
    SHELKUN("SHELKUN", PlayerRole.KILLER),
    KAZAK("KAZAK", PlayerRole.KILLER),
    CLEANER("CLEANER", PlayerRole.KILLER);

    private final String id;
    private final PlayerRole role;

    CharacterId(String id, PlayerRole role) {
        this.id = id;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public PlayerRole getRole() {
        return role;
    }

    public static CharacterId byId(String raw) {
        String upper = raw.toUpperCase();
        for (CharacterId value : values()) {
            if (value.id.equals(upper)) {
                return value;
            }
        }
        return null;
    }
}

