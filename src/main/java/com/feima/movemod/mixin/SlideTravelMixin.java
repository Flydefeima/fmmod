package com.feima.movemod.mixin;

import com.feima.movemod.action.DiveAction;
import com.feima.movemod.action.SlideAction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 滑铲 / 飞扑期间屏蔽 WASD 对移动的影响。
 *
 * 关键点：
 *   LivingEntity.aiStep() 在调用 travel() 之前已经读了 xxa/zza：
 *       this.travel(new Vec3(this.xxa, this.yya, this.zza));
 *   所以在 travel() HEAD 处清零字段是无用的——参数已经固定。
 *   必须用 @ModifyVariable(argsOnly = true) 直接改 travelVector 参数。
 */
@Mixin(LivingEntity.class)
public abstract class SlideTravelMixin {

    @Shadow public float xxa;
    @Shadow public float zza;

    @ModifyVariable(
            method = "travel",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Vec3 fmm$blockInputDuringSlide(Vec3 travelVector) {
        if (!((Object) this instanceof Player player)) return travelVector;

        boolean blocked = SlideAction.INSTANCE.isSliding(player)
                || DiveAction.INSTANCE.isDiving(player);
        if (!blocked) return travelVector;

        // 顺手把字段也清零，防止后续 tick 残留
        this.xxa = 0.0F;
        this.zza = 0.0F;

        // 只保留 y（垂直），水平输入置零
        return new Vec3(0.0D, travelVector.y, 0.0D);
    }
}