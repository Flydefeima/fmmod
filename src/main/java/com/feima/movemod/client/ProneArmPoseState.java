package com.feima.movemod.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 趴下 / 飞扑手臂姿态的权重状态。
 *
 * <p>两种姿态各持一条权重，互不干扰：
 * <ul>
 *   <li>{@link #DIVE_WEIGHT}  —— 飞扑（含上下摆动）</li>
 *   <li>{@link #PRONE_WEIGHT} —— 趴下</li>
 * </ul>
 *
 * <p>抽出来独立成类，是为了让 {@link com.feima.movemod.event.PlayerCleanupHandler}
 * 与 {@code ClientPlayerCleanupHandler} 能在玩家退出 / 重生 / 切换维度时
 * 清掉条目，避免长跑服务器里 mixin 内的静态 Map 持续累积不同玩家的 UUID。
 *
 * <p>本类不引用任何客户端 API，因此服务端加载也不会崩。
 */
public final class ProneArmPoseState {

    private ProneArmPoseState() {}

    private static final Map<UUID, Float> DIVE_WEIGHT  = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> PRONE_WEIGHT = new ConcurrentHashMap<>();

    public static float approachDive(UUID id, float target, float alpha) {
        return approach(DIVE_WEIGHT, id, target, alpha);
    }

    public static float approachProne(UUID id, float target, float alpha) {
        return approach(PRONE_WEIGHT, id, target, alpha);
    }

    public static void forget(UUID id) {
        DIVE_WEIGHT.remove(id);
        PRONE_WEIGHT.remove(id);
    }

    private static float approach(Map<UUID, Float> map, UUID id, float target, float alpha) {
        Float boxed = map.get(id);
        float cur = boxed != null ? boxed : 0F;
        float next = cur + (target - cur) * alpha;
        if (Math.abs(target - next) < 1.0E-3F) next = target;
        map.put(id, next);
        return next;
    }
}