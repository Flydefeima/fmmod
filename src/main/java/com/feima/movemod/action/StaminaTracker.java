package com.feima.movemod.action;

import com.feima.movemod.config.MoveConfig;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 耐力系统：双端各自维护一份，服务端权威。
 *
 * <p>三种动作消耗耐力（趴下不消耗）：
 * <ul>
 *   <li><b>滑铲</b>：启动时一次性消耗 {@code stamina.slide.costOnStart}，
 *       期间每 tick 消耗 {@code stamina.slide.costPerTick}；</li>
 *   <li><b>飞扑</b>：启动时一次性消耗 {@code stamina.dive.costOnStart}，
 *       期间每 tick 消耗 {@code stamina.dive.costPerTick}；
 *       启动时若耐力不足则直接拒绝；</li>
 *   <li><b>探头</b>：持续消耗 {@code stamina.peek.costPerTick}，
 *       耗尽自动退出。</li>
 * </ul>
 *
 * <p>滑铲的耐力 → 档位映射：
 *   比例 ≥ level1Threshold  → level 0（一档，motion.startSpeed）
 *   比例 ≥ level2Threshold  → level 1（二档，level2Speed）
 *   否则                    → level 2（三档，level3Speed）
 *
 * <p>同步模型：
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

    /**
     * 通用检查：玩家当前耐力是否 ≥ {@code amount}。
     *
     * <p>耐力系统关闭、{@code amount ≤ 0} 时永远返回 true。
     * 用于动作启动前的门槛检查——例如飞扑启动前检查是否有
     * {@code stamina.dive.costOnStart} 的耐力。
     */
    public boolean hasEnough(Player player, double amount) {
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return true;
        if (amount <= 0.0D) return true;
        return get(player) >= amount;
    }

    /**
     * 软消耗：能扣多少扣多少，返回实际扣除量。
     *
     * <p>消耗后重置回充延迟计时器。用于「持续消耗」和
     * 「启动一次性消耗」两种场景，不区分是否够扣。
     */
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

    // ============================================================
    // 滑铲专属：档位与速度
    // ============================================================
    public int levelOf(Player player) {
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return LEVEL_1;

        double max = getMax();
        if (max <= 0.0D) return LEVEL_1;

        double ratio = get(player) / max;
        double l1 = MoveConfig.INSTANCE.staminaSlideLevel1Threshold.get();
        double l2 = MoveConfig.INSTANCE.staminaSlideLevel2Threshold.get();

        if (ratio >= l1) return LEVEL_1;
        if (ratio >= l2) return LEVEL_2;
        return LEVEL_3;
    }

    /** 当前档位对应的滑铲初速（方块/tick）。 */
    public double speedFor(Player player) {
        return switch (levelOf(player)) {
            case LEVEL_1 -> MoveConfig.INSTANCE.startSpeed.get();
            case LEVEL_2 -> MoveConfig.INSTANCE.staminaSlideLevel2Speed.get();
            default      -> MoveConfig.INSTANCE.staminaSlideLevel3Speed.get();
        };
    }

    /**
     * 滑铲跳惩罚倍率 = 当前档位速度 / 一档速度。
     */
    public double jumpScaleFor(Player player) {
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return 1.0D;

        double base = MoveConfig.INSTANCE.startSpeed.get();
        if (base <= 0.0D) return 1.0D;

        return speedFor(player) / base;
    }

    /** 滑铲专用：当前耐力是否足够启动一次滑铲。 */
    public boolean canStart(Player player) {
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return true;
        return get(player) >= MoveConfig.INSTANCE.staminaSlideCostOnStart.get();
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