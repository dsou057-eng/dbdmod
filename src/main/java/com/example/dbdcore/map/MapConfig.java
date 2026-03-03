package com.example.dbdcore.map;

import com.example.dbdcore.DbDCoreMod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Читает карту и ключевые точки из JSON-файла конфигурации.
 *
 * Файл: config/dbdcore/map.json
 */
public class MapConfig {

    public static final class LobbyPositions {
        public BlockPos survivorLobby;
        public BlockPos killerLobby;
    }

    public static final class SpawnPositions {
        public final List<BlockPos> survivorSpawns = new ArrayList<>();
        public final List<BlockPos> killerSpawns = new ArrayList<>();
    }

    public static final class WorldPoints {
        public final List<BlockPos> generatorPositions = new ArrayList<>();
        public final List<BlockPos> exitGatePositions = new ArrayList<>();
    }

    private final LobbyPositions lobby = new LobbyPositions();
    private final SpawnPositions spawns = new SpawnPositions();
    private final WorldPoints points = new WorldPoints();

    public LobbyPositions lobby() {
        return lobby;
    }

    public SpawnPositions spawns() {
        return spawns;
    }

    public WorldPoints points() {
        return points;
    }

    public static MapConfig load(MinecraftServer server) {
        File configDir = server.getFile("config");
        File file = new File(configDir, DbDCoreMod.MOD_ID + File.separator + "map.json");
        MapConfig result = new MapConfig();
        if (!file.exists()) {
            return result;
        }
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            if (root == null) {
                return result;
            }
            JsonObject lobbyObj = root.getAsJsonObject("lobby");
            if (lobbyObj != null) {
                result.lobby.survivorLobby = readBlockPos(lobbyObj.getAsJsonObject("survivors"));
                result.lobby.killerLobby = readBlockPos(lobbyObj.getAsJsonObject("killer"));
            }

            JsonObject spawnsObj = root.getAsJsonObject("spawns");
            if (spawnsObj != null) {
                readBlockPosArray(spawnsObj.getAsJsonArray("survivors"), result.spawns.survivorSpawns);
                readBlockPosArray(spawnsObj.getAsJsonArray("killer"), result.spawns.killerSpawns);
            }

            JsonObject pointsObj = root.getAsJsonObject("points");
            if (pointsObj != null) {
                readBlockPosArray(pointsObj.getAsJsonArray("generators"), result.points.generatorPositions);
                readBlockPosArray(pointsObj.getAsJsonArray("exitGates"), result.points.exitGatePositions);
            }
        } catch (IOException e) {
            // Ошибки чтения карты логируются внешним слоем.
        }
        return result;
    }

    private static BlockPos readBlockPos(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        int x = obj.get("x").getAsInt();
        int y = obj.get("y").getAsInt();
        int z = obj.get("z").getAsInt();
        return new BlockPos(x, y, z);
    }

    private static void readBlockPosArray(JsonArray arr, List<BlockPos> out) {
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            JsonObject obj = arr.get(i).getAsJsonObject();
            out.add(readBlockPos(obj));
        }
    }
}

