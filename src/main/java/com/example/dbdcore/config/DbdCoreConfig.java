package com.example.dbdcore.config;

import com.example.dbdcore.DbDCoreMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Корневая точка конфигурации.
 * Все числовые значения читаются только отсюда.
 */
public final class DbdCoreConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();
    }

    private DbdCoreConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, DbDCoreMod.MOD_ID + "-common.toml");
    }

    public static final class Common {
        public final ForgeConfigSpec.IntValue maxSurvivors;
        public final ForgeConfigSpec.IntValue maxKillers;
        public final ForgeConfigSpec.IntValue matchStartDelaySeconds;

        public final ForgeConfigSpec.IntValue totalGeneratorPositions;
        public final ForgeConfigSpec.IntValue generatorsToSpawn;
        public final ForgeConfigSpec.IntValue generatorsToRepairForEscape;

        public final ForgeConfigSpec.DoubleValue survivorBaseMoveSpeed;
        public final ForgeConfigSpec.DoubleValue killerBaseMoveSpeed;

        public final ForgeConfigSpec.IntValue generatorRepairTime1;
        public final ForgeConfigSpec.IntValue generatorRepairTime2;
        public final ForgeConfigSpec.IntValue generatorRepairTime3;
        public final ForgeConfigSpec.IntValue generatorRepairTime4;

        public final ForgeConfigSpec.IntValue generatorBlockDurationTicks;
        public final ForgeConfigSpec.DoubleValue generatorRegressionPerSecond;

        public final ForgeConfigSpec.IntValue exitGateOpenTimeSeconds;

        public final ForgeConfigSpec.IntValue dbdBurstDurationTicks;
        public final ForgeConfigSpec.DoubleValue dbdBurstSpeedMultiplier;
        public final ForgeConfigSpec.IntValue dbdBurstCooldownTicks;

        public final ForgeConfigSpec.IntValue killerMissStunDurationTicks;
        public final ForgeConfigSpec.IntValue killerPalletStunDurationTicks;

        public final ForgeConfigSpec.IntValue joePotionCooldownTicks;
        public final ForgeConfigSpec.IntValue joePotionMaxDistance;
        public final ForgeConfigSpec.DoubleValue joePotionRadius;
        public final ForgeConfigSpec.IntValue joeTokenSlowDurationTicks;
        public final ForgeConfigSpec.IntValue joeMissStunTicks;
        public final ForgeConfigSpec.IntValue joeHitStunTicks;
        public final ForgeConfigSpec.DoubleValue joeVoidTerrorRadius;
        public final ForgeConfigSpec.DoubleValue joeAoeExitRadius;
        public final ForgeConfigSpec.IntValue joeWorldBreakerDurationTicks;
        public final ForgeConfigSpec.IntValue joeCannotUsePotionAfterExitTicks;
        public final ForgeConfigSpec.IntValue joeExitRootDurationTicks;

        public final ForgeConfigSpec.DoubleValue shelkunMovementThreshold;
        public final ForgeConfigSpec.IntValue shelkunPressureWindowTicks;
        public final ForgeConfigSpec.IntValue shelkunPressureCooldownTicks;

        public final ForgeConfigSpec.IntValue kazakTrapLifetimeTicks;
        public final ForgeConfigSpec.IntValue kazakTrapRefreshIntervalTicks;
        public final ForgeConfigSpec.IntValue kazakVomitMaxChargeTicks;
        public final ForgeConfigSpec.IntValue kazakVomitMaxPowerTicks;
        public final ForgeConfigSpec.IntValue kazakTeleportCooldownTicks;

        public final ForgeConfigSpec.IntValue scratchLifetimeSeconds;
        public final ForgeConfigSpec.IntValue bloodLifetimeSeconds;

        public final ForgeConfigSpec.DoubleValue survivorFootstepHearRadius;
        public final ForgeConfigSpec.DoubleValue survivorInjuredGroanHearRadius;
        public final ForgeConfigSpec.DoubleValue survivorBreathHearRadius;

        public final ForgeConfigSpec.IntValue endgameCollapseDurationSeconds;

        public Common(ForgeConfigSpec.Builder b) {
            b.push("roles");
            maxSurvivors = b.comment("Максимум выживших в матче")
                    .defineInRange("maxSurvivors", 4, 1, 4);
            maxKillers = b.comment("Максимум маньяков в матче (DBD = 1)")
                    .defineInRange("maxKillers", 1, 1, 1);
            matchStartDelaySeconds = b.comment("Задержка перед автоматическим стартом матча после выбора ролей, секунд")
                    .defineInRange("matchStartDelaySeconds", 10, 0, 300);
            b.pop();

            b.push("generators");
            totalGeneratorPositions = b.comment("Всего возможных позиций генераторов на карте")
                    .defineInRange("totalGeneratorPositions", 10, 1, 64);
            generatorsToSpawn = b.comment("Сколько генераторов спауним в начале")
                    .defineInRange("generatorsToSpawn", 8, 1, 64);
            generatorsToRepairForEscape = b.comment("Сколько генераторов нужно починить для активации ворот")
                    .defineInRange("generatorsToRepairForEscape", 5, 1, 64);

            generatorRepairTime1 = b.comment("Время починки 1 сурвом, секунд")
                    .defineInRange("repairTime1", 90, 1, 600);
            generatorRepairTime2 = b.comment("Время починки 2 сурвами, секунд")
                    .defineInRange("repairTime2", 75, 1, 600);
            generatorRepairTime3 = b.comment("Время починки 3 сурвами, секунд")
                    .defineInRange("repairTime3", 60, 1, 600);
            generatorRepairTime4 = b.comment("Время починки 4 сурвами, секунд")
                    .defineInRange("repairTime4", 45, 1, 600);

            generatorBlockDurationTicks = b.comment("Длительность блокировки генератора маньяком, тики")
                    .defineInRange("blockDurationTicks", 20 * 30, 0, 20 * 600);
            generatorRegressionPerSecond = b.comment("Скорость регресса генератора в секунду при сбивании прогресса, %")
                    .defineInRange("regressionPerSecond", 25.0D, 0.0D, 100.0D);
            b.pop();

            b.push("exit_gates");
            exitGateOpenTimeSeconds = b.comment("Время открытия ворот, секунд")
                    .defineInRange("openTimeSeconds", 20, 1, 120);
            b.pop();

            b.push("movement");
            survivorBaseMoveSpeed = b.comment("Базовая скорость сурва")
                    .defineInRange("survivorBaseMoveSpeed", 0.1D, 0.01D, 1.0D);
            killerBaseMoveSpeed = b.comment("Базовая скорость маньяка")
                    .defineInRange("killerBaseMoveSpeed", 0.12D, 0.01D, 1.0D);
            b.pop();

            b.push("dbd_burst");
            dbdBurstDurationTicks = b.comment("Длительность ускорения после урона, тики")
                    .defineInRange("durationTicks", 20 * 3, 0, 20 * 20);
            dbdBurstSpeedMultiplier = b.comment("Множитель скорости при ускорении после урона")
                    .defineInRange("speedMultiplier", 1.5D, 1.0D, 3.0D);
            b.pop();

            b.push("stun");
            killerMissStunDurationTicks = b.comment("Длительность стана мана за промах, тики")
                    .defineInRange("killerMissStunDurationTicks", 20 * 2, 0, 20 * 10);
            killerPalletStunDurationTicks = b.comment("Длительность стана мана за палету, тики")
                    .defineInRange("killerPalletStunDurationTicks", 20 * 3, 0, 20 * 10);
            b.pop();

            b.push("tracking");
            scratchLifetimeSeconds = b.comment("Длительность жизни следов (Scratch Marks), секунд")
                    .defineInRange("scratchLifetimeSeconds", 8, 1, 60);
            bloodLifetimeSeconds = b.comment("Длительность жизни следов крови, секунд")
                    .defineInRange("bloodLifetimeSeconds", 5, 1, 60);
            b.pop();

            b.push("audio");
            survivorFootstepHearRadius = b.comment("Радиус, в котором ман слышит шаги сурва")
                    .defineInRange("survivorFootstepHearRadius", 20.0D, 1.0D, 64.0D);
            survivorInjuredGroanHearRadius = b.comment("Радиус, в котором ман слышит стоны раненого сурва")
                    .defineInRange("survivorInjuredGroanHearRadius", 28.0D, 1.0D, 64.0D);
            survivorBreathHearRadius = b.comment("Радиус, в котором ман слышит дыхание сурва")
                    .defineInRange("survivorBreathHearRadius", 12.0D, 1.0D, 64.0D);
            b.pop();

            b.push("endgame");
            endgameCollapseDurationSeconds = b.comment("Длительность коллапса после активации ворот, секунд")
                    .defineInRange("collapseDurationSeconds", 180, 10, 600);
            b.pop();

            b.push("dbd_burst_cooldown");
            dbdBurstCooldownTicks = b.comment("Кулдаун между ускорениями после урона для одного сурва, тики")
                    .defineInRange("dbdBurstCooldownTicks", 20 * 10, 0, 20 * 60);
            b.pop();

            b.push("joe");
            joePotionCooldownTicks = b.comment("Кулдаун зелья Джо (5 сек)")
                    .defineInRange("joePotionCooldownTicks", 20 * 5, 0, 20 * 60);
            joePotionMaxDistance = b.comment("Макс. дистанция прицеливания зелья, блоки")
                    .defineInRange("joePotionMaxDistance", 16, 1, 64);
            joePotionRadius = b.comment("Радиус попадания зелья, блоки")
                    .defineInRange("joePotionRadius", 3.0D, 0.5D, 16.0D);
            joeTokenSlowDurationTicks = b.comment("Длительность замедления сурва от 1 жетона, тики (5 сек)")
                    .defineInRange("joeTokenSlowDurationTicks", 20 * 5, 0, 20 * 60);
            joeMissStunTicks = b.comment("Замедление Джо за промах зелья, тики")
                    .defineInRange("joeMissStunTicks", 20 * 2, 0, 20 * 10);
            joeHitStunTicks = b.comment("Замедление Джо при попадании (меньше), тики")
                    .defineInRange("joeHitStunTicks", 20, 0, 20 * 5);
            joeVoidTerrorRadius = b.comment("Радиус терора в Изнанке, блоки")
                    .defineInRange("joeVoidTerrorRadius", 8.0D, 1.0D, 32.0D);
            joeAoeExitRadius = b.comment("Радиус АОЕ при выходе из Изнанки, блоки")
                    .defineInRange("joeAoeExitRadius", 10.0D, 1.0D, 32.0D);
            joeWorldBreakerDurationTicks = b.comment("Длительность фазы Разрушитель миров, тики (1 мин)")
                    .defineInRange("joeWorldBreakerDurationTicks", 20 * 60, 0, 20 * 300);
            joeCannotUsePotionAfterExitTicks = b.comment("Нельзя юзать зелье после выхода из Изнанки, тики")
                    .defineInRange("joeCannotUsePotionAfterExitTicks", 20 * 5, 0, 20 * 30);
            joeExitRootDurationTicks = b.comment("Джо «стоит на месте» после выхода из Изнанки (нерф), тики (5 сек)")
                    .defineInRange("joeExitRootDurationTicks", 20 * 5, 0, 20 * 15);
            b.pop();

            b.push("shelkun");
            shelkunMovementThreshold = b.comment("Порог движения Шелкунчика (deltaX/deltaZ), блоки — меньше = стояние")
                    .defineInRange("shelkunMovementThreshold", 0.01D, 0.001D, 0.5D);
            shelkunPressureWindowTicks = b.comment("Окно ваншота «давление» после начала движения, тики (3 сек)")
                    .defineInRange("shelkunPressureWindowTicks", 20 * 3, 0, 20 * 10);
            shelkunPressureCooldownTicks = b.comment("Перезарядка ваншота после срабатывания, тики (3 сек)")
                    .defineInRange("shelkunPressureCooldownTicks", 20 * 3, 0, 20 * 10);
            b.pop();

            b.push("kazak");
            kazakTrapLifetimeTicks = b.comment("Время жизни трапа Казака, тики")
                    .defineInRange("kazakTrapLifetimeTicks", 20 * 60, 20 * 5, 20 * 300);
            kazakTrapRefreshIntervalTicks = b.comment("Интервал обновления ауры/эффектов трапа, тики (5 сек)")
                    .defineInRange("kazakTrapRefreshIntervalTicks", 20 * 5, 20, 20 * 20);
            kazakVomitMaxChargeTicks = b.comment("Максимальное время зарядки блевоты, тики (20 сек)")
                    .defineInRange("kazakVomitMaxChargeTicks", 20 * 20, 20, 20 * 40);
            kazakVomitMaxPowerTicks = b.comment("Время, до которого растёт сила блевоты, тики (10 сек)")
                    .defineInRange("kazakVomitMaxPowerTicks", 20 * 10, 20, 20 * 30);
            kazakTeleportCooldownTicks = b.comment("Кулдаун ТП на люк, тики (40 сек)")
                    .defineInRange("kazakTeleportCooldownTicks", 20 * 40, 20 * 5, 20 * 120);
            b.pop();
        }
    }
}

