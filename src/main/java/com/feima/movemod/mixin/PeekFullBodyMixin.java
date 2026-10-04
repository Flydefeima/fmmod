package com.feima.movemod.mixin;

import com.feima.movemod.action.PeekAction;
import com.feima.movemod.config.MoveConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 探头第三人称：整个玩家模型绕<b>脚底</b> Z 轴旋转 θ，并叠加横向平移。
 *
 * <p><b>为什么用 PoseStack</b>：在 {@code HumanoidModel.setupAnim} 里逐个修改
 * 7 个 {@code ModelPart} 会与 TaCZ / PlayerAnimator 的 {@code AdjustmentModifier}
 * 抢同一套字段，也漏掉帽子 / 外套 / 附加层。直接在
 * {@code LivingEntityRenderer.render} 里对 PoseStack 施加一次刚体变换，
 * 所有子部件（含附加层）自动跟随，且与任何修改 ModelPart 字段的模组
 * <b>不冲突</b>——它们改的是 ModelPart 内部，我们改的是外层矩阵。
 *
 * <p><b>坐标系</b>：注入点在 {@code translate(0, -1.501, 0)} 之后、
 * {@code model.setupAnim} 之后。此时局部空间 = 模型空间 ÷ 16：
 * <ul>
 *   <li>头顶 = {@code (0, 0, 0)}</li>
 *   <li>脚底 = {@code (0, 1.5, 0)}（模型空间 y=24）</li>
 *   <li>局部 +Y 指向世界下方（因 setupRotations 后接了 scale(-1,-1,1)）</li>
 * </ul>
 * 绕脚底 Z 轴旋转 = 平移 → 旋转 → 平移回去。
 *
 * <p><b>不做 push/pop</b>：让变换留在栈上直到渲染器自身的 popPose，
 * 这样 {@code renderToBuffer} 之后的附加层（手持物品、箭矢）也跟随倾斜，
 * 与"整个人探出去"的视觉一致。
 *
 * <p><b>与 TaCZ 共存</b>：TaCZ 的 {@code AdjustmentYRotModifier} 在
 * ModelPart 层做 body/head/arm 微调，我们在 PoseStack 层做整体刚体变换，
 * 两者叠加。不再需要单独处理持枪分支。
 */
@Mixin(value = LivingEntityRenderer.class, priority = 1500)
public abstract class PeekFullBodyMixin {

    /** 站立时脚底在模型空间的 y（模型单位 24 → 1.5 格）。 */
    private static final float FOOT_PIVOT_Y = 1.5F;

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
    private void fmm$applyPeekTransform(
            LivingEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!(entity instanceof Player player)) return;
        if (!PeekAction.INSTANCE.isPeekActive(player)) return;

        // 第一人称且本地玩家 → 跳过
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == player && mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        double offset = PeekAction.INSTANCE.smoothOffset(player, partialTicks);
        double max = MoveConfig.INSTANCE.peekDistance.get();
        if (max <= 0.0 || Math.abs(offset) < 1.0E-4) return;

        // 归一化比例：0 → 未探头，±1 → 探到最大
        double ratio = offset / max;

        // 旋转角（弧度，负号让侧倾方向与相机 roll 一致）
        float angle = (float) (ratio * MoveConfig.INSTANCE.peekAngleThirdPerson.get());
        float rad = -angle * Mth.DEG_TO_RAD;

        // 横向平移（格）。旧版量级 = -ratio * modelOffset * 16（模型单位），
        // 这里在 PoseStack 里直接以格为单位，所以除以 16 后 = -ratio * modelOffset。
        float lateral = (float) (-ratio * MoveConfig.INSTANCE.peekModelOffset.get());

        // 绕脚底 pivot 旋转 + 横向平移
        // 注意：不 push/pop，让变换留在栈上直到 renderToBuffer 之后的附加层也跟随
        poseStack.translate(0.0F, FOOT_PIVOT_Y, 0.0F);
        poseStack.mulPose(Axis.ZP.rotation(rad));
        poseStack.translate(0.0F, -FOOT_PIVOT_Y, 0.0F);
        poseStack.translate(lateral, 0.0F, 0.0F);
    }
}