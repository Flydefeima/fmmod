package com.feima.movemod.mixin;

import com.feima.movemod.action.CrawlAction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 趴下时把 pose 强制为 {@link net.minecraft.world.entity.Pose#SWIMMING}。
 *
 * <p>注入点在 HEAD 并 cancellable：一旦爬行状态生效，就接管原版的 pose 更新，
 * 避免原版每 tick 把 pose 覆写回 STANDING/CROUCHING。
 * 这样 {@code forcePose} 里只在"从非 SWIMMING 切换到 SWIMMING"那一刻刷新碰撞箱，
 * 而不是每 tick 都刷新。
 *
 * <p>碰撞箱 / 眼高由原版 Player 处理，不引用滑铲 mixin。
 *
 * <p>入水 / 上船 / 鞘翅等运行期退出由 {@link CrawlAction#tick} 处理，
 * 这里保持"信任 CrawlAction 状态"即可。
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void fmm$forceCrawlPose(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (CrawlAction.INSTANCE.forcePose(self)) {
            ci.cancel();
        }
    }
}