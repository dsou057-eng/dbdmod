package com.example.dbdcore.game.match;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.config.DbdCoreConfig;
import com.example.dbdcore.game.role.RoleManager;
import com.example.dbdcore.map.MapConfig;
import com.example.dbdcore.world.DefaultMapLayout;
import com.example.dbdcore.world.SpawnLayout;
import com.example.dbdcore.world.ExitGateManager;
import com.example.dbdcore.world.GeneratorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Управляет состоянием матча и переходами state‑machine.
 *
 * Переходы:
 * LOBBY -> PREPARATION  (роли выбраны)
 * PREPARATION -> ACTIVE (матч запущен)
 * ACTIVE -> ENDGAME     (починено нужное число генераторов)
 * ENDGAME -> FINISHED   (все сурвы убиты или сбежали)
 */
public class MatchManager {

    private final RoleManager roleManager;
    private final GeneratorManager generatorManager;
    private final ExitGateManager exitGateManager;

    private GameState gameState = GameState.LOBBY;
    private boolean autoStartRequested = false;
    private int autoStartTicksRemaining = 0;
    private int endgameCollapseTicksRemaining = 0;
    private int introTicksRemaining = 0;
    private int endgameCollapseTotalTicks = 0;

    public enum PlayerMatchStatus {
        IN_MATCH,
        ESCAPED,
        DEAD
    }

    private final java.util.Map<java.util.UUID, PlayerMatchStatus> playerStates = new java.util.HashMap<>();

