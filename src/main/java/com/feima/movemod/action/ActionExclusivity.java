package com.feima.movemod.action;

import net.minecraft.world.entity.player.Player;

/**
 * 动作互斥关系的<b>唯一</b>真相来源。
 *
 * <p><b>为什么需要本类</b>：滑铲 / 飞扑 / 趴下 / 探头四者两两互斥。
 * 在引入本类之前，这段互斥逻辑分散在至少六处
 * （{@code SlideAction.canStart}、{@code DiveAction.canDive}、
 * {@code ProneAction.canStart}、{@code ProneAction.forcePose}、
 * {@code PeekAction.canPeek}，以及四个 {@code applyRemoteState} 中的
 * {@code stop} 调用），任何新增动作都必须同步修改所有位置，漏一处
 * 就会产生「两个动作同时激活」的脏状态。
 *
 * <p><b>职责</b>：本类只回答两个问题——
 * <ul>
 *   <li>「除我以外，是否有其它动作正在激活？」（用于启动前的门槛检查）</li>
 *   <li>「停止除我以外的所有动作。」（用于权威状态切换时清场）</li>
 * </ul>
 *
 * <p>互斥的<b>具体判定</b>仍由各 action 类的 {@code isXxx} 提供，
 * 本类只是把它们汇总，不做额外判断，也不持有状态。
 *
 * <p><b>扩展示例</b>：新增动作 {@code WallRun} 时，只需在
 * {@link Action} 枚举里加一项、在下面两个 switch 里各加一行，即可
 * 让所有已有动作自动与新动作互斥。
 */
public final class ActionExclusivity {

    private ActionExclusivity() {}

    public enum Action {
        SLIDE,
        DIVE,
        PRONE,
        PEEK
    }

    /**
     * 除 {@code self} 外是否有任何动作正在激活。
     *
     * <p>各分支列出的动作组合必须与「该动作不应与谁共存」的语义一致。
     * 当前所有动作两两互斥，因此每个分支列出的是另外三个。
     * 若将来出现「A 与 B 可共存、但都不与 C 共存」这类偏序关系，
     * 应改为显式的冲突矩阵。
     */
    public static boolean isAnyOtherActive(Player p, Action self) {
        return switch (self) {
            case SLIDE -> ProneAction.INSTANCE.isProne(p)
                       || PeekAction.INSTANCE.isPeeking(p)
                       || DiveAction.INSTANCE.isDiving(p);
            case DIVE  -> SlideAction.INSTANCE.isSliding(p)
                       || PeekAction.INSTANCE.isPeeking(p)
                       || ProneAction.INSTANCE.isProne(p);
            case PRONE -> SlideAction.INSTANCE.isSliding(p)
                       || PeekAction.INSTANCE.isPeeking(p)
                       || DiveAction.INSTANCE.isDiving(p);
            case PEEK  -> SlideAction.INSTANCE.isSliding(p)
                       || ProneAction.INSTANCE.isProne(p)
                       || DiveAction.INSTANCE.isDiving(p);
        };
    }

    /**
     * 停止除 {@code self} 外的所有动作。
     *
     * <p>{@code stop} 对双端 Map 都执行 remove，由调用方自行保证只在
     * 合适的端（服务端权威切换 / 客户端远端同步 / 客户端本地预测被拒）
     * 调用。
     */
    public static void stopOthers(Player p, Action self) {
        if (self != Action.SLIDE) SlideAction.INSTANCE.stop(p);
        if (self != Action.DIVE)  DiveAction.INSTANCE.stop(p);
        if (self != Action.PRONE) ProneAction.INSTANCE.stop(p);
        if (self != Action.PEEK)  PeekAction.INSTANCE.stop(p);
    }
}