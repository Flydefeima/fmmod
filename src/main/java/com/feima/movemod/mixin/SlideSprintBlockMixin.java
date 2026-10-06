package com.feima.movemod.mixin;

import com.feima.movemod.action.SlideAction;
import com.feima.movemod.config.MoveConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 滑铲期间禁止玩家进入疾跑状态。
 */
@Mixin(LivingEntity.class)
public abstract class SlideSprintBlockMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void fmm$blockSprintWhileSliding(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) return;
        if (!MoveConfig.INSTANCE.slideEnabled.get()) return;
        if (!((Object) this instanceof Player player)) return;

        if (SlideAction.INSTANCE.isSliding(player)) {
            ci.cancel();
        }
    }
}