package com.feima.movemod.mixin;

import com.feima.movemod.action.PeekAction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 爆炸遮挡采样：探头玩家使用「旋转后的斜长方体体积」采样，而不是原版 AABB。
 *
 * <p>采样循环复刻原版 {@code Explosion.getSeenPercent} 的浮点步长
 * （{@code 1 / (size * 2 + 1)} 逐轴累加），保证非整数尺寸下采样点分布
 * 与原版一致；仅把世界坐标采样点替换为局部坐标经 {@code localToWorld}
 * 变换后的点，不改变几何判定标准。
 */
@Mixin(Explosion.class)
public abstract class PeekExplosionMixin {

    @Inject(method = "getSeenPercent", at = @At("HEAD"), cancellable = true)
    private static void fmm$getSeenPercent(
            Vec3 explosionPos, Entity entity,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!(entity instanceof Player p)) return;
        if (!PeekAction.INSTANCE.isPeeking(p)) return;

        Level level = p.level();
        float bodyYaw = p.yBodyRot;
        double theta = PeekAction.INSTANCE.thetaImmediate(p);

        AABB base = PeekAction.INSTANCE.baseBox(p);
        Vec3 pivot = PeekAction.INSTANCE.pivot(p);
        AABB localBox = base.move(-pivot.x, -pivot.y, -pivot.z);

        double dx = localBox.maxX - localBox.minX;
        double dy = localBox.maxY - localBox.minY;
        double dz = localBox.maxZ - localBox.minZ;

        // 与原版 Explosion.getSeenPercent 相同的采样步长。
        double d0 = 1.0 / (dx * 2.0 + 1.0);
        double d1 = 1.0 / (dy * 2.0 + 1.0);
        double d2 = 1.0 / (dz * 2.0 + 1.0);

        if (d0 < 0.0 || d1 < 0.0 || d2 < 0.0) {
            cir.setReturnValue(0.0F);
            return;
        }

        int seen = 0;
        int total = 0;

        for (double fx = 0.0; fx <= 1.0; fx += d0) {
            double lx = Mth.lerp(fx, localBox.minX, localBox.maxX);

            for (double fy = 0.0; fy <= 1.0; fy += d1) {
                double ly = Mth.lerp(fy, localBox.minY, localBox.maxY);

                for (double fz = 0.0; fz <= 1.0; fz += d2) {
                    double lz = Mth.lerp(fz, localBox.minZ, localBox.maxZ);

                    Vec3 sample = PeekAction.INSTANCE.localToWorld(
                            p, new Vec3(lx, ly, lz), bodyYaw, theta);

                    ClipContext ctx = new ClipContext(
                            sample, explosionPos,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            p
                    );
                    if (level.clip(ctx).getType() == HitResult.Type.MISS) {
                        seen++;
                    }
                    total++;
                }
            }
        }

        cir.setReturnValue(total == 0 ? 0.0F : (float) seen / (float) total);
    }
}