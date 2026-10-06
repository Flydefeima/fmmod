package com.feima.movemod.mixin;

import com.feima.movemod.action.DiveAction;
import com.feima.movemod.action.ProneAction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 趴下 / 飞扑时把 pose 强制为 {@link net.minecraft.world.entity.Pose#SWIMMING}。
 *
 * <p>飞扑优先于趴下判断：飞扑中途即使 {@link ProneAction} 因为某种
 * 时序原因短暂处于激活，也以飞扑的姿态为准。
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void fmm$forcePose(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (DiveAction.INSTANCE.forcePose(self)) {
            ci.cancel();
            return;
        }
        if (ProneAction.INSTANCE.forcePose(self)) {
            ci.cancel();
        }
    }
}