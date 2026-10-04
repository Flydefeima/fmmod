package com.feima.movemod.mixin;

import com.feima.movemod.action.PeekAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F3+B：探头时隐藏原版静态 AABB，改画「旋转后的白色斜长方体」。
 *
 * <p>做法：在 {@code renderHitbox} 的 HEAD 处取消原逻辑，然后自己重画：
 * <ul>
 *   <li><b>不画</b>原版白色静态 AABB（未旋转的玩家盒）</li>
 *   <li><b>不画</b>原版底部红色标记（LivingEntity 分支）</li>
 *   <li><b>保留</b>视线终点的小红方块（原版就有）</li>
 *   <li><b>新增</b>旋转后的白色斜长方体线框</li>
 * </ul>
 *
 * <p>旋转 pivot 在<b>脚底</b>（{@code base.minY}），与第三人称模型
 * （{@code PeekFullBodyMixin}）使用的 pivot 一致，因此线框形状与
 * 模型倾斜形状精确匹配。
 *
 * <p>本 Mixin 使用<b>渲染几何</b>（baseBoxRender / pivotOf /
 * thetaRender），跟随 {@code peek.transitionTicks} 过渡。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class PeekHitboxRenderMixin {

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void fmm$renderPeekHitbox(
            PoseStack poseStack,
            VertexConsumer buffer,
            Entity entity,
            float partialTicks,
            CallbackInfo ci
    ) {
        if (!(entity instanceof Player player)) return;
        if (!PeekAction.INSTANCE.isPeekActive(player)) return;

        // ---- 视线终点小方块（保留原版风格）----
        Vec3 viewVec = player.getViewVector(partialTicks);
        Vec3 eyePos = player.getEyePosition(partialTicks);
        Vec3 endPos = eyePos.add(viewVec.scale(2.0));
        LevelRenderer.renderLineBox(poseStack, buffer,
                endPos.x - 0.15D, endPos.y - 0.15D, endPos.z - 0.15D,
                endPos.x + 0.15D, endPos.y + 0.15D, endPos.z + 0.15D,
                1.0F, 0.0F, 0.0F, 1.0F);

        // ---- 旋转后的白色斜长方体（渲染几何，带过渡）----
        AABB base = PeekAction.INSTANCE.baseBoxRender(player, partialTicks);
        Vec3 pv = PeekAction.pivotOf(base);  // 底边中心（脚底）

        // pivot 相对实体 tick 位置的偏移（renderHitbox 的 poseStack 原点
        // 在实体渲染位置）。
        double pivotRelX = pv.x - entity.getX();
        double pivotRelY = pv.y - entity.getY();
        double pivotRelZ = pv.z - entity.getZ();

        double theta = PeekAction.INSTANCE.thetaRender(player, partialTicks);
        float bodyYaw = player.yBodyRot;
        double rad = Math.toRadians(bodyYaw);
        double fX = -Math.sin(rad);
        double fZ =  Math.cos(rad);

        double halfW  = (base.maxX - base.minX) * 0.5;
        double height = base.maxY - base.minY;

        poseStack.pushPose();
        try {
            poseStack.translate(pivotRelX, pivotRelY, pivotRelZ);

            if (Math.abs(theta) > 1.0E-4) {
                Vector3f axis = new Vector3f((float) fX, 0.0F, (float) fZ);
                if (axis.lengthSquared() > 1.0E-8F) {
                    axis.normalize();
                    Quaternionf q = new Quaternionf().rotateAxis((float) theta, axis);
                    poseStack.mulPose(q);
                }
            }

            // pivot 在底边中心：局部盒 y 从 0（脚底）到 height（头顶）。
            AABB localBox = new AABB(-halfW, 0.0, -halfW, halfW, height, halfW);
            LevelRenderer.renderLineBox(poseStack, buffer, localBox,
                    1.0F, 1.0F, 1.0F, 1.0F); // 原版白色
        } finally {
            poseStack.popPose();
        }

        ci.cancel();
    }
}