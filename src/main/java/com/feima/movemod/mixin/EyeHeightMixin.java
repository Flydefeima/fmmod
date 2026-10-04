package com.feima.movemod.mixin;

import com.feima.movemod.action.SlideAction;
import com.feima.movemod.config.MoveConfig;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class EyeHeightMixin {

    @Inject(method = "getEyeHeight", at = @At("HEAD"), cancellable = true)
    private void fmm$getEyeHeight(
            Pose pose,
            EntityDimensions dims,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!((Object) this instanceof Player)) return;
        if (!MoveConfig.INSTANCE.enabled.get()) return;
        if (!MoveConfig.INSTANCE.slideEnabled.get()) return;

        Player self = (Player) (Object) this;
        if (SlideAction.INSTANCE.isSliding(self)) {
            cir.setReturnValue(MoveConfig.INSTANCE.eyeHeight.get().floatValue());
        }
    }
}