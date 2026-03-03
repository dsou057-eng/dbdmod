package com.example.dbdcore.killer.kazak;

import com.example.dbdcore.GameSystems;
import com.example.dbdcore.character.CharacterId;
import com.example.dbdcore.config.DbdCoreConfig;
import com.example.dbdcore.game.health.HealthState;
import com.example.dbdcore.game.match.GameState;
import com.example.dbdcore.game.role.PlayerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Казак: трапы, струя "зелья блевоты" и ТП на рандомный люк.
 * Только ванильные эффекты и изменение блоков.
 */
public class KazakStateManager {

    public static final String KAZAK_TRAP_ITEM_TAG = "dbdcore_kazak_trap_item";
    public static final String KAZAK_VOMIT_ITEM_TAG = "dbdcore_kazak_vomit_item";
    public static final String KAZAK_TP_ITEM_TAG = "dbdcore_kazak_tp_item";

    private static final BlockPos[] HATCH_POINTS = new BlockPos[]{
            new BlockPos(60, -41, 8),
            new BlockPos(9, -55, -30),
            new BlockPos(61, -52, -119),
            new BlockPos(33, -59, -80),
            new BlockPos(66, -39, -24)
    };

    private static final class Trap {
        BlockPos pos;
        BlockState original;
        int lifeTicks;
    }

    private static final class State {
        final List<Trap> traps = new ArrayList<>();
        final Map<UUID, Integer> ticksOnTrap = new HashMap<>();

        boolean vomitCharging;
        int vomitChargeTicks;
        boolean vomitActive;
        int vomitActiveTicks;
        final Map<UUID, Integer> vomitContactTicks = new HashMap<>();

        int teleportCooldownTicks;
    }

    private final Map<UUID, State> states = new HashMap<>();

    public void clear(ServerLevel level) {
        // Вернуть все трапы в исходное состояние.
        for (State s : states.values()) {
            for (Trap t : s.traps) {
                if (t.original != null) {
                    level.setBlockAndUpdate(t.pos, t.original);
                }
            }
        }
        states.clear();
    }

    private State state(UUID id) {
        return states.computeIfAbsent(id, k -> new State());
    }

    public static boolean isKazak(ServerPlayer player) {
        var loadout = GameSystems.loadouts().getLoadout(player);
        return loadout != null && loadout.characterId == CharacterId.KAZAK;
    }

    // === Предметы для способностей ===

