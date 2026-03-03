package com.example.dbdcore;

import com.example.dbdcore.audio.AmbientSoundManager;
import com.example.dbdcore.audio.SurvivorSoundManager;
import com.example.dbdcore.character.PlayerLoadoutManager;
import com.example.dbdcore.game.health.HealthManager;
import com.example.dbdcore.game.match.MatchManager;
import com.example.dbdcore.game.movement.BurstManager;
import com.example.dbdcore.game.movement.StunManager;
import com.example.dbdcore.game.role.RoleManager;
import com.example.dbdcore.killer.jd.JdShovelProjectileManager;
import com.example.dbdcore.killer.jd.JdShovelStateManager;
import com.example.dbdcore.killer.joe.JoePotionManager;
import com.example.dbdcore.killer.joe.JoeStateManager;
import com.example.dbdcore.killer.joe.JoeTokenManager;
import com.example.dbdcore.killer.kazak.KazakStateManager;
import com.example.dbdcore.killer.shelkun.ShelkunStateManager;
import com.example.dbdcore.tracking.BloodTrailManager;
import com.example.dbdcore.tracking.ScratchMarkManager;
import com.example.dbdcore.ui.MatchBossBarManager;
import com.example.dbdcore.vision.AuraManager;
import com.example.dbdcore.world.ExitGateManager;
import com.example.dbdcore.world.GeneratorManager;

/**
 * Центральная точка инициализации всех подсистем DBD‑режима.
 */
public final class GameSystems {

    private static MatchManager matchManager;
    private static RoleManager roleManager;
    private static GeneratorManager generatorManager;
    private static ExitGateManager exitGateManager;
    private static HealthManager healthManager;
    private static BurstManager burstManager;
    private static StunManager stunManager;
    private static ScratchMarkManager scratchMarkManager;
    private static BloodTrailManager bloodTrailManager;
    private static SurvivorSoundManager survivorSoundManager;
    private static PlayerLoadoutManager loadoutManager;
    private static MatchBossBarManager bossBarManager;
    private static AmbientSoundManager ambientSoundManager;
    private static AuraManager auraManager;
    private static JdShovelStateManager jdShovelStateManager;
    private static JdShovelProjectileManager jdProjectileManager;
    private static JoeTokenManager joeTokenManager;
    private static JoeStateManager joeStateManager;
    private static JoePotionManager joePotionManager;
    private static ShelkunStateManager shelkunStateManager;
    private static KazakStateManager kazakStateManager;

    private GameSystems() {
    }

    public static void bootstrap() {
        roleManager = new RoleManager();
        generatorManager = new GeneratorManager();
        exitGateManager = new ExitGateManager();
        healthManager = new HealthManager();
        burstManager = new BurstManager();
        stunManager = new StunManager();
        scratchMarkManager = new ScratchMarkManager();
        bloodTrailManager = new BloodTrailManager();
        survivorSoundManager = new SurvivorSoundManager();
        loadoutManager = new PlayerLoadoutManager();
        bossBarManager = new MatchBossBarManager();
        ambientSoundManager = new AmbientSoundManager();
        auraManager = new AuraManager();
        jdShovelStateManager = new JdShovelStateManager();
        jdProjectileManager = new JdShovelProjectileManager();
        joeTokenManager = new JoeTokenManager();
        joeStateManager = new JoeStateManager();
        joePotionManager = new JoePotionManager();
        shelkunStateManager = new ShelkunStateManager();
        kazakStateManager = new KazakStateManager();
        matchManager = new MatchManager(roleManager, generatorManager, exitGateManager);
    }

    public static MatchManager match() {
        return matchManager;
    }

    public static RoleManager roles() {
        return roleManager;
    }

    public static GeneratorManager generators() {
        return generatorManager;
    }

    public static ExitGateManager exitGates() {
        return exitGateManager;
    }

    public static HealthManager health() {
        return healthManager;
    }

    public static BurstManager burst() {
        return burstManager;
    }

    public static StunManager stuns() {
        return stunManager;
    }

    public static ScratchMarkManager scratches() {
        return scratchMarkManager;
    }

    public static BloodTrailManager blood() {
        return bloodTrailManager;
    }

    public static SurvivorSoundManager survivorSounds() {
        return survivorSoundManager;
    }

    public static PlayerLoadoutManager loadouts() {
        return loadoutManager;
    }

    public static MatchBossBarManager bossbar() {
        return bossBarManager;
    }

    public static AmbientSoundManager ambient() {
        return ambientSoundManager;
    }

    public static AuraManager auras() {
        return auraManager;
    }

    public static JdShovelStateManager jdShovel() {
        return jdShovelStateManager;
    }

    public static JdShovelProjectileManager jdProjectiles() {
        return jdProjectileManager;
    }

    public static JoeTokenManager joeTokens() {
        return joeTokenManager;
    }

    public static JoeStateManager joeState() {
        return joeStateManager;
    }

    public static JoePotionManager joePotions() {
        return joePotionManager;
    }

    public static ShelkunStateManager shelkunState() {
        return shelkunStateManager;
    }

    public static KazakStateManager kazak() {
        return kazakStateManager;
    }
}

