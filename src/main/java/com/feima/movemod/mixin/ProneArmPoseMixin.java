package com.feima.movemod.mixin;

import com.feima.movemod.action.DiveAction;
import com.feima.movemod.action.ProneAction;
import com.feima.movemod.client.ProneArmPoseState;
import com.feima.movemod.config.MoveConfig;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 趴下 / 飞扑时覆盖双臂姿态。
 *
 * <p>本 Mixin 保持在 {@code setupAnim} TAIL、默认 priority，让 TaCZ /
 * PlayerAnimator 在它之后写入的动画覆盖趴下 / 飞扑姿态——趴下时优先
 * 使用 TaCZ 的 lie 动画，无 TaCZ 时回落到本 Mixin 的前伸姿态。
 * 滑铲不同，见 {@link SlidePartPoseMixin}。
 */
@Mixin(HumanoidModel.class)
public abstract class ProneArmPoseMixin {

    private static final float PRONE_ANGLE_DEG      = 190.0F;
    private static final float PRONE_SWAY_DEG       = 30.0F;
    private static final float PRONE_SIDE_SWAY_DEG  = 20.0F;
    private static final float DIVE_ANGLE_DEG       = 180.0F;
    private static final float DIVE_SWAY_DEG        = 30.0F;
    private static final float ARM_SPREAD_DEG       = 12.0F;
    private static final float ARM_TRANSITION_TICKS = 8.0F;
    private static final float DIVE_SWAY_FREQ       = 0.35F;
    private static final float WALK_PHASE_SCALE     = 0.6662F;

    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void fmm$poseArms(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (!(entity instanceof Player player)) return;

        final boolean diveEnabled = MoveConfig.INSTANCE.diveEnabled.get();
        final boolean diving = diveEnabled && DiveAction.INSTANCE.isDiving(player);
        final boolean prone  = ProneAction.INSTANCE.isProne(player);

        final UUID id = player.getUUID();

        final float alpha = Mth.clamp(1.0F / ARM_TRANSITION_TICKS, 0.02F, 1.0F);

        final float diveW  = ProneArmPoseState.approachDive (id, diving ? 1F : 0F, alpha);
        final float proneW = ProneArmPoseState.approachProne(id, prone  ? 1F : 0F, alpha);

        final float totalW = diveW + proneW;
        final float weight = Mth.clamp(totalW, 0.0F, 1.0F);

        if (weight <= 0.001F && !diving && !prone) {
            ProneArmPoseState.forget(id);
            return;
        }

        final float angleDeg;
        if (totalW > 0.0001F) {
            angleDeg = (DIVE_ANGLE_DEG * diveW + PRONE_ANGLE_DEG * proneW) / totalW;
        } else {
            angleDeg = PRONE_ANGLE_DEG;
        }

        final float angleRad  = (float) Math.toRadians(angleDeg);
        final float spreadRad = (float) Math.toRadians(ARM_SPREAD_DEG);

        final float proneSwayRad     = (float) Math.toRadians(PRONE_SWAY_DEG);
        final float proneSideSwayRad = (float) Math.toRadians(PRONE_SIDE_SWAY_DEG);

        final float walkAmount = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        final float walkPhase  = limbSwing * WALK_PHASE_SCALE;

        final float crawlSwingX = Mth.cos(walkPhase) * walkAmount * proneSwayRad;
        final float crawlSwingZ = Mth.sin(walkPhase) * walkAmount * proneSideSwayRad;

        final float diveSwayRad = (float) Math.toRadians(DIVE_SWAY_DEG);
        final float diveSwingX  = Mth.sin(ageInTicks * DIVE_SWAY_FREQ) * diveSwayRad;

        final float rightXRotSwing =  crawlSwingX * proneW + diveSwingX * diveW;
        final float leftXRotSwing  = -crawlSwingX * proneW + diveSwingX * diveW;

        final float rightXTarget = angleRad + rightXRotSwing;
        final float leftXTarget  = angleRad + leftXRotSwing;

        final float proneZWingR =  crawlSwingZ * proneW;
        final float proneZWingL = -crawlSwingZ * proneW;

        final float rightZTarget = -spreadRad + proneZWingR;
        final float leftZTarget  =  spreadRad + proneZWingL;

        this.rightArm.xRot = Mth.lerp(weight, this.rightArm.xRot, rightXTarget);
        this.leftArm.xRot  = Mth.lerp(weight, this.leftArm.xRot,  leftXTarget);
        this.rightArm.zRot = Mth.lerp(weight, this.rightArm.zRot, rightZTarget);
        this.leftArm.zRot  = Mth.lerp(weight, this.leftArm.zRot,  leftZTarget);
        this.rightArm.yRot = Mth.lerp(weight, this.rightArm.yRot, 0.0F);
        this.leftArm.yRot  = Mth.lerp(weight, this.leftArm.yRot,  0.0F);
    }
}