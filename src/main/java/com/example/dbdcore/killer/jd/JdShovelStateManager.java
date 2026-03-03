package com.example.dbdcore.killer.jd;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * Состояние лопаты Жирной Директрисы (JD) для каждого мана.
 * Управляет зарядкой броска, кулдауном и наличием уникального предмета.
 */
public class JdShovelStateManager {

    public static final String JD_SHOVEL_TAG = "dbdcore_jd_shovel";
    public static final String JD_BARRIER_TAG = "dbdcore_jd_barrier";

    private static final class State {
        int chargeTicks;
        int cooldownTicks;
        boolean charging;
        boolean hasShovelInWorld; // true после броска до возврата
        boolean readyToThrow;
    }

    private final Map<UUID, State> states = new HashMap<>();
    private final Set<UUID> shovelInSurvivors = new HashSet<>();

    public void clear() {
        states.clear();
        shovelInSurvivors.clear();
    }

    private State state(UUID id) {
        return states.computeIfAbsent(id, k -> new State());
    }

    public void ensureShovelInHand(ServerPlayer killer) {
        State s = state(killer.getUUID());
        if (s.cooldownTicks > 0) {
            ensureBarrierInHand(killer, s);
        } else {
            ensureUniqueShovelInHand(killer);
        }
    }

    private void ensureUniqueShovelInHand(ServerPlayer killer) {
        ItemStack main = killer.getMainHandItem();
        if (isJdShovel(main)) {
            removeDuplicates(killer, false);
            return;
        }
        removeDuplicates(killer, true);
        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        shovel.setHoverName(net.minecraft.network.chat.Component.literal("Орбитальная лопата JD"));
        shovel.getOrCreateTag().putBoolean(JD_SHOVEL_TAG, true);
        killer.setItemInHand(InteractionHand.MAIN_HAND, shovel);
    }

    private void ensureBarrierInHand(ServerPlayer killer, State s) {
        ItemStack main = killer.getMainHandItem();
        if (!isJdBarrier(main)) {
            removeDuplicates(killer, true);
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.getOrCreateTag().putBoolean(JD_BARRIER_TAG, true);
            killer.setItemInHand(InteractionHand.MAIN_HAND, barrier);
        }
        // Обновляем имя барьера по КД.
        int seconds = Math.max(0, s.cooldownTicks / 20);
        main = killer.getMainHandItem();
        if (isJdBarrier(main)) {
            main.setHoverName(net.minecraft.network.chat.Component.literal("КД: " + seconds));
        }
    }

    private void removeDuplicates(ServerPlayer killer, boolean keepMain) {
        for (int i = 0; i < killer.getInventory().getContainerSize(); i++) {
            ItemStack stack = killer.getInventory().getItem(i);
            if (isJdShovel(stack) || isJdBarrier(stack)) {
                if (keepMain && i == killer.getInventory().selected) {
                    continue;
                }
                killer.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    public static boolean isJdShovel(ItemStack stack) {
        return stack != null && stack.getItem() == Items.IRON_SHOVEL
                && stack.hasTag() && stack.getTag().getBoolean(JD_SHOVEL_TAG);
    }

    public static boolean isJdBarrier(ItemStack stack) {
        return stack != null && stack.getItem() == Items.BARRIER
                && stack.hasTag() && stack.getTag().getBoolean(JD_BARRIER_TAG);
    }

    public void startCharge(ServerPlayer killer) {
        State s = state(killer.getUUID());
        if (s.cooldownTicks > 0 || s.charging || s.readyToThrow) {
            return;
        }
        s.charging = true;
        s.chargeTicks = 40; // 2 секунды
        s.readyToThrow = false;
    }

    public boolean isCharging(ServerPlayer killer) {
        return state(killer.getUUID()).charging;
    }

    public int getCooldownTicks(ServerPlayer killer) {
        return state(killer.getUUID()).cooldownTicks;
    }

    public void tick(ServerLevel level, ServerPlayer killer) {
        State s = state(killer.getUUID());
        if (s.charging) {
            if (s.chargeTicks > 0) {
                s.chargeTicks--;
            }
            if (s.chargeTicks <= 0) {
                s.charging = false;
                s.readyToThrow = true;
            }
        }
        if (s.cooldownTicks > 0) {
            s.cooldownTicks--;
        }
        ensureShovelInHand(killer);
    }

    public boolean canThrow(ServerPlayer killer) {
        State s = state(killer.getUUID());
        return s.readyToThrow && s.cooldownTicks == 0 && isJdShovel(killer.getMainHandItem());
    }

    public void onThrow(ServerPlayer killer) {
        State s = state(killer.getUUID());
        s.cooldownTicks = 30 * 20;
        s.charging = false;
        s.chargeTicks = 0;
        s.readyToThrow = false;
        s.hasShovelInWorld = true;
        // Заменяем лопату на барьер: tick() сам подставит барьер в руку.
        killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    public void onShovelReturned(ServerPlayer killer) {
        State s = state(killer.getUUID());
        s.hasShovelInWorld = false;
        shovelInSurvivors.clear();
        if (s.cooldownTicks <= 0) {
            ensureUniqueShovelInHand(killer);
        }
    }

    public void markShovelInSurvivor(ServerPlayer survivor) {
        shovelInSurvivors.add(survivor.getUUID());
    }

    public boolean hasShovelInSurvivor(ServerPlayer survivor) {
        return shovelInSurvivors.contains(survivor.getUUID());
    }
}

