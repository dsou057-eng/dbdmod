package com.example.dbdcore.server;

import com.example.dbdcore.GameSystems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Глобальные серверные события: тик матча, обновление систем стана, Burst, следов и т.д.
 */
public class ServerEvents {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                GameSystems.jdShovel().tick(level, player);
                GameSystems.joeState().tick(level, player);
                GameSystems.shelkunState().tick(level, player);
                GameSystems.kazak().tick(level, player);
            }
            GameSystems.jdProjectiles().tick(level);
            GameSystems.joePotions().tick(level);
            GameSystems.match().tick(level);
            GameSystems.burst().tick();
            GameSystems.stuns().tick();
            GameSystems.scratches().tick(level);
            GameSystems.blood().tick(level);
            GameSystems.survivorSounds().onTick(level);
            GameSystems.ambient().tick(level, GameSystems.match().getGameState());
            GameSystems.auras().tick(level);
        }
    }
}

