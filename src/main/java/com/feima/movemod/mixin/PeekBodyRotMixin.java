package com.feima.movemod.mixin;

import com.feima.movemod.action.PeekAction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 探头时锁定身体旋转，抑制原版横向移动产生的视觉倾斜。
 *
 * <p>用 {@link PeekAction#isPeekActive}（而非 isPeeking），
 * 保证收回过渡期间也保持锁定，避免过渡中突然解锁产生的抖动。
 */
@Mixin(LivingEntity.class)
public abstract class PeekBodyRotMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void fmm$lockBodyRotWhilePeeking(CallbackInfo ci) {
        // 1. 只处理玩家
        if (!((Object) this instanceof Player player)) return;
        // 2. 只在探头（含收回过渡）时生效
        if (!PeekAction.INSTANCE.isPeekActive(player)) return;

        // 3. 强制身体旋转跟随头部，消除横向移动的倾斜。
        //    注意 O 用上一 tick 的头旋转，否则渲染插值 lerp(partial, O, cur) 会失效。
        player.yBodyRotO = player.yHeadRotO;
        player.yBodyRot  = player.yHeadRot;
    }
}