package com.feima.movemod.action;

import com.feima.movemod.config.MoveConfig;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 耐力条：双端各自维护一份，服务端权威。
 *
 * 耐力 → 档位的映射：
 *   比例 ≥ level1Threshold  → level 0（一档，motion.startSpeed）
 *   比例 ≥ level2Threshold  → level 1（二档，level2Speed）
 *   否则                    → level 2（三档，level3Speed）
 *
 * 同步模型：
 *   1. 客户端预测启动滑铲时本地 consume 一次，用本地耐力决定档位
 *   2. 服务端接受时也 consume 一次，并把权威耐力 + 实际使用的初速广播
 *   3. 客户端收到包后无条件覆盖本地耐力和（滑铲早期的）初速
 *      → 任何预测偏差立刻被修正
 *   4. 服务端拒绝时广播 {sliding=false, stamina=权威值}，客户端覆盖即回滚
 */
public final class StaminaTracker {

    public static final StaminaTracker INSTANCE = new StaminaTracker();

    /** 档位常量 */
    public static final int LEVEL_1 = 0;
    public static final int LEVEL_2 = 1;
    public static final int LEVEL_3 = 2;

    private final Map<UUID, Entry> serverData = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> clientData  = new ConcurrentHashMap<>();

    private StaminaTracker() {}

    private Map<UUID, Entry> data(Player player) {
        return player.level().isClientSide ? clientData : serverData;
    }

    public double getMax() {
        return MoveConfig.INSTANCE.staminaMax.get();
    }

    public double get(Player player) {
        Entry e = data(player).get(player.getUUID());
        return e == null ? getMax() : e.value;
    }

    public void set(Player player, double value) {
        Entry e = data(player).computeIfAbsent(player.getUUID(), k -> new Entry(getMax()));
        e.value = Math.max(0.0D, Math.min(getMax(), value));
    }

    /** 软消耗：能扣多少扣多少，返回实际扣除量。 */
    public double consumeUpTo(Player player, double amount) {
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return 0.0D;
        if (amount <= 0.0D) return 0.0D;

        Entry e = data(player).computeIfAbsent(player.getUUID(), k -> new Entry(getMax()));
        double taken = Math.min(e.value, amount);
        e.value -= taken;
        e.regenDelay = MoveConfig.INSTANCE.staminaRegenDelayTicks.get();
        return taken;
    }

    public void tick(Player player) {
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return;

        Entry e = data(player).get(player.getUUID());
        if (e == null) return;

        if (e.regenDelay > 0) {
            e.regenDelay--;
            return;
        }
        double max = getMax();
        if (e.value < max) {
            e.value = Math.min(max, e.value + MoveConfig.INSTANCE.staminaRegenPerTick.get());
        }
    }

    /**
     * 当前耐力档位：0 = 一档（最快），1 = 二档，2 = 三档（最慢）。
     * 耐力系统关闭时永远返回一档。
     */
    public int levelOf(Player player) {
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return LEVEL_1;

        double max = getMax();
        if (max <= 0.0D) return LEVEL_1;

        double ratio = get(player) / max;
        double l1 = MoveConfig.INSTANCE.staminaLevel1Threshold.get();
        double l2 = MoveConfig.INSTANCE.staminaLevel2Threshold.get();

        if (ratio >= l1) return LEVEL_1;
        if (ratio >= l2) return LEVEL_2;
        return LEVEL_3;
    }

    /** 当前档位对应的滑铲初速（方块/tick）。 */
    public double speedFor(Player player) {
        return switch (levelOf(player)) {
            case LEVEL_1 -> MoveConfig.INSTANCE.startSpeed.get();
            case LEVEL_2 -> MoveConfig.INSTANCE.staminaLevel2Speed.get();
            default      -> MoveConfig.INSTANCE.staminaLevel3Speed.get();
        };
    }

    /**
     * 滑铲跳惩罚倍率 = 当前档位速度 / 一档速度。
     * 一档 = 1.0，二档 = level2Speed/startSpeed，三档 = level3Speed/startSpeed。
     */
    public double jumpScaleFor(Player player) {
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return 1.0D;

        double base = MoveConfig.INSTANCE.startSpeed.get();
        if (base <= 0.0D) return 1.0D;

        return speedFor(player) / base;
    }

    /** 是否够耐力启动滑铲（≥ costOnStart）。 */
    public boolean canStart(Player player) {
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return true;
        return get(player) >= MoveConfig.INSTANCE.staminaCostOnStart.get();
    }

    public void forget(UUID id) {
        serverData.remove(id);
        clientData.remove(id);
    }

    private static final class Entry {
        double value;
        int regenDelay;
        Entry(double value) { this.value = value; }
    }
}