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
 *
 * 为什么需要：
 *   1. 原版疾跑会在 LivingEntity.travel 里给玩家水平速度加成，
 *      滑铲每 tick 覆盖 setDeltaMovement 之后，疾跑加成仍可能在
 *      同 tick 稍后叠加，导致滑铲速度被拉高。
 *   2. 滑铲期间玩家可能会一直按着疾跑键，或从服务端收到 sprint 同步，
 *      我们需要保证 sprint 状态始终为 false。
 *
 * 拦截策略：
 *   只在 setSprinting(true) 时拦截；setSprinting(false) 放行。
 *   这样原版/其他模组想把玩家设为不疾跑时不会被挡。
 *
 * 客户端、服务端都会装这个 Mixin，两端状态保持一致。
 */
@Mixin(LivingEntity.class)
public abstract class SlideSprintBlockMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void fmm$blockSprintWhileSliding(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) return;
        if (!MoveConfig.INSTANCE.enabled.get()) return;
        if (!MoveConfig.INSTANCE.slideEnabled.get()) return;
        if (!((Object) this instanceof Player player)) return;

        if (SlideAction.INSTANCE.isSliding(player)) {
            ci.cancel();
        }
    }
}