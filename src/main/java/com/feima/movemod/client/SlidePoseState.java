package com.feima.movemod.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滑铲姿态渲染状态。
 *
 * <p>{@link com.feima.movemod.mixin.SlidePartPoseMixin} 需要记录
 * 「上一帧该玩家是否处于滑铲动画中」，以便在滑铲结束的那一帧把
 * ModelPart 字段重置为 idle 值——否则 PlayerAnimator 会把滑铲残留值
 * 当作下一帧的输入继续传递，导致姿态永久残留。
 *
 * <p>历史上这个集合直接以 {@code @Unique} 字段挂在
 * {@code SlidePartPoseMixin} 上，但那样会在玩家于滑铲中退出时
 * 永久累积 UUID（render 不再被调用，状态永不清除）。抽成独立静态类后，
 * {@link com.feima.movemod.event.ClientPlayerCleanupHandler} 可以在
 * {@code EntityLeaveLevelEvent} 触发时清掉条目。
 *
 * <p>本类不引用任何客户端 API，因此服务端加载也不会崩。
 */
public final class SlidePoseState {

    private SlidePoseState() {}

    private static final Set<UUID> WAS_SLIDING = ConcurrentHashMap.newKeySet();

    /** 标记该玩家当前处于滑铲渲染帧中。幂等。 */
    public static void mark(UUID id) {
        WAS_SLIDING.add(id);
    }

    /**
     * 取消标记。
     *
     * @return true 表示该玩家此前确实处于滑铲状态，调用方需要执行
     *         一次性姿态重置；false 表示无需重置。
     */
    public static boolean unmark(UUID id) {
        return WAS_SLIDING.remove(id);
    }

    /** 强制清理该玩家的一切状态（退出 / 切维度 / 重生）。 */
    public static void forget(UUID id) {
        WAS_SLIDING.remove(id);
    }
}