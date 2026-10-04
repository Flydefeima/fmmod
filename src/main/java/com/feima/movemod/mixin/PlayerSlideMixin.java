package com.feima.movemod.mixin;

import com.feima.movemod.action.CrawlAction;
import com.feima.movemod.action.PeekAction;
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
        CrawlAction.INSTANCE.tick(self);
    }
}