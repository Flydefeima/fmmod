package com.feima.movemod.client;

import com.feima.movemod.FeimaMoveMod;
import com.feima.movemod.action.DiveAction;
import com.feima.movemod.action.PeekAction;
import com.feima.movemod.action.ProneAction;
import com.feima.movemod.action.SlideAction;
import com.feima.movemod.config.MoveConfig;
import com.feima.movemod.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FeimaMoveMod.MODID, value = Dist.CLIENT)
public final class ClientInputHandler {

    private ClientInputHandler() {}

    /** 上一 tick 的左右探头按键状态，用于检测按下边沿。 */
    private static boolean peekLeftPrev  = false;
    private static boolean peekRightPrev = false;

    /** 「后按下的探头键生效」模式下，最近一次按下的方向。 */
    private static PeekAction.Dir peekLastPressed = PeekAction.Dir.NONE;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        // ---- 滑铲键（C）----
        while (KeyBindings.SLIDE.consumeClick()) {
            if (player != null && SlideAction.INSTANCE.tryStartClient(player)) {
                NetworkHandler.sendSlide();
            }
        }

        // ---- 趴下 / 飞扑键（Z，上下文切换）----
        while (KeyBindings.PRONE.consumeClick()) {
            if (player == null) break;

            if (ProneAction.INSTANCE.isProne(player)) {
                ProneAction.INSTANCE.stopClient(player);
                NetworkHandler.sendProneSet(false);
                continue;
            }

            if (DiveAction.INSTANCE.isDiving(player)) {
                continue;
            }

            if (DiveAction.INSTANCE.canDive(player)
                    && DiveAction.INSTANCE.tryStartClient(player)) {
                NetworkHandler.sendDive();
                continue;
            }

            if (ProneAction.INSTANCE.tryStartClient(player)) {
                NetworkHandler.sendProneSet(true);
            }
        }

        // ---- 探头（按住 Q / E）----
        if (player != null) {
            boolean left  = KeyBindings.PEEK_LEFT.isDown();
            boolean right = KeyBindings.PEEK_RIGHT.isDown();

            PeekAction.Dir target = resolvePeekTarget(left, right);

            if (target != PeekAction.INSTANCE.dir(player)
                    && PeekAction.INSTANCE.trySet(player, target)) {
                NetworkHandler.sendPeekSet(target);
            }
        }

        // ---- 跳跃键（滑铲跳）----
        // 注意：consumeClick() 会消费掉整个队列里的点击，无论分支是否命中。
        // 原版跳跃用的是 isDown() 状态而非点击队列，所以正常跳跃不受影响。
        // 如果将来需要监听「跳跃按下」事件，请放在本循环之前。
        while (mc.options.keyJump.consumeClick()) {
            if (player != null && SlideAction.INSTANCE.isSliding(player)) {
                SlideAction.INSTANCE.trySlideJump(player);
                NetworkHandler.sendSlideJump();
            }
        }
    }

    /**
     * 解析本 tick 探头目标方向。
     *
     * <p>{@code peek.lastPressWins = true}：两键同时按住时以「最近按下的
     * 方向」为准；某键松开后自动回落到另一个还按着的键。
     * <p>{@code peek.lastPressWins = false}：两键同时按住时互相抵消，
     * 方向为 {@link PeekAction.Dir#NONE}（站立）。
     *
     * <p>按下边沿通过对比上一 tick 与当前 tick 的 {@code isDown()} 得到，
     * 不使用 {@code consumeClick()}——后者是队列语义，会与其它读取同一
     * KeyMapping 状态的逻辑抢消费。
     */
    private static PeekAction.Dir resolvePeekTarget(boolean left, boolean right) {
        boolean leftEdge  = left  && !peekLeftPrev;
        boolean rightEdge = right && !peekRightPrev;

        // 记录本 tick 状态，供下一 tick 检测边沿
        peekLeftPrev  = left;
        peekRightPrev = right;

        if (!MoveConfig.INSTANCE.peekLastPressWins.get()) {
            // 原逻辑：两键同按时抵消为 NONE
            return (left == right)
                    ? PeekAction.Dir.NONE
                    : (left ? PeekAction.Dir.LEFT : PeekAction.Dir.RIGHT);
        }

        // 有新的按下边沿 → 更新「最近按下」
        if (leftEdge)  peekLastPressed = PeekAction.Dir.LEFT;
        if (rightEdge) peekLastPressed = PeekAction.Dir.RIGHT;

        // 最近按下的方向对应的键仍按住 → 用它
        if (peekLastPressed == PeekAction.Dir.LEFT && left) {
            return PeekAction.Dir.LEFT;
        }
        if (peekLastPressed == PeekAction.Dir.RIGHT && right) {
            return PeekAction.Dir.RIGHT;
        }

        // 最近按下的键已松开：如果另一键仍按住，回落到它
        if (left) {
            peekLastPressed = PeekAction.Dir.LEFT;
            return PeekAction.Dir.LEFT;
        }
        if (right) {
            peekLastPressed = PeekAction.Dir.RIGHT;
            return PeekAction.Dir.RIGHT;
        }
        return PeekAction.Dir.NONE;
    }
}