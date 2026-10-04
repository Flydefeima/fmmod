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
 * <p>原版 {@code Explosion.getSeenPercent} 在实体 AABB 上均匀采样点，从每个
 * 采样点向爆炸中心发射线，未命中的比例即为爆炸伤害的有效比例。对探头玩家
 * 来说，AABB 无法表达侧倾的体积——本 Mixin 在玩家未旋转局部系里采样，
 * 采样点旋转回世界坐标后再发射线。
 *
 * <p>效果：探出的头部若暴露在爆炸视线内，采样命中率上升（吃更多伤害）；
 * 身体另一侧缩回的部分若被方块挡住，采样命中率下降（少受伤）。
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

        // 玩家未旋转局部系里的基盒（平移到以 pivot 为原点）。
        AABB base = PeekAction.INSTANCE.baseBox(p);
        Vec3 pivot = PeekAction.INSTANCE.pivot(p);
        AABB localBox = base.move(-pivot.x, -pivot.y, -pivot.z);

        double dx = localBox.maxX - localBox.minX;
        double dy = localBox.maxY - localBox.minY;
        double dz = localBox.maxZ - localBox.minZ;

        // 与原版一致：采样步长由盒子尺寸决定。
        double stepX = 1.0D / (dx * 2.0D + 1.0D);
        double stepY = 1.0D / (dy * 2.0D + 1.0D);
        double stepZ = 1.0D / (dz * 2.0D + 1.0D);

        if (stepX < 0.0D || stepY < 0.0D || stepZ < 0.0D) {
            cir.setReturnValue(0.0F);
            return;
        }

        int seen = 0;
        int total = 0;

        for (double fx = 0.0D; fx <= 1.0D; fx += stepX) {
            for (double fy = 0.0D; fy <= 1.0D; fy += stepY) {
                for (double fz = 0.0D; fz <= 1.0D; fz += stepZ) {
                    double lx = Mth.lerp(fx, localBox.minX, localBox.maxX);
                    double ly = Mth.lerp(fy, localBox.minY, localBox.maxY);
                    double lz = Mth.lerp(fz, localBox.minZ, localBox.maxZ);

                    // 局部采样点 → 世界坐标（旋转 + 平移）。
                    Vec3 sample = PeekAction.INSTANCE.localToWorld(
                            p, new Vec3(lx, ly, lz), bodyYaw, theta);

                    // 与原版方向一致：从采样点射向爆炸中心。
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