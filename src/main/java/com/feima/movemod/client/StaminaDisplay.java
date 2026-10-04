package com.feima.movemod.client;

import com.feima.movemod.FeimaMoveMod;
import com.feima.movemod.action.StaminaTracker;
import com.feima.movemod.config.MoveConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 耐力数字显示 —— 硬编码样式。
 *
 * 显示行为：
 *   - 数值变化后保持 {@link #HOLD_TICKS} tick，再在 {@link #FADE_TICKS} tick 内淡出
 *   - 位置：屏幕底部居中
 *   - 颜色：白色，带阴影
 *   - 字号缩放：{@link #SCALE}
 *
 * 数值来源：{@link StaminaTracker#get}（本地玩家双端同步过，权威）。
 */
@Mod.EventBusSubscriber(modid = FeimaMoveMod.MODID, value = Dist.CLIENT)
public final class StaminaDisplay {

    private StaminaDisplay() {}

    // ============================================================
    // 硬编码显示参数
    // ============================================================
    /** 保持不透明的时间（tick） */
    private static final int HOLD_TICKS = 20;
    /** 淡出时间（tick） */
    private static final int FADE_TICKS = 20;
    /** 字号缩放 */
    private static final float SCALE = 1.5F;
    /** 颜色（0xRRGGBB） */
    private static final int COLOR = 0xFFFFFF;
    /** 是否绘制文字阴影 */
    private static final boolean SHADOW = true;

    /** 上次显示过的整数值，用于检测变化。-1 表示尚未初始化 */
    private static int lastValue = -1;
    /** 剩余保持 tick 数（变化后置为 HOLD_TICKS） */
    private static int holdRemaining = 0;

    // ============================================================
    // Tick：检测数值变化、推进淡出计时
    // ============================================================
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isEnabled()) {
            lastValue = -1;
            holdRemaining = 0;
            return;
        }

        int value = (int) Math.round(StaminaTracker.INSTANCE.get(player));

        // 首次进入世界：静默初始化，不触发显示
        if (lastValue < 0) {
            lastValue = value;
            holdRemaining = 0;
            return;
        }

        if (value != lastValue) {
            lastValue = value;
            holdRemaining = HOLD_TICKS;
        } else if (holdRemaining > 0) {
            holdRemaining--;
        }
    }

    // ============================================================
    // Render：绘制数字
    // ============================================================
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // hold 用完就不再显示
        if (holdRemaining <= 0) return;

        // ---- 透明度 ----
        int alpha = 255;
        if (FADE_TICKS > 0 && holdRemaining < FADE_TICKS) {
            alpha = (int) (255L * holdRemaining / FADE_TICKS);
        }
        if (alpha <= 0) return;

        // ---- 文本：硬编码 value 模式 ----
        String text = String.valueOf(Math.round(StaminaTracker.INSTANCE.get(player)));
        int argb = (alpha << 24) | COLOR;

        GuiGraphics g = event.getGuiGraphics();

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // 硬编码位置：屏幕底部居中
        float cx = screenW / 2.0F;
        float cy = screenH - 60.0F;

        int textW = mc.font.width(text);
        int lineH = mc.font.lineHeight;

        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(cx, cy, 0.0F);
            pose.scale(SCALE, SCALE, 1.0F);
            // 以 (cx, cy) 为中心绘制
            g.drawString(mc.font, text, -textW / 2, -lineH / 2, argb, SHADOW);
        } finally {
            pose.popPose();
        }
    }

    // ============================================================
    // 工具
    // ============================================================
    private static boolean isEnabled() {
        if (!MoveConfig.INSTANCE.enabled.get()) return false;
        if (!MoveConfig.INSTANCE.slideEnabled.get()) return false;
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return false;
        return true;
    }
}