    public MatchManager(RoleManager roleManager,
                        GeneratorManager generatorManager,
                        ExitGateManager exitGateManager) {
        this.roleManager = roleManager;
        this.generatorManager = generatorManager;
        this.exitGateManager = exitGateManager;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void toPreparation() {
        if (gameState != GameState.LOBBY) {
            return;
        }
        gameState = GameState.PREPARATION;
    }

    public void startMatch(ServerLevel level) {
        if (gameState != GameState.PREPARATION) {
            return;
        }
        gameState = GameState.ACTIVE;
        GameSystems.bossbar().updateForState(gameState);
        // Спаун генераторов и ворот по предзаданным позициям.
        MapConfig map = MapConfig.load(level.getServer());
        var generatorPositions = map.points().generatorPositions.isEmpty()
                ? DefaultMapLayout.generatorPositions()
                : map.points().generatorPositions;
        var exitGatePositions = map.points().exitGatePositions.isEmpty()
                ? DefaultMapLayout.exitGatePositions()
                : map.points().exitGatePositions;
        GameSystems.generators().spawnGenerators(generatorPositions, level.random);
        GameSystems.exitGates().spawnGates(exitGatePositions);

        // Телепорт сурвов и мана на стартовые точки из SpawnLayout.
        spawnPlayers(level);

        // Интро-фаза: эффекты слепоты и замедления на 5 секунд.
        introTicksRemaining = 5 * 20;
        applyIntroEffects(level);

        // Атмосферный звук в начале матча.
        level.playSound(null, new BlockPos(0, 0, 0), SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, 1.0F, 1.0F);
    }

    public void tick(ServerLevel level) {
        if (gameState == GameState.PREPARATION && autoStartRequested) {
            if (autoStartTicksRemaining > 0) {
                autoStartTicksRemaining--;
                if (autoStartTicksRemaining == 0) {
                    startMatch(level);
                }
            }
        }

        GameSystems.bossbar().syncPlayers(level);

        if (gameState == GameState.ACTIVE || gameState == GameState.ENDGAME) {
            if (introTicksRemaining > 0) {
                introTicksRemaining--;
                if (introTicksRemaining == 0) {
                    endIntro(level);
                }
            }

            generatorManager.tick(level);
            exitGateManager.tick(level);
            checkStateTransitions();
            checkEscapes(level);
        }
    }

    private void checkStateTransitions() {
        if (gameState == GameState.ACTIVE) {
            int required = DbdCoreConfig.COMMON.generatorsToRepairForEscape.get();
            int completed = generatorManager.countCompletedGenerators();
            if (completed >= required) {
                gameState = GameState.ENDGAME;
                GameSystems.exitGates().powerAllGates();
                endgameCollapseTotalTicks = DbdCoreConfig.COMMON.endgameCollapseDurationSeconds.get() * 20;
                endgameCollapseTicksRemaining = endgameCollapseTotalTicks;
                GameSystems.bossbar().updateForState(gameState);
            } else if (required > 0) {
                double fraction = (double) completed / (double) required;
                GameSystems.bossbar().setProgress(fraction);
            }
        }

        if (gameState == GameState.ENDGAME) {
            if (endgameCollapseTicksRemaining > 0) {
                endgameCollapseTicksRemaining--;
                if (endgameCollapseTotalTicks > 0) {
                    double fraction = (double) endgameCollapseTicksRemaining / (double) endgameCollapseTotalTicks;
                    GameSystems.bossbar().setProgress(fraction);
                }
                if (endgameCollapseTicksRemaining == 0) {
                    // Коллапс: все оставшиеся участники умирают.
                    for (java.util.UUID id : playerStates.keySet()) {
                        if (playerStates.get(id) == PlayerMatchStatus.IN_MATCH) {
                            playerStates.put(id, PlayerMatchStatus.DEAD);
                        }
                    }
                    if (allPlayersFinished()) {
                        gameState = GameState.FINISHED;
                        GameSystems.bossbar().updateForState(gameState);
                    }
                }
            }
        }
    }

    /**
     * Регистрирует игрока как участника матча (этап 2.5, без ролей).
     */
    public void registerParticipant(net.minecraft.server.level.ServerPlayer player) {
        playerStates.put(player.getUUID(), PlayerMatchStatus.IN_MATCH);
    }

    public PlayerMatchStatus getPlayerStatus(net.minecraft.server.level.ServerPlayer player) {
        return playerStates.getOrDefault(player.getUUID(), PlayerMatchStatus.DEAD);
    }

    private void checkEscapes(ServerLevel level) {
        if (playerStates.isEmpty()) {
            return;
        }
        for (var player : level.players()) {
            var state = playerStates.get(player.getUUID());
            if (state != PlayerMatchStatus.IN_MATCH) {
                continue;
            }
            if (isInAnyOpenGateZone(player)) {
                onPlayerEscaped(level, player);
            }
        }
    }

    private boolean isInAnyOpenGateZone(net.minecraft.server.level.ServerPlayer player) {
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        double radius = 1.5D;
        double radiusSq = radius * radius;
        for (ExitGateManager.ExitGateInstance gate : exitGateManager.all()) {
            if (gate.state != ExitGateManager.ExitGateState.OPEN) {
                continue;
            }
            double gx = gate.pos.getX() + 0.5D;
            double gy = gate.pos.getY();
            double gz = gate.pos.getZ() + 0.5D;
            double dx = px - gx;
            double dy = py - gy;
            double dz = pz - gz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private void onPlayerEscaped(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        playerStates.put(player.getUUID(), PlayerMatchStatus.ESCAPED);

        // Телепортируем в фиксированную safe‑зону для этапа 2.6.
        BlockPos safe = new BlockPos(-73, -59, 294);
        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(), player.getXRot());

        if (allPlayersFinished()) {
            gameState = GameState.FINISHED;
        }
    }

    private boolean allPlayersFinished() {
        if (playerStates.isEmpty()) {
            return false;
        }
        for (PlayerMatchStatus status : playerStates.values()) {
            if (status == PlayerMatchStatus.IN_MATCH) {
                return false;
            }
        }
        return true;
    }

    /**
     * Побег через синюю шерсть: та же логика, что при выходе через ворота,
     * но без проверки зон ворот и без таймеров.
     */
    public void escapeViaBlueWool(ServerLevel level, net.minecraft.server.level.ServerPlayer player) {
        if (playerStates.get(player.getUUID()) != PlayerMatchStatus.IN_MATCH) {
            return;
        }
        onPlayerEscaped(level, player);
    }

    private void spawnPlayers(ServerLevel level) {
        // На этапе 3 работаем только с участниками матча как с "сурвами".
        java.util.List<java.util.UUID> participants = new java.util.ArrayList<>();
        for (var e : playerStates.entrySet()) {
            if (e.getValue() == PlayerMatchStatus.IN_MATCH) {
                participants.add(e.getKey());
            }
        }
        java.util.Collections.shuffle(participants, level.random);

        java.util.List<BlockPos> survivorSpawns = new java.util.ArrayList<>(SpawnLayout.survivorSpawns());
        java.util.Collections.shuffle(survivorSpawns, level.random);

        int i = 0;
        for (java.util.UUID id : participants) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                continue;
            }
            if (i >= survivorSpawns.size()) {
                break;
            }
            BlockPos pos = survivorSpawns.get(i);
            p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
            i++;
        }

        // В будущем сюда будет добавлен спавн мана с учётом расстояния от сурвов.
    }

