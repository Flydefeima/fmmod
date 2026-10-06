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
 */
@Mod.EventBusSubscriber(modid = FeimaMoveMod.MODID, value = Dist.CLIENT)
public final class StaminaDisplay {

    private StaminaDisplay() {}

    // ============================================================
    // 硬编码显示参数
    // ============================================================
    private static final int HOLD_TICKS = 20;
    private static final int FADE_TICKS = 20;
    private static final float SCALE = 1.5F;
    private static final int COLOR = 0xFFFFFF;
    private static final boolean SHADOW = true;

    private static int lastValue = -1;
    private static int holdRemaining = 0;

    // ============================================================
    // Tick
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
    // Render
    // ============================================================
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (holdRemaining <= 0) return;

        int alpha = 255;
        if (FADE_TICKS > 0 && holdRemaining < FADE_TICKS) {
            alpha = (int) (255L * holdRemaining / FADE_TICKS);
        }
        if (alpha <= 0) return;

        String text = String.valueOf(Math.round(StaminaTracker.INSTANCE.get(player)));
        int argb = (alpha << 24) | COLOR;

        GuiGraphics g = event.getGuiGraphics();

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        float cx = screenW / 2.0F;
        float cy = screenH - 60.0F;

        int textW = mc.font.width(text);
        int lineH = mc.font.lineHeight;

        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(cx, cy, 0.0F);
            pose.scale(SCALE, SCALE, 1.0F);
            g.drawString(mc.font, text, -textW / 2, -lineH / 2, argb, SHADOW);
        } finally {
            pose.popPose();
        }
    }

    // ============================================================
    // 工具
    // ============================================================
    private static boolean isEnabled() {
        if (!MoveConfig.INSTANCE.slideEnabled.get()) return false;
        if (!MoveConfig.INSTANCE.staminaEnabled.get()) return false;
        return true;
    }
}