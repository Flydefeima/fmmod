package com.feima.movemod.mixin;

import com.feima.movemod.action.DiveAction;
import com.feima.movemod.action.PeekAction;
import com.feima.movemod.action.ProneAction;
import com.feima.movemod.action.SlideAction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerSlideMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void fmm$tickActions(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        SlideAction.INSTANCE.tick(self);
        PeekAction.INSTANCE.tick(self);
        // Dive 在 Prone 之前：Dive 的 finishDive 会直接调用 ProneAction.tryStart，
        // 紧随其后的 Prone tick 立即处理新激活的趴下状态。
        DiveAction.INSTANCE.tick(self);
        ProneAction.INSTANCE.tick(self);
    }
}