package com.feima.movemod.mixin;

import com.feima.movemod.action.PeekAction;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 探头第一人称相机横向平移（探身的位移感）。
 *
 * <p>屏幕 roll 不在这里做——Camera.rotation 在 1.20.1 渲染路径里没被消费，
 * 交给 {@link com.feima.movemod.client.PeekCameraRollHandler} 的
 * ViewportEvent.ComputeCameraAngles 处理。
 *
 * <p>用 {@link PeekAction#isPeekActive}（而非 isPeeking）作为入口判断，
 * 保证收回时也有过渡。
 */
@Mixin(Camera.class)
public abstract class PeekCameraMixin {

    @Shadow
    public abstract Vec3 getPosition();

    /** Camera.setPosition 在 1.20.1 是 protected，用 @Shadow 暴露。 */
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void fmm$peekCameraOffset(
            BlockGetter area,
            Entity entity,
            boolean detached,
            boolean thirdPersonReverse,
            float partialTick,
            CallbackInfo ci
    ) {
        // detached = 第三人称，不做
        if (detached) return;
        if (!(entity instanceof Player player)) return;
        if (!PeekAction.INSTANCE.isPeekActive(player)) return;

        double offset = PeekAction.INSTANCE.smoothOffset(player, partialTick);
        if (Math.abs(offset) < 1.0E-4) return;

        Vec3 right = PeekAction.rightVector(
                PeekAction.bodyYaw(player, partialTick)).scale(offset);
        this.setPosition(this.getPosition().add(right));
    }
}