    public static boolean isTrapItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.hasTag() && stack.getTag().getBoolean(KAZAK_TRAP_ITEM_TAG);
    }

    public static boolean isVomitItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.hasTag() && stack.getTag().getBoolean(KAZAK_VOMIT_ITEM_TAG);
    }

    public static boolean isTeleportItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.hasTag() && stack.getTag().getBoolean(KAZAK_TP_ITEM_TAG);
    }

    public void ensureItems(ServerPlayer kazak) {
        if (!isKazak(kazak)) {
            return;
        }
        boolean hasTrap = false;
        boolean hasVomit = false;
        boolean hasTp = false;
        for (int i = 0; i < kazak.getInventory().getContainerSize(); i++) {
            ItemStack st = kazak.getInventory().getItem(i);
            if (isTrapItem(st)) hasTrap = true;
            if (isVomitItem(st)) hasVomit = true;
            if (isTeleportItem(st)) hasTp = true;
        }
        if (!hasTrap) {
            ItemStack trap = new ItemStack(Items.SLIME_BALL);
            trap.setHoverName(net.minecraft.network.chat.Component.literal("Трап Казака"));
            trap.getOrCreateTag().putBoolean(KAZAK_TRAP_ITEM_TAG, true);
            kazak.getInventory().add(trap);
        }
        if (!hasVomit) {
            ItemStack vomit = new ItemStack(Items.SPLASH_POTION);
            vomit.setHoverName(net.minecraft.network.chat.Component.literal("Зелье блевоты"));
            vomit.getOrCreateTag().putBoolean(KAZAK_VOMIT_ITEM_TAG, true);
            kazak.setItemInHand(InteractionHand.MAIN_HAND, vomit);
        }
        if (!hasTp) {
            ItemStack tp = new ItemStack(Items.ENDER_PEARL);
            tp.setHoverName(net.minecraft.network.chat.Component.literal("Люк Казака"));
            tp.getOrCreateTag().putBoolean(KAZAK_TP_ITEM_TAG, true);
            kazak.getInventory().add(tp);
        }
    }

    // === Публичные действия от InteractionEvents ===

    /** Установка трапа под Казаком (до 10 штук). */
    public void placeTrap(ServerLevel level, ServerPlayer kazak) {
        if (!isKazak(kazak)) return;
        State s = state(kazak.getUUID());
        int maxTraps = MAX_TRAPS;
        if (s.traps.size() >= maxTraps) {
            return;
        }
        BlockPos pos = kazak.blockPosition().below();
        BlockState current = level.getBlockState(pos);
        if (current.is(Blocks.SLIME_BLOCK)) {
            // Уже трап или слайм — не дублируем.
            return;
        }
        Trap t = new Trap();
        t.pos = pos.immutable();
        t.original = current;
        t.lifeTicks = DbdCoreConfig.COMMON.kazakTrapLifetimeTicks.get();
        s.traps.add(t);
        level.setBlockAndUpdate(pos, Blocks.SLIME_BLOCK.defaultBlockState());
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** ПКМ по предмету блевоты: старт/завершение зарядки и запуск струи. */
    public void onVomitRightClick(ServerLevel level, ServerPlayer kazak) {
        if (!isKazak(kazak)) return;
        State s = state(kazak.getUUID());
        if (s.vomitActive) {
            return;
        }
        if (!s.vomitCharging) {
            // Начало зарядки.
            s.vomitCharging = true;
            s.vomitChargeTicks = 0;
            return;
        } else {
            // Отпускание ПКМ: старт струи.
            startVomit(level, kazak, s);
        }
    }

    /** ПКМ по предмету ТП: телепорт на рандомный люк с КД. */
    public void onTeleportRightClick(ServerLevel level, ServerPlayer kazak) {
        if (!isKazak(kazak)) return;
        State s = state(kazak.getUUID());
        if (s.teleportCooldownTicks > 0) {
            return;
        }
        BlockPos target = HATCH_POINTS[level.random.nextInt(HATCH_POINTS.length)];
        kazak.teleportTo(level,
                target.getX() + 0.5,
                target.getY(),
                target.getZ() + 0.5,
                kazak.getYRot(), kazak.getXRot());
        s.teleportCooldownTicks = DbdCoreConfig.COMMON.kazakTeleportCooldownTicks.get();
        level.playSound(null, kazak.getX(), kazak.getY(), kazak.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL, kazak.getX(), kazak.getY(), kazak.getZ(), 40, 0.5, 1.0, 0.5, 0.1);
    }

    // === Tick ===

    public void tick(ServerLevel level, ServerPlayer player) {
        if (!isKazak(player)) {
            return;
        }
        State s = state(player.getUUID());

        tickTraps(level, player, s);
        tickVomit(level, player, s);

        if (s.teleportCooldownTicks > 0) {
            s.teleportCooldownTicks--;
        }

        ensureItems(player);
        updateBossbar(level, player, s);
    }

    private void tickTraps(ServerLevel level, ServerPlayer kazak, State s) {
        // Аура трапов и срок жизни.
        int refreshInterval = DbdCoreConfig.COMMON.kazakTrapRefreshIntervalTicks.get();
        Iterator<Trap> it = s.traps.iterator();
        while (it.hasNext()) {
            Trap t = it.next();
            t.lifeTicks--;
            if (t.lifeTicks <= 0) {
                if (t.original != null) {
                    level.setBlockAndUpdate(t.pos, t.original);
                }
                it.remove();
                continue;
            }
            // Аура зелеными партиклами.
            if (t.lifeTicks % refreshInterval == 0) {
                level.sendParticles(ParticleTypes.ITEM_SLIME,
                        t.pos.getX() + 0.5, t.pos.getY() + 0.1, t.pos.getZ() + 0.5,
                        8, 0.3, 0.05, 0.3, 0.01);
            }
        }

        // Эффекты на сурвов, стоящих на трапе.
        for (ServerPlayer surv : level.players()) {
            if (GameSystems.roles().getRole(surv) != PlayerRole.SURVIVOR) {
                continue;
            }
            UUID id = surv.getUUID();
            boolean onTrap = false;
            BlockPos under = surv.blockPosition().below();
            for (Trap t : s.traps) {
                if (t.pos.equals(under)) {
                    onTrap = true;
                    break;
                }
            }
            int ticks = s.ticksOnTrap.getOrDefault(id, 0);
            if (onTrap) {
                ticks++;
                s.ticksOnTrap.put(id, ticks);
                if (ticks % refreshInterval == 0) {
                    int ampl = Math.min(2, ticks / refreshInterval);
                    int dur = refreshInterval + refreshInterval * ampl;
                    surv.addEffect(new MobEffectInstance(MobEffects.CONFUSION, dur, ampl, false, true, true));
                    surv.addEffect(new MobEffectInstance(MobEffects.POISON, dur, ampl, false, true, true));
                    level.sendParticles(ParticleTypes.ITEM_SLIME,
                            under.getX() + 0.5, under.getY() + 0.5, under.getZ() + 0.5,
                            10, 0.3, 0.3, 0.3, 0.02);
                }
            } else {
                if (ticks > 0) {
                    s.ticksOnTrap.remove(id);
                }
            }
        }
    }

    private void tickVomit(ServerLevel level, ServerPlayer kazak, State s) {
        if (s.vomitCharging) {
            int maxCharge = DbdCoreConfig.COMMON.kazakVomitMaxChargeTicks.get();
            if (s.vomitChargeTicks < maxCharge) {
                s.vomitChargeTicks++;
            }
            // Сильное замедление во время зарядки.
            kazak.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false, true));
        }

        if (s.vomitActive) {
            if (s.vomitActiveTicks > 0) {
                s.vomitActiveTicks--;
                tickVomitStream(level, kazak, s);
            } else {
                finishVomitEffects(level, s);
                s.vomitActive = false;
                s.vomitChargeTicks = 0;
                s.vomitContactTicks.clear();
            }
        }
    }

    private void startVomit(ServerLevel level, ServerPlayer kazak, State s) {
        int powerMax = DbdCoreConfig.COMMON.kazakVomitMaxPowerTicks.get();
        int powerTicks = Math.min(s.vomitChargeTicks, powerMax);
        if (powerTicks <= 0) {
            s.vomitCharging = false;
            s.vomitChargeTicks = 0;
            return;
        }
        s.vomitCharging = false;
        s.vomitActive = true;
        // Время активной струи пропорционально заряду (до 10 сек).
        int seconds = Math.max(1, powerTicks / 20);
        s.vomitActiveTicks = seconds * VOMIT_ACTIVE_TICKS_PER_SECOND;
        s.vomitContactTicks.clear();
        level.playSound(null, kazak.getX(), kazak.getY(), kazak.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    private void tickVomitStream(ServerLevel level, ServerPlayer kazak, State s) {
        Vec3 eye = kazak.getEyePosition();
        Vec3 look = kazak.getLookAngle().normalize();
        double range = 12.0D;
        Vec3 end = eye.add(look.scale(range));
        Vec3 ab = end.subtract(eye);
        double abLenSq = ab.lengthSqr();
        double radius = 1.0D;
        double radiusSq = radius * radius;

        // Частицы зелёной струи.
        int steps = 16;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            Vec3 p = eye.add(ab.scale(t));
            level.sendParticles(ParticleTypes.ITEM_SLIME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }

        // Контакт с сурвами.
        for (ServerPlayer target : level.players()) {
            if (GameSystems.roles().getRole(target) != PlayerRole.SURVIVOR) {
                continue;
            }
            Vec3 ap = target.position().subtract(eye);
            double t = ap.dot(ab) / abLenSq;
            if (t < 0.0D || t > 1.0D) {
                continue;
            }
            Vec3 closest = eye.add(ab.scale(t));
            double distSq = target.position().distanceToSqr(closest);
            if (distSq > radiusSq) {
                continue;
            }
            UUID id = target.getUUID();
            s.vomitContactTicks.merge(id, 1, Integer::sum);
            // Лёгкий звук при попадании.
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.4F, 1.2F);
        }
    }

    private void finishVomitEffects(ServerLevel level, State s) {
        for (Map.Entry<UUID, Integer> e : s.vomitContactTicks.entrySet()) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (target == null) continue;
            if (GameSystems.roles().getRole(target) != PlayerRole.SURVIVOR) {
                continue;
            }
            int contact = e.getValue();
            int duration = Math.min(20 * 12, 20 + contact * 4); // до ~12 сек
            target.addEffect(new MobEffectInstance(MobEffects.POISON, duration, 2, false, true, true)); // Отравление III
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1, false, true, true));
        }
    }

    // === Bossbar ===

    private void updateBossbar(ServerLevel level, ServerPlayer kazak, State s) {
        GameState gs = GameSystems.match().getGameState();
        if (gs != GameState.ACTIVE && gs != GameState.ENDGAME) {
            return;
        }
        int trapCount = s.traps.size();
        int tpSec = (s.teleportCooldownTicks + 19) / 20;
        String tpText = tpSec > 0 ? ("ТП КД " + tpSec + "с") : "ТП готов";
        int chargeSec = s.vomitCharging ? (s.vomitChargeTicks / 20) : 0;
        String vomitText;
        if (s.vomitActive) {
            vomitText = "Блевота: активно";
        } else if (s.vomitCharging) {
            vomitText = "Блевота: заряд " + chargeSec + "с";
        } else {
            vomitText = "Блевота: готово";
        }
        String line = "Трапов: " + trapCount + " | " + vomitText + " | " + tpText;
        GameSystems.bossbar().setKazakState(line);
    }
}

