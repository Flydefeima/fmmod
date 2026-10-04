package com.feima.movemod.mixin;

import com.feima.movemod.action.PeekAction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 探头时眼睛跟随上半身偏移：准星 / 近战 / 视线自动正确。
 *
 * <p>用 {@link PeekAction#isPeekActive}（而非 isPeeking）作为入口判断，
 * 保证收回时眼睛偏移也有过渡。
 */
@Mixin(Entity.class)
public abstract class PeekEyeMixin {

    @Inject(
            method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void fmm$peekEye(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof Player player)) return;
        if (!PeekAction.INSTANCE.isPeekActive(player)) return;

        Vec3 base = cir.getReturnValue();
        double offset = PeekAction.INSTANCE.smoothOffset(player, partialTicks);
        if (Math.abs(offset) < 1.0E-4) return;

        Vec3 right = PeekAction.rightVector(
                PeekAction.bodyYaw(player, partialTicks)).scale(offset);
        cir.setReturnValue(base.add(right));
    }
}