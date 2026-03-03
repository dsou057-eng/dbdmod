package com.example.dbdcore.server;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.game.health.HealthManager;
import com.example.dbdcore.game.health.HealthState;
import com.example.dbdcore.game.match.MatchManager;
import com.example.dbdcore.game.role.PlayerRole;
import com.example.dbdcore.killer.jd.JdShovelStateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Общая боевая логика: неуязвимость мана, M1 у всех манов — 3 удара до дауна; ваншот по сурву с лопатой JD.
 */
public class CombatEvents {

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Все маны неуязвимы.
        PlayerRole role = GameSystems.roles().getRole(player);
        if (role == PlayerRole.KILLER) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        // Блокируем урон по ману ещё на уровне атаки, для надёжности.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerRole role = GameSystems.roles().getRole(player);
        if (role == PlayerRole.KILLER) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (!(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }

        PlayerRole attackerRole = GameSystems.roles().getRole(attacker);
        PlayerRole targetRole = GameSystems.roles().getRole(target);

        if (attackerRole == PlayerRole.KILLER && targetRole == PlayerRole.SURVIVOR) {
            HealthManager hm = GameSystems.health();
            // Ваншот только если в сурве застряла лопата JD.
            if (GameSystems.jdShovel().hasShovelInSurvivor(target)) {
                hm.setState(target, HealthState.DEAD);
            } else {
                hm.applyM1Hit(target); // M1: 3 удара до дауна
            }

            // Отменяем ванильный урон, у нас своя система здоровья.
            event.setCanceled(true);
        }

        // JD не может нанести урон себе, это уже обеспечено неуязвимостью в onLivingHurt/onLivingAttack.
    }
}

