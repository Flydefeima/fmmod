package com.feima.movemod.mixin;

import com.feima.movemod.action.PeekAction;
import com.feima.movemod.config.MoveConfig;
import net.minecraft.client.Minecraft;
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

/**
 * 探头第三人称：整个玩家模型绕<b>脚底</b>竖直轴旋转 θ，并叠加横向平移。
 *
 * <p><b>为什么 pivot 在脚而不是腰</b>：绕腰旋转时只有上半身明显侧倾，
 * 腿几乎不动，看起来像"上半身扭了一下"。绕脚底旋转时整个模型作为
 * 刚体倾斜，脚钉在原地、头向侧方远离，视觉上就是"整个人向侧面探出"，
 * 即用户期待的"整体偏移"。
 *
 * <p><b>数学自洽</b>：腿的 pivot 在大腿根（站立时模型 y=12），
 * 腿几何向下延伸到脚（y=24）。当 pivotY=24、part.zRot=rad 时，
 * 腿的下端经过"位置绕 pivot 旋转 + 几何绕自身 pivot 旋转"后恰好
 * 仍落在 (0, 24)——脚不动，腿正确倾斜。
 *
 * <p><b>模型空间约定</b>：1 格 = 16 模型单位。经
 * {@code LivingEntityRenderer} 的变换链后，模型空间 <b>+x = 玩家左</b>
 * （模型里 {@code rightArm.x = -5}、{@code leftArm.x = +5}）。
 * 所以 world 空间"向右探"对应模型 -x，lateral 取负号。
 *
 * <p><b>蹲下适配</b>：原版蹲下时 {@code rightLeg.y} 从 12 变到 12.2，
 * 脚底随之从 24 变到 24.2。这里用腿 pivot 的偏移量让绕轴中心一起跟随，
 * 保证蹲下时脚仍钉在原地。
 *
 * <p>第一人称手臂也走 HumanoidModel.setupAnim，本地玩家 + 第一人称时
 * 跳过整体变换，避免手臂跟随绕脚旋转。
 *
 * <p>用 {@link PeekAction#isPeekActive}（而非 isPeeking）作为入口判断，
 * 保证收回时第三人称也有过渡。
 */
@Mixin(HumanoidModel.class)
public abstract class PeekFullBodyMixin<T extends LivingEntity> {

    @Shadow public ModelPart body;
    @Shadow public ModelPart head;
    @Shadow public ModelPart hat;
    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;

    /** 站立时脚底位置（模型坐标系，Y 向下为正）。 */
    private static final float FOOT_BOTTOM_Y = 24.0F;

    /** 站立时腿 pivot（大腿根）的 y。 */
    private static final float LEG_BASE_Y = 12.0F;

    /** 模型空间缩放：1 格 = 16 单位。 */
    private static final double MODEL_UNITS_PER_BLOCK = 16.0;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void fmm$rotateFullBody(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (!(entity instanceof Player player)) return;
        if (!PeekAction.INSTANCE.isPeekActive(player)) return;

        // 第一人称且是本地玩家 → 跳过（否则手臂会被绕脚旋转）
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == player && mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;

        float partial = mc.getFrameTime();
        double offset = PeekAction.INSTANCE.smoothOffset(player, partial);
        double max = MoveConfig.INSTANCE.peekDistance.get();
        if (max <= 0.0 || Math.abs(offset) < 1.0E-4) return;

        // 归一化比例：0 → 未探头，±1 → 探到最大
        double ratio = offset / max;

        // 旋转角（弧度，负号让侧倾方向与相机 roll 一致）
        float angle = (float) (ratio * MoveConfig.INSTANCE.peekAngleThirdPerson.get());
        float rad = -angle * Mth.DEG_TO_RAD;

        // 横向平移（模型单位，取负号：world 向右 = 模型 -x）
        float lateral = (float) (-ratio
                * MoveConfig.INSTANCE.peekModelOffset.get()
                * MODEL_UNITS_PER_BLOCK);

        // 绕脚为轴：站立时脚在 24；蹲下时腿 pivot 下移 0.2，
        // 脚随之到 24.2，绕轴中心同步跟随。
        float pivotY = FOOT_BOTTOM_Y + (self.rightLeg.y - LEG_BASE_Y);

        applyPivot(self.body,     pivotY, rad, lateral);
        applyPivot(self.head,     pivotY, rad, lateral);
        applyPivot(self.hat,      pivotY, rad, lateral);
        applyPivot(self.rightArm, pivotY, rad, lateral);
        applyPivot(self.leftArm,  pivotY, rad, lateral);
        applyPivot(self.rightLeg, pivotY, rad, lateral);
        applyPivot(self.leftLeg,  pivotY, rad, lateral);

        // jacket / sleeves 由 PlayerModel.setupAnim 的 copyFrom 自动跟随
    }

    /**
     * 绕模型空间 (0, pivotY) 处的竖直轴旋转 part，再沿模型 x 轴平移
     * {@code lateral}。
     *
     * <p>顺序：先旋转（绕 pivot），再平移。
     * 所有 part 使用同一 {@code pivotY} 与 {@code lateral}，
     * 等价于「整个模型刚体绕 pivot 旋转 + 沿 x 平移」。
     *
     * <p>{@code part.zRot = rad} 是让 part 的几何方向也旋转 rad。
     * 对腿部来说，这一步 + 位置绕脚 pivot 旋转，正好让脚落在原位置。
     */
    private static void applyPivot(ModelPart part, float pivotY, float rad, float lateral) {
        float px = part.x;
        float py = part.y;

        float dx = px;
        float dy = py - pivotY;

        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);

        part.x = dx * cos - dy * sin + lateral;
        part.y = dx * sin + dy * cos + pivotY;
        part.zRot = rad;
    }
}