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
 * <p>采样循环用整数索引，避免浮点累加在 1.0 边界处丢采样。
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

        // 整数循环：采样点数 = floor(尺寸 * 2) + 1，与原版采样密度一致。
        int nx = (int) Math.floor(dx * 2.0D) + 1;
        int ny = (int) Math.floor(dy * 2.0D) + 1;
        int nz = (int) Math.floor(dz * 2.0D) + 1;

        if (nx <= 0 || ny <= 0 || nz <= 0) {
            cir.setReturnValue(0.0F);
            return;
        }

        int seen = 0;
        int total = 0;

        for (int ix = 0; ix <= nx; ix++) {
            double fx = (double) ix / nx;
            double lx = Mth.lerp(fx, localBox.minX, localBox.maxX);

            for (int iy = 0; iy <= ny; iy++) {
                double fy = (double) iy / ny;
                double ly = Mth.lerp(fy, localBox.minY, localBox.maxY);

                for (int iz = 0; iz <= nz; iz++) {
                    double fz = (double) iz / nz;
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