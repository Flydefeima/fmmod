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
 * <p><b>栈平衡</b>：本 Mixin 显式 push / pop 配对。
 * {@link #fmm$applyPeekTransform} 在 {@code setupAnim} 之后 push 并叠加变换，
 * {@link #fmm$popPeekTransform} 在 render 的 {@code RETURN} 处 pop，
 * 保证变换在 {@code renderToBuffer} 与后续附加层（手持物品、箭矢）都已消费后
 * 才被移除，同时避免与其他模组在 {@code setupAnim} 之后、
 * {@code renderToBuffer} 之前插入的 push/pop 相互夹持。
 *
 * <p><b>与其它 FullBody Mixin 的叠加顺序</b>：
 * <pre>
 *   Slide    priority = 1500  （先 push，最内层）
 *   AirDive  priority = 1600
 *   Peek     priority = 1700  （本类，后 push，最外层）
 * </pre>
 * push/pop 是 LIFO，priority 高的先 push、后 pop，栈平衡。
 *
 * <p><b>名字牌为何不受影响</b>：{@code renderNameTag} 由
 * {@code super.render} 在 {@code popPose()} 之后独立 push/pop 绘制，
 * 我们的变换在此期间已被自身 pop，因此名字牌保持水平。
 *
 * <p><b>与 TaCZ 共存</b>：TaCZ 的 {@code AdjustmentYRotModifier} 在
 * ModelPart 层做 body/head/arm 微调，我们在 PoseStack 层做整体刚体变换，
 * 两者叠加。不再需要单独处理持枪分支。
 */
@Mixin(value = LivingEntityRenderer.class, priority = 1700)
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
        if (!shouldApply(entity, partialTicks)) return;

        Player player = (Player) entity;
        double offset = PeekAction.INSTANCE.smoothOffset(player, partialTicks);
        double max = MoveConfig.INSTANCE.peekDistance.get();

        // 归一化比例：0 → 未探头，±1 → 探到最大
        double ratio = offset / max;

        // 旋转角（弧度，负号让侧倾方向与相机 roll 一致）
        float angle = (float) (ratio * MoveConfig.INSTANCE.peekAngleThirdPerson.get());
        float rad = -angle * Mth.DEG_TO_RAD;

        // 横向平移（格）。
        float lateral = (float) (-ratio * MoveConfig.INSTANCE.peekModelOffset.get());

        poseStack.pushPose();
        poseStack.translate(0.0F, FOOT_PIVOT_Y, 0.0F);
        poseStack.mulPose(Axis.ZP.rotation(rad));
        poseStack.translate(0.0F, -FOOT_PIVOT_Y, 0.0F);
        poseStack.translate(lateral, 0.0F, 0.0F);
    }

    /**
     * 与 {@link #fmm$applyPeekTransform} 严格配对。
     *
     * <p>两次判断条件完全一致（共用 {@link #shouldApply}），且这些条件在单次
     * render 调用期间都是稳定的（动画状态由 tick 更新，partialTicks 是方法
     * 入参，配置为常量），因此两次判断结果相同、栈平衡。
     *
     * <p>放在 {@code RETURN}：此时 {@code renderToBuffer} 和所有后续附加层
     * 都已完成，我们这一层使命结束。渲染器自身的 {@code popPose} 随后会把
     * 剩下的栈恢复到调用前。
     */
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FF" +
                     "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                     "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void fmm$popPeekTransform(
            LivingEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!shouldApply(entity, partialTicks)) return;
        poseStack.popPose();
    }

    /**
     * apply / pop 共用的前置条件。单次 render 调用期间结果稳定。
     */
    private static boolean shouldApply(LivingEntity entity, float partialTicks) {
        if (!(entity instanceof Player player)) return false;
        if (!PeekAction.INSTANCE.isPeekActive(player)) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == player && mc.options.getCameraType().isFirstPerson()) {
            return false;
        }

        double offset = PeekAction.INSTANCE.smoothOffset(player, partialTicks);
        double max = MoveConfig.INSTANCE.peekDistance.get();
        if (max <= 0.0 || Math.abs(offset) < 1.0E-4) return false;

        return true;
    }
}