    private void applyIntroEffects(ServerLevel level) {
        for (java.util.UUID id : playerStates.keySet()) {
            if (playerStates.get(id) != PlayerMatchStatus.IN_MATCH) {
                continue;
            }
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                continue;
            }
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, introTicksRemaining, 255, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, introTicksRemaining, 0, false, false, true));
        }
        GameSystems.bossbar().updateForState(GameState.PREPARATION);
    }

    private void endIntro(ServerLevel level) {
        for (java.util.UUID id : playerStates.keySet()) {
            if (playerStates.get(id) != PlayerMatchStatus.IN_MATCH) {
                continue;
            }
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                continue;
            }
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.removeEffect(MobEffects.BLINDNESS);
        }
        // Звук старта охоты.
        level.playSound(null, new BlockPos(0, 0, 0), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.AMBIENT, 1.0F, 1.0F);
        GameSystems.bossbar().updateForState(GameState.ACTIVE);
    }

    /**
     * Запросить автозапуск матча: переключить в PREPARATION и запустить таймер.
     */
    public void requestAutoStart(MinecraftServer server) {
        if (gameState != GameState.LOBBY) {
            return;
        }
        toPreparation();
        autoStartRequested = true;
        autoStartTicksRemaining = DbdCoreConfig.COMMON.matchStartDelaySeconds.get() * 20;

        // Телепорт всех игроков в их лобби.
        MapConfig map = MapConfig.load(server);
        var lobby = map.lobby();
        ServerLevel level = server.overworld();
        for (var uuid : roleManager.getSurvivors()) {
            var p = level.getPlayerByUUID(uuid);
            if (p != null && lobby.survivorLobby != null) {
                var pos = lobby.survivorLobby;
                p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
            }
        }
        roleManager.getKiller().ifPresent(uuid -> {
            var p = level.getPlayerByUUID(uuid);
            if (p != null && lobby.killerLobby != null) {
                var pos = lobby.killerLobby;
                p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
            }
        });
    }

    /**
     * Полный сброс матча в LOBBY для административной команды.
     * Восстанавливает ворота и очищает визуальные элементы генов.
     */
    public void resetToLobby(ServerLevel level) {
        gameState = GameState.LOBBY;
        autoStartRequested = false;
        autoStartTicksRemaining = 0;
        endgameCollapseTicksRemaining = 0;
        endgameCollapseTotalTicks = 0;
        introTicksRemaining = 0;
        generatorManager.resetVisuals(level);
        exitGateManager.restoreClosedBlocks(level);
        generatorManager.clear();
        exitGateManager.clear();
        playerStates.clear();
        GameSystems.health().clear();
        GameSystems.burst().clear();
        GameSystems.stuns().clear();
        GameSystems.scratches().clear();
        GameSystems.blood().clear();
        GameSystems.survivorSounds().clear();
        GameSystems.loadouts().clear();
        GameSystems.roles().clear();
        GameSystems.joeTokens().clear();
        GameSystems.joeState().clear();
        GameSystems.joePotions().clear();
        GameSystems.shelkunState().clear();
        GameSystems.kazak().clear(level);
    }
}

