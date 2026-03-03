package com.example.dbdcore.commands;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.character.CharacterId;
import com.example.dbdcore.character.PlayerLoadoutManager;
import com.example.dbdcore.config.DbdCoreConfig;
import com.example.dbdcore.game.match.GameState;
import com.example.dbdcore.game.match.MatchManager;
import com.example.dbdcore.game.role.PlayerRole;
import com.example.dbdcore.map.MapConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.StringArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * Регистрация и логика серверных команд режима.
 *
 * /start <player> <ID>  - запуск матча для указанного игрока (этап 2.5)
 * /start <ID>           - выбор роли и персонажа (этап с ролями)
 * /dbd reset            - сброс текущего матча
 */
public class DbdCommands {

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("start")
                        .requires(src -> src.hasPermission(0))
                        // Этап 2.5: /start <player> <id>
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            String id = StringArgumentType.getString(ctx, "id");
                                            return handleStartForPlayer(ctx.getSource(), target, id);
                                        })))
                        // Предыдущий этап: /start <id> (выбор роли и персонажа)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    return handleRoleStart(ctx.getSource(), id);
                                }))
        );

        event.getDispatcher().register(
                Commands.literal("dbd")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    resetMatch(ctx.getSource());
                                    return 1;
                                }))
        );
    }

    /**
     * Этап 2.5: старт матча для конкретного игрока без ролей (только инфраструктура).
     */
    private static int handleStartForPlayer(CommandSourceStack source, ServerPlayer player, String idRaw) {
        MatchManager match = GameSystems.match();
        if (match.getGameState() != GameState.LOBBY) {
            source.sendFailure(Component.literal("Матч уже идёт или запущен."));
            return 0;
        }

        // Определяем роль по ID.
        CharacterId characterId = CharacterId.byId(idRaw);
        if (characterId != null) {
            PlayerRole role = characterId.getRole();
            GameSystems.roles().assignRole(player, role);
            GameSystems.loadouts().setLoadout(player, characterId, role, List.of());
        }

        source.sendSuccess(() -> Component.literal("Старт матча для игрока " + player.getGameProfile().getName() + " с ID " + idRaw), false);

        match.registerParticipant(player);

        // Запускаем матч сразу: инициализация генераторов, ворот и логики.
        MinecraftServer server = source.getServer();
        ServerLevel level = server.overworld();
        match.toPreparation();
        match.startMatch(level);
        return 1;
    }

    /**
     * Предыдущий этап: выбор роли/персонажа без запуска матча.
     */
    private static int handleRoleStart(CommandSourceStack source, String idRaw) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }

        MatchManager match = GameSystems.match();
        if (match.getGameState() != GameState.LOBBY) {
            source.sendFailure(Component.literal("Матч уже идёт или запущен."));
            return 0;
        }

        CharacterId characterId = CharacterId.byId(idRaw);
        if (characterId == null) {
            source.sendFailure(Component.literal("Неизвестный ID персонажа: " + idRaw));
            return 0;
        }

        PlayerRole role = characterId.getRole();

        // Ограничение: маньяк может быть только один.
        if (role == PlayerRole.KILLER && !GameSystems.roles().getKiller().isEmpty()) {
            source.sendFailure(Component.literal("Маньяк уже выбран."));
            return 0;
        }

        // Назначаем роль игроку.
        GameSystems.roles().assignRole(player, role);

        // Заглушка перков: пустой список, реальные перки подключаются позже.
        GameSystems.loadouts().setLoadout(player, characterId, role, List.of());

        // Телепортируем в лобби нужного типа.
        MinecraftServer server = source.getServer();
        MapConfig mapConfig = MapConfig.load(server);
        ServerLevel level = server.overworld();
        BlockPos lobbyPos;
        if (role == PlayerRole.SURVIVOR) {
            lobbyPos = mapConfig.lobby().survivorLobby;
        } else {
            lobbyPos = mapConfig.lobby().killerLobby;
        }
        if (lobbyPos != null) {
            player.teleportTo(level, lobbyPos.getX() + 0.5, lobbyPos.getY(), lobbyPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        }

        source.sendSuccess(() -> Component.literal("Выбран персонаж " + characterId.getId() + " как " + role), false);

        // Проверяем условия автостарта.
        autoStartIfReady(server, match);
        return 1;
    }

    private static void autoStartIfReady(MinecraftServer server, MatchManager match) {
        int survivors = GameSystems.roles().getSurvivors().size();
        boolean hasKiller = GameSystems.roles().getKiller().isPresent();
        if (!hasKiller || survivors == 0) {
            return;
        }
        match.requestAutoStart(server);
    }

    private static void resetMatch(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        GameSystems.match().resetToLobby(level);
        source.sendSuccess(() -> Component.literal("Матч DBD сброшен в состояние LOBBY."), true);
    }
}

