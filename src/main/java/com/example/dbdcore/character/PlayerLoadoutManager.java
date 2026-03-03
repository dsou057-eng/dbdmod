package com.example.dbdcore.character;

import com.example.dbdcore.game.role.PlayerRole;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Хранит выбранного персонажа, роль и перки игрока.
 * Эффекты перков реализуются вне ядра через хуки.
 */
public class PlayerLoadoutManager {

    public static final class Loadout {
        public final CharacterId characterId;
        public final PlayerRole role;
        public final List<Perk> perks;

        public Loadout(CharacterId characterId, PlayerRole role, List<Perk> perks) {
            this.characterId = characterId;
            this.role = role;
            this.perks = List.copyOf(perks);
        }
    }

    private final Map<UUID, Loadout> loadouts = new HashMap<>();

    public void clear() {
        loadouts.clear();
    }

    public void setLoadout(ServerPlayer player, CharacterId characterId, PlayerRole role, List<Perk> perks) {
        loadouts.put(player.getUUID(), new Loadout(characterId, role, new ArrayList<>(perks)));
    }

    public Loadout getLoadout(ServerPlayer player) {
        return loadouts.get(player.getUUID());
    }
}

