package com.feima.movemod.mixin;

import com.feima.movemod.action.SlideAction;
import com.feima.movemod.client.SlidePoseState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 滑铲：各身体部位的局部姿态。
 *
 * <p>注入 {@code LivingEntityRenderer.render} 里 {@code setupAnim} 调用
 * 之后——无论 PlayerAnimator / TaCZ 在 setupAnim 内部如何接管，控制权
 * 回到这里时 ModelPart 已是它们的最终值，我们直接覆盖。
 *
 * <p><b>2D 皮肤层的同步</b>：vanilla 里 {@code hat} 靠
 * {@code head.copyFrom} 同步、{@code jacket/sleeves/pants} 靠
 * {@code body/arms/legs.copyFrom} 同步。我们覆盖主部件后必须手动再
 * 同步一次这些层，否则它们会保持原版 idle 朝向，视觉上「皮肤层与
 * 模型分离」。
 *
 * <p><b>滑铲结束的清理</b>：PlayerAnimator 会读取 ModelPart 当前值作为
 * 它的输入（{@code value0}）。若滑铲结束后我们不主动恢复字段，滑铲
 * 残留值会被它继续当作输入传递下去，导致姿态永久残留。本类在滑铲
 * 状态 {@code true → false} 的那一帧主动把字段恢复为原版 idle 值。
 * 状态由 {@link SlidePoseState} 持有，玩家退出时由清理处理器回收。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class SlidePartPoseMixin {

    @Shadow
    public abstract EntityModel<LivingEntity> getModel();

    /**
     * 部件初始化位置缓存。
     *
     * <p>key 是模型部件实例，数量有限且随渲染器常驻，<b>不需要</b>
     * 玩家维度的清理——请勿误加清理逻辑，否则会把缓存打断导致
     * 姿态平移基准被重置。
     */
    @Unique
    private Map<ModelPart, float[]> fmm$initPos;

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
    private void fmm$applySlidePartPose(
            LivingEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!(entity instanceof Player player)) return;

        EntityModel<LivingEntity> rawModel = this.getModel();
        if (!(rawModel instanceof HumanoidModel<?> humanoid)) return;

        final UUID id = player.getUUID();
        final boolean sliding = SlideAction.INSTANCE.isSliding(player);

        if (sliding) {
            SlidePoseState.mark(id);

            fmm$restorePos(humanoid.head);
            fmm$restorePos(humanoid.body);
            fmm$restorePos(humanoid.rightArm);
            fmm$restorePos(humanoid.leftArm);
            fmm$restorePos(humanoid.rightLeg);
            fmm$restorePos(humanoid.leftLeg);
            humanoid.head.zRot = 0.0F;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == player && mc.options.getCameraType().isFirstPerson()) {
                return;
            }

            // 参数：rotX, rotY, rotZ, posX, posY, posZ
            fmm$apply(humanoid.head,      38.3466F, -16.7208F, -4.6321F,   0.0F,  0.0F,  3.0F);
            fmm$apply(humanoid.body,       0.0F,    -31.6631F,  1.3388F,   0.0F,  0.0F,  2.0F);
            fmm$apply(humanoid.rightArm, -65.6576F, -59.6194F, 37.7303F,   1.5F,  0.0F,  0.0F);
            fmm$apply(humanoid.leftArm,   36.4338F,  16.9161F, -68.6414F,  0.0F, -1.0F,  5.0F);
            fmm$apply(humanoid.rightLeg, -41.9596F, -19.224F,  29.2305F,   0.0F,  0.0F,  0.0F);
            fmm$apply(humanoid.leftLeg,  -40.8224F, -16.019F,  -3.34F,     0.0F,  0.0F,  1.0F);

            fmm$sync2DLayers(humanoid);

        } else if (SlidePoseState.unmark(id)) {
            fmm$resetPose(humanoid, player, partialTicks);
            fmm$sync2DLayers(humanoid);
        }
    }

    /**
     * 把 hat / jacket / sleeves / pants 从对应主部件同步过去。
     *
     * <p>vanilla 是在 setupAnim 内部做这一步的，我们在 setupAnim 之后
     * 覆盖了主部件，必须手动再同步一次。
     */
    @Unique
    private void fmm$sync2DLayers(HumanoidModel<?> humanoid) {
        humanoid.hat.copyFrom(humanoid.head);

        if (humanoid instanceof PlayerModel<?> pm) {
            pm.jacket.copyFrom(humanoid.body);
            pm.leftSleeve.copyFrom(humanoid.leftArm);
            pm.rightSleeve.copyFrom(humanoid.rightArm);
            pm.leftPants.copyFrom(humanoid.leftLeg);
            pm.rightPants.copyFrom(humanoid.rightLeg);
        }
    }

    /**
     * 把滑铲期间修改过的字段恢复为接近原版 idle 的值。
     */
    @Unique
    private void fmm$resetPose(HumanoidModel<?> humanoid, Player player, float partialTicks) {
        fmm$restorePos(humanoid.head);
        fmm$restorePos(humanoid.body);
        fmm$restorePos(humanoid.rightArm);
        fmm$restorePos(humanoid.leftArm);
        fmm$restorePos(humanoid.rightLeg);
        fmm$restorePos(humanoid.leftLeg);

        final float xRot = Mth.rotLerp(partialTicks, player.xRotO, player.getXRot());
        final float yHead = Mth.rotLerp(partialTicks, player.yHeadRotO, player.yHeadRot);
        final float yBody = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
        final float netHeadYaw = Mth.wrapDegrees(yHead - yBody);

        humanoid.head.xRot = xRot * Mth.DEG_TO_RAD;
        humanoid.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        humanoid.head.zRot = 0.0F;

        humanoid.body.xRot = 0.0F;
        humanoid.body.yRot = 0.0F;
        humanoid.body.zRot = 0.0F;

        humanoid.rightArm.xRot = 0.0F;
        humanoid.rightArm.yRot = 0.0F;
        humanoid.rightArm.zRot = 0.0F;
        humanoid.leftArm.xRot = 0.0F;
        humanoid.leftArm.yRot = 0.0F;
        humanoid.leftArm.zRot = 0.0F;

        humanoid.rightLeg.xRot = 0.0F;
        humanoid.rightLeg.yRot = 0.0F;
        humanoid.rightLeg.zRot = 0.0F;
        humanoid.leftLeg.xRot = 0.0F;
        humanoid.leftLeg.yRot = 0.0F;
        humanoid.leftLeg.zRot = 0.0F;
    }

    @Unique
    private void fmm$restorePos(ModelPart part) {
        if (this.fmm$initPos == null) {
            this.fmm$initPos = new IdentityHashMap<>();
        }
        float[] init = this.fmm$initPos.get(part);
        if (init == null) {
            init = new float[]{part.x, part.y, part.z};
            this.fmm$initPos.put(part, init);
        }
        part.x = init[0];
        part.y = init[1];
        part.z = init[2];
    }

    @Unique
    private void fmm$apply(ModelPart part,
                           float rx, float ry, float rz,
                           float px, float py, float pz) {
        part.xRot = (float) Math.toRadians(rx);
        part.yRot = (float) Math.toRadians(ry);
        part.zRot = (float) Math.toRadians(rz);

        float[] init = this.fmm$initPos.get(part);
        part.x = init[0] + px;
        part.y = init[1] + py;
        part.z = init[2] + pz;
    }
}