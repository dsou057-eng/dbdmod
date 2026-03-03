package com.example.dbdcore;

import com.example.dbdcore.config.DbdCoreConfig;
import com.example.dbdcore.server.CombatEvents;
import com.example.dbdcore.server.InteractionEvents;
import com.example.dbdcore.server.MovementEvents;
import com.example.dbdcore.server.ServerEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DbDCoreMod.MOD_ID)
public class DbDCoreMod {

    public static final String MOD_ID = "dbdcore";

    public DbDCoreMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        DbdCoreConfig.register();
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        MinecraftForge.EVENT_BUS.register(new MovementEvents());
        MinecraftForge.EVENT_BUS.register(new CombatEvents());
        MinecraftForge.EVENT_BUS.register(new InteractionEvents());
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        // Инициализация высокоуровневых систем (матчи, роли, игровые объекты)
        GameSystems.bootstrap();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        com.example.dbdcore.commands.DbdCommands.register(event);
    }
}

