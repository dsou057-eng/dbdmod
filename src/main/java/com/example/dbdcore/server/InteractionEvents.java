package com.example.dbdcore.server;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.world.GeneratorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Ответственен за взаимодействие игроков с кнопками, которые запускают починку генов.
 */
public class InteractionEvents {

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        // Запуск зарядки лопаты JD.
        if (com.example.dbdcore.killer.jd.JdShovelStateManager.isJdShovel(stack)) {
            if (GameSystems.jdShovel().getCooldownTicks(player) == 0) {
                GameSystems.jdShovel().startCharge(player);
            }
            // Бросок после зарядки.
            if (GameSystems.jdShovel().canThrow(player)) {
                GameSystems.jdShovel().onThrow(player);
                GameSystems.jdProjectiles().spawn(player);
                event.setCanceled(true);
            }
        }

        // Возврат лопаты после КД через барьер.
        if (com.example.dbdcore.killer.jd.JdShovelStateManager.isJdBarrier(stack)
                && GameSystems.jdShovel().getCooldownTicks(player) == 0) {
            GameSystems.jdShovel().onShovelReturned(player);
            event.setCanceled(true);
        }

        // Джо: зелье — только на земле, прицел по блоку до 16 блоков.
        if (com.example.dbdcore.killer.joe.JoeStateManager.isJoePotion(stack)
                && com.example.dbdcore.killer.joe.JoeStateManager.isJoe(player)) {
            if (!player.isOnGround()) {
                event.setCanceled(true);
                return;
            }
            if (GameSystems.joeState().canUsePotion(player)) {
                BlockHitResult blockHit = GameSystems.joeState().getTargetBlock(player, level);
                if (blockHit != null && blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    BlockPos targetBlock = blockHit.getBlockPos();
                    GameSystems.joePotions().scheduleImpacts(level, player, targetBlock);
                    GameSystems.joeState().onPotionThrown(player);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
                    event.setCanceled(true);
                }
            }
        }

        // Джо: возврат зелья по ПКМ барьером, когда КД истёк.
        if (com.example.dbdcore.killer.joe.JoeStateManager.isJoeBarrier(stack)
                && GameSystems.joeState().getPotionCooldownTicks(player) == 0) {
            GameSystems.joeState().onBarrierReturn(player);
            event.setCanceled(true);
        }

        // Джо: Осколок Изнанки — вход/выход из Изнанки.
        if (com.example.dbdcore.killer.joe.JoeStateManager.isJoeVoidShard(stack)
                && com.example.dbdcore.killer.joe.JoeStateManager.isJoe(player)) {
            if (GameSystems.joeState().isInVoid(player)) {
                GameSystems.joeState().exitVoid(player, level);
            } else {
                GameSystems.joeState().enterVoid(player, level);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            event.setCanceled(true);
        }

        // Казак: установка трапа под собой.
        if (com.example.dbdcore.killer.kazak.KazakStateManager.isTrapItem(stack)
                && com.example.dbdcore.killer.kazak.KazakStateManager.isKazak(player)) {
            GameSystems.kazak().placeTrap(level, player);
            event.setCanceled(true);
        }

        // Казак: зелье блевоты — старт/завершение зарядки и запуск струи.
        if (com.example.dbdcore.killer.kazak.KazakStateManager.isVomitItem(stack)
                && com.example.dbdcore.killer.kazak.KazakStateManager.isKazak(player)) {
            GameSystems.kazak().onVomitRightClick(level, player);
            event.setCanceled(true);
        }

        // Казак: ТП на рандомный люк (с КД).
        if (com.example.dbdcore.killer.kazak.KazakStateManager.isTeleportItem(stack)
                && com.example.dbdcore.killer.kazak.KazakStateManager.isKazak(player)) {
            GameSystems.kazak().onTeleportRightClick(level, player);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ButtonBlock)) {
            return;
        }

        // Находим ближайший генератор к кнопке.
        GeneratorManager.GeneratorInstance nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (GeneratorManager.GeneratorInstance gen : GameSystems.generators().all()) {
            double dx = gen.pos.getX() - pos.getX();
            double dy = gen.pos.getY() - pos.getY();
            double dz = gen.pos.getZ() - pos.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = gen;
            }
        }

        if (nearest == null) {
            return;
        }

        // Ограничиваем поиск радиусом, чтобы не хватать далекие гены.
        if (nearestDistSq > 16.0D) { // 4 блока
            return;
        }

        // Начинаем починку этого генератора данным игроком.
        GameSystems.generators().startInteraction(player, nearest.pos);

        // Блокируем стандартное поведение кнопки, чтобы не включать редстоун.
        event.setCanceled(true);
    }
}

