package com.feima.movemod.mixin;

import com.feima.movemod.action.SlideAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 滑铲：整体刚体变换（绕脚底旋转 + 模型空间位移）。
 *
 * <p>变换参数由调参确定，硬编码在类里。
 * 各部位的局部姿态由 {@link SlidePartPoseMixin} 处理。
 *
 * <p><b>变换顺序（与 Blockbench 一致）</b>：
 * <ol>
 *   <li>先在模型空间平移（方向固定，不随旋转改变）</li>
 *   <li>再绕脚底 pivot 旋转</li>
 * </ol>
 *
 * <p>对所有处于滑铲状态的玩家生效（含本地与远端），第一人称本地玩家
 * 跳过（看不到自己）。
 *
 * <p><b>与其它 FullBody Mixin 的叠加顺序</b>：
 * 三个 {@code LivingEntityRenderer} 层的 Mixin 使用独立的 priority
 * 明确排序，避免同 priority 下的执行顺序未定义：
 * <pre>
 *   Slide    priority = 1500  （先 push，最内层）
 *   AirDive  priority = 1600
 *   Peek     priority = 1700  （后 push，最外层）
 * </pre>
 * push/pop 是 LIFO，priority 高的先 push、后 pop，栈平衡。
 * <b>任何新增的 FullBody 动作 Mixin 请避开 1500~1700 这段 priority。</b>
 *
 * <p><b>命名注意</b>：辅助方法名必须全类唯一，否则与其它 mixin 注入
 * 到同一目标类时会被静默覆盖。本类所有辅助方法都带 {@code fmm$} 前缀。
 */
@Mixin(value = LivingEntityRenderer.class, priority = 1500)
public abstract class SlideFullBodyMixin {

    /** 站立时脚底在模型空间的 y（模型单位 24 → 1.5 格）。 */
    private static final float FOOT_PIVOT_Y = 1.5F;

    // ---- 硬编码姿态（单位：度 / 模型像素） ----
    private static final float ROT_X = -50.0F;
    private static final float ROT_Y = 0.0F;
    private static final float ROT_Z = 0.0F;
    private static final float POS_X = 0.0F;
    private static final float POS_Y = 0.45F;
    private static final float POS_Z = -0.8F;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FF" +
                     "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                     "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;" +
                             "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void fmm$applySlideRoot(
            LivingEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!fmm$shouldApplySlide(entity)) return;

        poseStack.pushPose();

        // 1) 模型空间平移（方向固定）
        if (POS_X != 0F || POS_Y != 0F || POS_Z != 0F) {
            poseStack.translate(POS_X, POS_Y, POS_Z);
        }

        // 2) 绕脚底 pivot 旋转
        poseStack.translate(0.0F, FOOT_PIVOT_Y, 0.0F);
        if (ROT_X != 0F) poseStack.mulPose(Axis.XP.rotationDegrees(ROT_X));
        if (ROT_Y != 0F) poseStack.mulPose(Axis.YP.rotationDegrees(ROT_Y));
        if (ROT_Z != 0F) poseStack.mulPose(Axis.ZP.rotationDegrees(ROT_Z));
        poseStack.translate(0.0F, -FOOT_PIVOT_Y, 0.0F);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FF" +
                     "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                     "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void fmm$popSlideRoot(
            LivingEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!fmm$shouldApplySlide(entity)) return;
        poseStack.popPose();
    }

    private static boolean fmm$shouldApplySlide(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        if (!SlideAction.INSTANCE.isSliding(player)) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == player && mc.options.getCameraType().isFirstPerson()) {
            return false;
        }
        return true;
    }
}