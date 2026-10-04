package com.feima.movemod.mixin;

import com.feima.movemod.action.SlideAction;
import com.feima.movemod.config.MoveConfig;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerHitboxMixin {

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void fmm$getDimensions(
            Pose pose,
            CallbackInfoReturnable<EntityDimensions> cir
    ) {
        Player self = (Player) (Object) this;
        if (!MoveConfig.INSTANCE.enabled.get()) return;
        if (!MoveConfig.INSTANCE.slideEnabled.get()) return;
        if (SlideAction.INSTANCE.isSliding(self)) {
            cir.setReturnValue(EntityDimensions.fixed(
                    MoveConfig.INSTANCE.hitboxWidth.get().floatValue(),
                    MoveConfig.INSTANCE.hitboxHeight.get().floatValue()
            ));
        }
    }
}